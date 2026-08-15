package com.tonio.libre2clock.ui.report

import android.content.Context
import com.tonio.libre2clock.data.local.SectionCacheDatabaseHelper
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.util.SectionPerfTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis

class ReportSectionCacheRepository(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
) {

    private val db = SectionCacheDatabaseHelper(context.applicationContext)
    private var lastPurgeAtMs: Long = 0L

    suspend fun getOrComputeReportMetrics(
        signature: String,
        retentionDays: Int,
        calculator: () -> ReportMetrics
    ): ReportMetrics =
        withContext(Dispatchers.IO) {
            purgeIfNeeded(retentionDays)
            var result: ReportMetrics? = null
            var cacheHit = false
            val duration = measureTimeMillis {
                val cached = db.getCachedPayload(REPORT_METRICS_SECTION_KEY, signature)
                if (cached != null) {
                    runCatching { json.decodeFromString<ReportMetrics>(cached) }
                        .getOrNull()
                        ?.let {
                            cacheHit = true
                            result = it
                            return@measureTimeMillis
                        }
                }

                val fresh = calculator()
                db.upsertPayload(REPORT_METRICS_SECTION_KEY, signature, json.encodeToString(fresh))
                result = fresh
            }

            SectionPerfTelemetry.record(REPORT_METRICS_SECTION_KEY, duration, cacheHit)
            requireNotNull(result) { "Report metrics cache computation returned null result" }
        }

    suspend fun getOrComputeAgp(
        signature: String,
        retentionDays: Int,
        calculator: () -> List<AgpPoint>
    ): List<AgpPoint> =
        withContext(Dispatchers.IO) {
            purgeIfNeeded(retentionDays)
            var result: List<AgpPoint>? = null
            var cacheHit = false
            val duration = measureTimeMillis {
                val cached = db.getCachedPayload(REPORT_AGP_SECTION_KEY, signature)
                if (cached != null) {
                    runCatching { json.decodeFromString<List<AgpPoint>>(cached) }
                        .getOrNull()
                        ?.let {
                            cacheHit = true
                            result = it
                            return@measureTimeMillis
                        }
                }

                val fresh = calculator()
                db.upsertPayload(REPORT_AGP_SECTION_KEY, signature, json.encodeToString(fresh))
                result = fresh
            }

            SectionPerfTelemetry.record(REPORT_AGP_SECTION_KEY, duration, cacheHit)
            requireNotNull(result) { "Report AGP cache computation returned null result" }
        }

    suspend fun getOrComputeDailySummaries(
        signature: String,
        retentionDays: Int,
        calculator: () -> List<DailySummary>
    ): List<DailySummary> = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        var result: List<DailySummary>? = null
        var cacheHit = false
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(REPORT_DAILY_SECTION_KEY, signature)
            if (cached != null) {
                runCatching { json.decodeFromString<List<DailySummaryCacheItem>>(cached) }
                    .getOrNull()
                    ?.map { it.toDailySummary() }
                    ?.let {
                        cacheHit = true
                        result = it
                        return@measureTimeMillis
                    }
            }

            val fresh = calculator()
            val serializable = fresh.map { DailySummaryCacheItem.fromDailySummary(it) }
            db.upsertPayload(REPORT_DAILY_SECTION_KEY, signature, json.encodeToString(serializable))
            result = fresh
        }

        SectionPerfTelemetry.record(REPORT_DAILY_SECTION_KEY, duration, cacheHit)
        requireNotNull(result) { "Report daily cache computation returned null result" }
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
        private const val REPORT_METRICS_SECTION_KEY = "report_metrics_v2"
        private const val REPORT_AGP_SECTION_KEY = "report_agp_v2"
        private const val REPORT_DAILY_SECTION_KEY = "report_daily_v2"
        private const val PURGE_INTERVAL_MS = 6L * 60L * 60L * 1000L // Purge every 6 hours
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_RETENTION_DAYS = 30
        private const val MAX_RETENTION_DAYS = 365

        fun buildSignature(
            glucose: List<GlucoseMeasurement>,
            doses: List<InsulinDose>,
            offset: Int,
            ranges: List<GlucoseOffsetRange>,
            autoAdjustEnabled: Boolean,
            capillaries: List<CapillaryMeasurement>,
            autoRangeMode: String,
            extraTag: String
        ): String {
            val gFirst = glucose.firstOrNull()
            val gLast = glucose.lastOrNull()
            val dFirst = doses.firstOrNull()
            val dLast = doses.lastOrNull()
            val capSample = capillaries.take(16)

            return buildString {
                append("tag=")
                append(extraTag)
                append(";g=")
                append(glucose.size)
                append(";g1=")
                append(gFirst?.factoryTimestamp ?: "-")
                append(':')
                append(gFirst?.value ?: -1)
                append(':')
                append(gFirst?.calibratedValue ?: -1)
                append(";gn=")
                append(gLast?.factoryTimestamp ?: "-")
                append(':')
                append(gLast?.value ?: -1)
                append(':')
                append(gLast?.calibratedValue ?: -1)
                append(";d=")
                append(doses.size)
                append(";d1=")
                append(dFirst?.timestamp ?: "-")
                append(':')
                append(dFirst?.units ?: -1.0)
                append(':')
                append(dFirst?.type?.name ?: "-")
                append(";dn=")
                append(dLast?.timestamp ?: "-")
                append(':')
                append(dLast?.units ?: -1.0)
                append(':')
                append(dLast?.type?.name ?: "-")
                append(";off=")
                append(offset)
                append(";rangesHash=")
                append(ranges.hashCode())
                append(";auto=")
                append(autoAdjustEnabled)
                append(";mode=")
                append(autoRangeMode)
                append(";caps=")
                append(capillaries.size)
                append(";capsHash=")
                append(capSample.hashCode())
            }
        }
    }
}
