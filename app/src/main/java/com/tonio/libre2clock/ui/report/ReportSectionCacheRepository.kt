package com.tonio.libre2clock.ui.report

import android.content.Context
import com.tonio.libre2clock.data.local.SectionCacheDatabaseHelper
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.ui.report.ReportMetrics
import com.tonio.libre2clock.ui.report.AgpPoint
import com.tonio.libre2clock.ui.report.DailySummary
import com.tonio.libre2clock.ui.report.FullReportData
import com.tonio.libre2clock.util.SectionPerfTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.system.measureTimeMillis
import java.util.concurrent.atomic.AtomicLong

class ReportSectionCacheRepository(
    context: Context,
    private val json: Json = Companion.defaultJson
) {

    private val db = SectionCacheDatabaseHelper(context.applicationContext)
    
    // OPTIMIZACIÓN 1: AtomicLong para evitar purgas redundantes por condiciones de carrera ("thundering herd")
    private val lastPurgeAtMs = AtomicLong(0L)

    suspend fun getOrComputeReportMetrics(
        signature: String,
        retentionDays: Int,
        calculator: suspend () -> ReportMetrics // OPTIMIZACIÓN 2: 'suspend' permite operaciones asíncronas reales
    ): ReportMetrics = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        
        var result: ReportMetrics? = null
        var cacheHit = false
        
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(REPORT_METRICS_SECTION_KEY, signature)
            if (cached != null) {
                try {
                    // OPTIMIZACIÓN 3: try-catch nativo es más rápido que runCatching (evita crear objetos Result/Exception)
                    result = json.decodeFromString<ReportMetrics>(cached)
                    cacheHit = true
                    return@measureTimeMillis
                } catch (e: Exception) {
                    // Caché corrupta o cambio de esquema: se ignora y se recalcula
                }
            }

            val fresh = calculator()
            try {
                db.upsertPayload(REPORT_METRICS_SECTION_KEY, signature, json.encodeToString(fresh))
            } catch (e: Exception) {
                // Fallo de escritura en DB: ignoramos para no interrumpir el flujo
            }
            result = fresh
        }

        SectionPerfTelemetry.record(REPORT_METRICS_SECTION_KEY, duration, cacheHit)
        requireNotNull(result) { "Report metrics cache computation returned null result" }
    }

    suspend fun getOrComputeAgp(
        signature: String,
        retentionDays: Int,
        calculator: suspend () -> List<AgpPoint>
    ): List<AgpPoint> = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        
        var result: List<AgpPoint>? = null
        var cacheHit = false
        
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(REPORT_AGP_SECTION_KEY, signature)
            if (cached != null) {
                try {
                    result = json.decodeFromString<List<AgpPoint>>(cached)
                    cacheHit = true
                    return@measureTimeMillis
                } catch (e: Exception) {
                    // Ignorar
                }
            }

            val fresh = calculator()
            try {
                db.upsertPayload(REPORT_AGP_SECTION_KEY, signature, json.encodeToString(fresh))
            } catch (e: Exception) {
                // Ignorar
            }
            result = fresh
        }

        SectionPerfTelemetry.record(REPORT_AGP_SECTION_KEY, duration, cacheHit)
        requireNotNull(result) { "Report AGP cache computation returned null result" }
    }

    suspend fun getOrComputeDailySummaries(
        signature: String,
        retentionDays: Int,
        calculator: suspend () -> List<DailySummary>
    ): List<DailySummary> = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        
        var result: List<DailySummary>? = null
        var cacheHit = false
        
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(REPORT_DAILY_SECTION_KEY, signature)
            if (cached != null) {
                try {
                    result = json.decodeFromString<List<DailySummaryCacheItem>>(cached)
                        ?.map { it.toDailySummary() }
                    if (result != null) {
                        cacheHit = true
                        return@measureTimeMillis
                    }
                } catch (e: Exception) {
                    // Ignorar
                }
            }

            val fresh = calculator()
            try {
                val serializable = fresh.map { DailySummaryCacheItem.fromDailySummary(it) }
                db.upsertPayload(REPORT_DAILY_SECTION_KEY, signature, json.encodeToString(serializable))
            } catch (e: Exception) {
                // Ignorar
            }
            result = fresh
        }

        SectionPerfTelemetry.record(REPORT_DAILY_SECTION_KEY, duration, cacheHit)
        requireNotNull(result) { "Report daily cache computation returned null result" }
    }

    suspend fun getOrComputeFullReport(
        signature: String,
        retentionDays: Int,
        calculator: suspend () -> FullReportData
    ): FullReportData = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        
        var result: FullReportData? = null
        var cacheHit = false
        
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(FULL_REPORT_SECTION_KEY, signature)
            if (cached != null) {
                try {
                    result = json.decodeFromString<FullReportData>(cached)
                    cacheHit = true
                    return@measureTimeMillis
                } catch (e: Exception) {
                    // Ignorar
                }
            }

            val fresh = calculator()
            try {
                db.upsertPayload(FULL_REPORT_SECTION_KEY, signature, json.encodeToString(fresh))
            } catch (e: Exception) {
                // Ignorar
            }
            result = fresh
        }

        SectionPerfTelemetry.record(FULL_REPORT_SECTION_KEY, duration, cacheHit)
        requireNotNull(result) { "Full report cache computation returned null result" }
    }

    private fun purgeIfNeeded(retentionDays: Int) {
        val now = System.currentTimeMillis()
        val lastPurge = lastPurgeAtMs.get()
        
        // Chequeo rápido sin bloqueo
        if (now - lastPurge < PURGE_INTERVAL_MS) return
        
        // Solo una corrutina logrará actualizar el valor y ejecutar la purga
        if (lastPurgeAtMs.compareAndSet(lastPurge, now)) {
            val safeDays = retentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
            val cutoff = now - safeDays * DAY_MS
            db.purgeOlderThan(cutoff)
        }
    }

    companion object {
        const val REPORT_METRICS_SECTION_KEY = "report_metrics_v2"
        const val REPORT_AGP_SECTION_KEY = "report_agp_v2"
        const val REPORT_DAILY_SECTION_KEY = "report_daily_v2"
        const val FULL_REPORT_SECTION_KEY = "full_report_v2"
        private const val PURGE_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 horas
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_RETENTION_DAYS = 30
        private const val MAX_RETENTION_DAYS = 365

        // OPTIMIZACIÓN 4: Instancia única de Json para evitar sobrecarga de inicialización
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

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
            // OPTIMIZACIÓN 5: Pre-asignación de capacidad para evitar redimensionamientos del StringBuilder
            return buildString(256) {
                append("tag=").append(extraTag)
                append(";g=").append(glucose.size)
                
                if (glucose.isNotEmpty()) {
                    val gFirst = glucose[0]
                    append(";g1=").append(gFirst.factoryTimestamp).append(':').append(gFirst.value).append(':').append(gFirst.calibratedValue)
                    val gLast = glucose[glucose.size - 1]
                    append(";gn=").append(gLast.factoryTimestamp).append(':').append(gLast.value).append(':').append(gLast.calibratedValue)
                }

                append(";d=").append(doses.size)
                if (doses.isNotEmpty()) {
                    val dFirst = doses[0]
                    append(";d1=").append(dFirst.timestamp).append(':').append(dFirst.units).append(':').append(dFirst.type.name)
                    val dLast = doses[doses.size - 1]
                    append(";dn=").append(dLast.timestamp).append(':').append(dLast.units).append(':').append(dLast.type.name)
                }

                append(";off=").append(offset)
                append(";rangesHash=").append(ranges.hashCode())
                append(";auto=").append(autoAdjustEnabled)
                append(";mode=").append(autoRangeMode)
                append(";caps=").append(capillaries.size)
                
                // OPTIMIZACIÓN 6: Cálculo de hash sin crear lista intermedia (Zero-Allocation)
                var capHash = 0
                val limit = min(16, capillaries.size)
                for (i in 0 until limit) {
                    capHash = 31 * capHash + capillaries[i].hashCode()
                }
                append(";capsHash=").append(capHash)
            }
        }
    }
}