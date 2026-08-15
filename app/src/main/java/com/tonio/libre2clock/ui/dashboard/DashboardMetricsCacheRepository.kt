package com.tonio.libre2clock.ui.dashboard

import android.content.Context
import com.tonio.libre2clock.data.local.SectionCacheDatabaseHelper
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.util.SectionPerfTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis

class DashboardMetricsCacheRepository(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
) {

    private val db = SectionCacheDatabaseHelper(context.applicationContext)
    private var lastPurgeAtMs: Long = 0L

    suspend fun getOrCompute(
        sectionKey: String,
        signature: String,
        retentionDays: Int,
        calculator: () -> DashboardMetrics
    ): DashboardMetrics = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        var result: DashboardMetrics? = null
        var cacheHit = false
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(sectionKey, signature)
            if (cached != null) {
                runCatching { json.decodeFromString<DashboardMetrics>(cached) }
                    .getOrNull()
                    ?.let {
                        cacheHit = true
                        result = it
                        return@measureTimeMillis
                    }
            }

            val fresh = calculator()
            val payload = json.encodeToString(fresh)
            db.upsertPayload(sectionKey, signature, payload)
            result = fresh
        }

        SectionPerfTelemetry.record(section = sectionKey, durationMs = duration, cacheHit = cacheHit)
        requireNotNull(result) { "Dashboard metrics cache computation returned null result" }
    }

    private fun purgeIfNeeded(retentionDays: Int) {
        val now = System.currentTimeMillis()
        if (now - lastPurgeAtMs < PURGE_INTERVAL_MS) return

        val safeDays = retentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        val cutoff = now - safeDays * DAY_MS
        db.purgeOlderThan(cutoff)
        lastPurgeAtMs = now
    }

    companion object {
        const val DASHBOARD_SECTION_KEY = "dashboard_metrics_v2"
        private const val PURGE_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_RETENTION_DAYS = 30
        private const val MAX_RETENTION_DAYS = 365

        fun buildSignatureFast(
            measurements: List<GlucoseMeasurement>,
            dataVersion: Long,
            capillaries: List<com.tonio.libre2clock.data.model.CapillaryMeasurement>
        ): String {
            if (measurements.isEmpty()) return "empty-$dataVersion"

            val first = measurements.first()
            val last = measurements.last()
            
            // Fast signature using version metadata and boundary items.
            // dataVersion captures any DB change.
            // capillary count/last timestamp captures calibration changes.
            val capSig = if (capillaries.isNotEmpty()) {
                "${capillaries.size}-${capillaries.first().timestamp}"
            } else "no-cap"

            return buildString {
                append("v=")
                append(dataVersion)
                append(";c=")
                append(measurements.size)
                append(";cp=")
                append(capSig)
                append(";f=")
                append(first.epochSeconds ?: first.factoryTimestamp)
                append(':')
                append(first.value)
                append(";l=")
                append(last.epochSeconds ?: last.factoryTimestamp)
                append(':')
                append(last.value)
            }
        }

        fun buildSignature(measurements: List<GlucoseMeasurement>): String {
            if (measurements.isEmpty()) return "empty"

            // Use total sums to ensure any change in any measurement invalidates the cache.
            // This is critical when old historical values are recalculated after a new capillary reading.
            var rawSum = 0L
            var calibratedSum = 0L
            measurements.forEach {
                rawSum += it.value
                calibratedSum += it.calibratedValue
            }

            val first = measurements.first()
            val last = measurements.last()

            return buildString {
                append("c=")
                append(measurements.size)
                append(";rS=")
                append(rawSum)
                append(";cS=")
                append(calibratedSum)
                append(";f=")
                append(first.epochSeconds ?: first.factoryTimestamp)
                append(";l=")
                append(last.epochSeconds ?: last.factoryTimestamp)
            }
        }
    }
}
