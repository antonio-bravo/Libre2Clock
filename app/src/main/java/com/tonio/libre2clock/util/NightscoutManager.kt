package com.tonio.libre2clock.util

import android.util.Log
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.data.model.SensorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object NightscoutManager {

    private const val TAG = "NightscoutManager"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun uploadGlucose(
        nightscoutUrl: String,
        apiSecret: String,
        measurement: GlucoseMeasurement,
        useCalibrated: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = sanitizeUrl(nightscoutUrl)
            if (cleanUrl.isBlank()) {
                return@withContext Result.failure(Exception("Nightscout URL is empty"))
            }

            val endpoint = "$cleanUrl/api/v1/entries"
            val valueToUpload = if (useCalibrated && measurement.calibratedValue > 0) {
                measurement.calibratedValue
            } else {
                measurement.value
            }

            val rawValue = measurement.value
            val instant = TimestampParser.parseMeasurementInstant(measurement) ?: Instant.now()
            val dateStr = DateTimeFormatter.ISO_INSTANT.format(instant)

            val entryJson = JSONObject().apply {
                put("type", "sgv")
                put("sgv", valueToUpload)
                put("date", instant.toEpochMilli())
                put("dateString", dateStr)
                put("direction", getNightscoutDirection(measurement.trendArrow))
                put("device", "Libre2Clock")
                if (useCalibrated && rawValue != valueToUpload) {
                    put("sysBG", rawValue)
                }
            }

            val payloadArray = JSONArray().apply { put(entryJson) }
            val requestBody = payloadArray.toString().toRequestBody(jsonMediaType)

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")

            if (apiSecret.isNotBlank()) {
                val hashedSecret = sha1(apiSecret)
                requestBuilder.header("api-secret", hashedSecret)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body.string()

                if (response.isSuccessful) {
                    Log.i(TAG, "Successfully uploaded glucose $valueToUpload to Nightscout")
                    Result.success("Nightscout upload OK (HTTP ${response.code})")
                } else {
                    val errorMsg = "Nightscout HTTP ${response.code}: $responseBody"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading to Nightscout", e)
            Result.failure(e)
        }
    }

    suspend fun uploadTreatmentsBatch(
        nightscoutUrl: String,
        apiSecret: String,
        doses: List<InsulinDose>,
        capillaries: List<CapillaryMeasurement> = emptyList(),
        sensorLogs: List<SensorLog> = emptyList()
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = sanitizeUrl(nightscoutUrl)
            if (cleanUrl.isBlank()) {
                return@withContext Result.failure(Exception("Nightscout URL is empty"))
            }

            val payloadArray = JSONArray()

            for (dose in doses) {
                if (dose.isDeleted) continue
                val instant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: continue
                val eventType = when {
                    dose.type == InsulinType.SLOW -> "Basal"
                    (dose.carbs ?: 0.0) > 0.0 -> "Meal Bolus"
                    else -> "Correction Bolus"
                }
                payloadArray.put(JSONObject().apply {
                    put("eventType", eventType)
                    put("insulin", dose.units)
                    if ((dose.carbs ?: 0.0) > 0.0) put("carbs", dose.carbs)
                    put("created_at", DateTimeFormatter.ISO_INSTANT.format(instant))
                    put("enteredBy", "Libre2Clock")
                })
            }

            for (cap in capillaries) {
                if (cap.isDeleted) continue
                val instant = TimestampParser.parseFlexibleInstant(cap.timestamp) ?: continue
                payloadArray.put(JSONObject().apply {
                    put("eventType", "BG Check")
                    put("glucose", cap.value)
                    put("glucoseType", "Finger")
                    put("units", "mg/dl")
                    put("created_at", DateTimeFormatter.ISO_INSTANT.format(instant))
                    put("enteredBy", "Libre2Clock")
                })
            }

            for (sensor in sensorLogs) {
                if (sensor.isDeleted) continue
                val instant = TimestampParser.parseFlexibleInstant(sensor.startDate) ?: continue
                payloadArray.put(JSONObject().apply {
                    put("eventType", "Sensor Start")
                    put("notes", "SN: ${sensor.serialNumber}")
                    put("created_at", DateTimeFormatter.ISO_INSTANT.format(instant))
                    put("enteredBy", "Libre2Clock")
                })
            }

            if (payloadArray.length() == 0) {
                return@withContext Result.success(0)
            }

            val endpoint = "$cleanUrl/api/v1/treatments"
            val requestBody = payloadArray.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")

            if (apiSecret.isNotBlank()) {
                requestBuilder.header("api-secret", sha1(apiSecret))
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body.string()
                if (response.isSuccessful) {
                    val count = payloadArray.length()
                    Log.i(TAG, "Successfully uploaded $count treatments to Nightscout")
                    Result.success(count)
                } else {
                    val errorMsg = "Nightscout Treatments HTTP ${response.code}: $responseBody"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading treatments to Nightscout", e)
            Result.failure(e)
        }
    }

    private fun getNightscoutDirection(trend: Int?): String {
        return when (trend) {
            1 -> "DoubleDown"
            2 -> "FortyFiveDown"
            3 -> "Flat"
            4 -> "FortyFiveUp"
            5 -> "DoubleUp"
            else -> "Flat"
        }
    }

    private fun sanitizeUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.dropLast(1)
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && trimmed.isNotBlank()) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
