package com.tonio.libre2clock.ui.settings

import android.content.Context
import com.tonio.libre2clock.data.local.SectionCacheDatabaseHelper
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.RangeOffsetInsight
import com.tonio.libre2clock.util.SectionPerfTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis

class SettingsSectionCacheRepository(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
) {

    private val db = SectionCacheDatabaseHelper(context.applicationContext)
    private var lastPurgeAtMs: Long = 0L

    suspend fun getOrComputeRangeInsights(
        signature: String,
        retentionDays: Int,
        calculator: () -> List<RangeOffsetInsight>
    ): List<RangeOffsetInsight> = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        var result: List<RangeOffsetInsight>? = null
        var cacheHit = false
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(RANGE_INSIGHTS_SECTION_KEY, signature)
            if (cached != null) {
                runCatching { json.decodeFromString<List<RangeOffsetInsight>>(cached) }
                    .getOrNull()
                    ?.let {
                        cacheHit = true
                        result = it
                        return@measureTimeMillis
                    }
            }

            val fresh = calculator()
            db.upsertPayload(RANGE_INSIGHTS_SECTION_KEY, signature, json.encodeToString(fresh))
            result = fresh
        }

        SectionPerfTelemetry.record(RANGE_INSIGHTS_SECTION_KEY, duration, cacheHit)
        requireNotNull(result) { "Settings range insights cache computation returned null result" }
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
        private const val RANGE_INSIGHTS_SECTION_KEY = "settings_range_insights_v1"
        private const val PURGE_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_RETENTION_DAYS = 30
        private const val MAX_RETENTION_DAYS = 365

        fun buildRangeInsightsSignature(
            ranges: List<GlucoseOffsetRange>,
            capillaries: List<CapillaryMeasurement>
        ): String {
            val rangeEdge = ranges.joinToString("|") { "${it.min}:${it.max ?: -1}:${it.offset}:${it.percentage}" }
            val capSample = capillaries.take(24)
            return "ranges=${ranges.size};edge=$rangeEdge;caps=${capillaries.size};capsHash=${capSample.hashCode()}"
        }
    }
}
