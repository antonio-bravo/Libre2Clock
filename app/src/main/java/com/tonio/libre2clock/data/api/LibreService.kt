package com.tonio.libre2clock.data.api

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tonio.libre2clock.data.model.LoginData
import com.tonio.libre2clock.data.model.LoginRequest
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.SecureCredentialStore
import com.tonio.libre2clock.util.EventLogManager
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.MessageDigest

object LibreService {
    private const val DEFAULT_BASE_URL = "https://api.libreview.io/"
    private const val PRODUCT = "llu.android"
    private const val VERSION = "4.16.0"

    @Volatile
    private var baseUrl = DEFAULT_BASE_URL

    @Volatile
    private var authToken: String? = null

    @Volatile
    private var userId: String? = null

    @Volatile
    private var isInitialized = false

    private var preferenceManager: PreferenceManager? = null
    private var credentialStore: SecureCredentialStore? = null
    private var eventLogger: EventLogManager? = null

    private var retrofit: Retrofit? = null
    private var directRetrofit: Retrofit? = null

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
                val token = prefManager.getAuthTokenSync()
                val id = prefManager.getUserIdSync()
                val savedRegion = prefManager.getRegionSync()

                if (!token.isNullOrBlank() && !id.isNullOrBlank()) {
                    authToken = token
                    userId = id
                }

                if (!savedRegion.isNullOrBlank()) {
                    baseUrl = "https://api-$savedRegion.libreview.io/"
                }
            }

            isInitialized = true
            Log.d("LibreService", "LibreService initialized with baseUrl: $baseUrl, authToken present: ${authToken != null}")
        }
    }

    fun getAuthToken(): String? = authToken
    fun getUserId(): String? = userId
    fun getBaseUrl(): String = baseUrl

    fun setAuth(token: String, id: String) {
        authToken = token
        userId = id
    }

    fun clearAuth() {
        authToken = null
        userId = null
    }

    fun updateRegion(region: String) {
        val newBaseUrl = "https://api-$region.libreview.io/"
        if (baseUrl != newBaseUrl) {
            Log.i("LibreService", "Updating LibreLinkUp region to $region ($newBaseUrl)")
            baseUrl = newBaseUrl
            preferenceManager?.let { pm ->
                runBlocking { pm.saveRegion(region) }
            }
            retrofit = null // Force rebuild of API on next call
            directRetrofit = null
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("product", PRODUCT)
            .header("version", VERSION)
            .header("Content-Type", "application/json")

        // Dynamically rewrite host if region changed or custom baseUrl is set
        val currentBaseUrl = baseUrl
        val targetHttpUrl = currentBaseUrl.toHttpUrlOrNull()
        if (targetHttpUrl != null && originalRequest.url.host != targetHttpUrl.host) {
            val newUrl = originalRequest.url.newBuilder()
                .scheme(targetHttpUrl.scheme)
                .host(targetHttpUrl.host)
                .port(targetHttpUrl.port)
                .build()
            requestBuilder.url(newUrl)
        }

        val token = authToken
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val id = userId
        if (!id.isNullOrBlank()) {
            requestBuilder.header("Account-Id", sha256(id))
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val directOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val mainOkHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)

        val pm = preferenceManager
        val cs = credentialStore
        if (pm != null && cs != null) {
            builder.authenticator(LibreAuthenticator(cs, pm, eventLogger))
        }

        builder.build()
    }

    val api: LibreLinkUpApi
        get() {
            if (retrofit == null) {
                synchronized(this) {
                    if (retrofit == null) {
                        retrofit = Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(mainOkHttpClient)
                            .addConverterFactory(MoshiConverterFactory.create(moshi))
                            .build()
                    }
                }
            }
            return retrofit!!.create(LibreLinkUpApi::class.java)
        }

    private val directApi: LibreLinkUpApi
        get() {
            if (directRetrofit == null) {
                synchronized(this) {
                    if (directRetrofit == null) {
                        directRetrofit = Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(directOkHttpClient)
                            .addConverterFactory(MoshiConverterFactory.create(moshi))
                            .build()
                    }
                }
            }
            return directRetrofit!!.create(LibreLinkUpApi::class.java)
        }

    fun performSilentReauth(email: String, password: String): Result<LoginData> {
        return try {
            runBlocking {
                var response = directApi.login(LoginRequest(email, password))
                var data = response.data

                // Handle regional redirect if necessary
                if (data?.redirect == true && data.region != null) {
                    updateRegion(data.region)
                    response = directApi.login(LoginRequest(email, password))
                    data = response.data
                }

                if (response.status == 0 && data != null && data.authTicket != null && data.user != null) {
                    setAuth(data.authTicket.token, data.user.id)
                    Result.success(data)
                } else {
                    Result.failure(Exception("Silent reauth failed with status ${response.status}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
