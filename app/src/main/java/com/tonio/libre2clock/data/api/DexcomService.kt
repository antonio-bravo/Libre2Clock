package com.tonio.libre2clock.data.api

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tonio.libre2clock.data.model.DexcomAuthRequest
import com.tonio.libre2clock.data.model.DexcomGlucoseReading
import com.tonio.libre2clock.data.model.DexcomLoginByIdRequest
import com.tonio.libre2clock.data.model.DexcomServerRegion
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.SecureCredentialStore
import com.tonio.libre2clock.util.EventLogManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object DexcomService {

    @Volatile
    private var region: DexcomServerRegion = DexcomServerRegion.OUS

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var isInitialized = false

    private var preferenceManager: PreferenceManager? = null
    private var credentialStore: SecureCredentialStore? = null
    private var eventLogger: EventLogManager? = null

    private var retrofit: Retrofit? = null

    fun init(
        context: Context,
        prefManager: PreferenceManager,
        credStore: SecureCredentialStore,
        logger: EventLogManager? = null
    ) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            preferenceManager = prefManager
            credentialStore = credStore
            eventLogger = logger

            runBlocking {
                val savedRegion = prefManager.dexcomServerRegion.first()
                region = savedRegion
            }

            isInitialized = true
            Log.d("DexcomService", "DexcomService initialized with region: ${region.displayName}")
        }
    }

    fun setRegion(newRegion: DexcomServerRegion) {
        if (region != newRegion) {
            region = newRegion
            sessionId = null
            retrofit = null // Force re-build retrofit with new base URL
        }
    }

    fun getRegion(): DexcomServerRegion = region

    fun getSessionId(): String? = sessionId

    fun setSessionId(id: String?) {
        sessionId = id
    }

    fun clearSession() {
        sessionId = null
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val api: DexcomShareApi
        get() {
            if (retrofit == null) {
                synchronized(this) {
                    if (retrofit == null) {
                        retrofit = Retrofit.Builder()
                            .baseUrl(region.baseUrl)
                            .client(okHttpClient)
                            .addConverterFactory(ScalarsConverterFactory.create())
                            .addConverterFactory(MoshiConverterFactory.create(moshi))
                            .build()
                    }
                }
            }
            return retrofit!!.create(DexcomShareApi::class.java)
        }

    suspend fun login(accountName: String, password: String, targetRegion: DexcomServerRegion = DexcomServerRegion.OUS): Result<String> {
        return try {
            setRegion(targetRegion)
            // Step 1: Get Account ID
            val rawAccountId = api.authenticate(DexcomAuthRequest(accountName = accountName, password = password))
            val accountId = cleanQuotes(rawAccountId)

            if (accountId.isBlank() || accountId == "00000000-0000-0000-0000-000000000000") {
                return Result.failure(Exception("Autenticación Dexcom fallida: usuario o contraseña incorrectos"))
            }

            // Step 2: Login by Account ID to get Session ID
            val rawSessionId = api.loginById(DexcomLoginByIdRequest(accountId = accountId, password = password))
            val newSessionId = cleanQuotes(rawSessionId)

            if (newSessionId.isBlank() || newSessionId == "00000000-0000-0000-0000-000000000000") {
                return Result.failure(Exception("Inicio de sesión Dexcom fallido: sesión no válida"))
            }

            sessionId = newSessionId
            Result.success(newSessionId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchReadings(accountName: String?, password: String?): Result<List<GlucoseMeasurement>> {
        return try {
            var currentSessionId = sessionId
            if (currentSessionId.isNullOrBlank() && !accountName.isNullOrBlank() && !password.isNullOrBlank()) {
                val loginRes = login(accountName, password, region)
                if (loginRes.isFailure) {
                    return Result.failure(loginRes.exceptionOrNull() ?: Exception("Dexcom re-auth failed"))
                }
                currentSessionId = sessionId
            }

            val sid = currentSessionId ?: return Result.failure(Exception("No active Dexcom session"))

            val rawReadings = try {
                api.readLatestGlucoseValues(sessionId = sid)
            } catch (e: Exception) {
                // Try re-authenticating if session expired
                if (!accountName.isNullOrBlank() && !password.isNullOrBlank()) {
                    val loginRes = login(accountName, password, region)
                    if (loginRes.isSuccess) {
                        api.readLatestGlucoseValues(sessionId = sessionId!!)
                    } else {
                        throw e
                    }
                } else {
                    throw e
                }
            }

            val measurements = rawReadings.mapNotNull { convertReadingToMeasurement(it) }
            Result.success(measurements)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun convertReadingToMeasurement(reading: DexcomGlucoseReading): GlucoseMeasurement? {
        val epochMs = extractEpochMs(reading) ?: return null
        val epochSeconds = epochMs / 1000L

        val isoTimestamp = formatIsoTimestamp(epochMs)
        val trendArrow = mapTrendToTrendArrow(reading.trend)

        return GlucoseMeasurement(
            factoryTimestamp = isoTimestamp,
            timestamp = isoTimestamp,
            type = 1,
            valueInMgPerDl = reading.value,
            trendArrow = trendArrow,
            value = reading.value,
            calibratedValue = reading.value,
            epochSeconds = epochSeconds
        )
    }

    fun extractEpochMs(reading: DexcomGlucoseReading): Long? {
        if (reading.epoch != null && reading.epoch > 0) {
            return reading.epoch
        }

        val dateStr = reading.wt ?: reading.st ?: reading.dt ?: return null
        val pattern = Pattern.compile("Date\\((-?\\d+)")
        val matcher = pattern.matcher(dateStr)
        if (matcher.find()) {
            return matcher.group(1)?.toLongOrNull()
        }
        return null
    }

    fun mapTrendToTrendArrow(trend: String?): Int {
        return when (trend?.lowercase()?.trim()) {
            "doubleup" -> 1       // Subiendo rápido
            "singleup" -> 2       // Subiendo
            "fortyfiveup" -> 3   // Subiendo suavemente
            "flat" -> 4           // Estable
            "fortyfivedown" -> 5 // Bajando suavemente
            "singledown" -> 6     // Bajando
            "doubledown" -> 7     // Bajando rápido
            else -> 0             // Sin flecha / No computable
        }
    }

    private fun cleanQuotes(str: String): String {
        return str.trim().trim('"').trim('\'')
    }

    private fun formatIsoTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }
}
