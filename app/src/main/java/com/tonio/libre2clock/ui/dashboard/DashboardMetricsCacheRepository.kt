package com.tonio.libre2clock.ui.dashboard

import android.content.Context
import com.tonio.libre2clock.data.local.SectionCacheDatabaseHelper
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.util.SectionPerfTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis
import java.util.concurrent.atomic.AtomicLong

class DashboardMetricsCacheRepository(
    context: Context,
    private val json: Json = Companion.defaultJson
) {

    private val db = SectionCacheDatabaseHelper(context.applicationContext)
    private val lastPurgeAtMs = AtomicLong(0L)

    suspend fun getOrCompute(
        sectionKey: String,
        signature: String,
        retentionDays: Int,
        calculator: suspend () -> DashboardMetrics
    ): DashboardMetrics = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        
        var result: DashboardMetrics? = null
        var cacheHit = false
        
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(sectionKey, signature)
            if (cached != null) {
                try {
                    result = json.decodeFromString<DashboardMetrics>(cached)
                    cacheHit = true
                    return@measureTimeMillis
                } catch (e: Exception) {
                    // Caché corrupta o cambio de esquema: se ignora y se recalcula
                }
            }

            val fresh = calculator()
            try {
                val payload = json.encodeToString(fresh)
                db.upsertPayload(sectionKey, signature, payload)
            } catch (e: Exception) {
                // Fallo de escritura en DB: ignoramos para no interrumpir el flujo
            }
            result = fresh
        }

        SectionPerfTelemetry.record(section = sectionKey, durationMs = duration, cacheHit = cacheHit)
        requireNotNull(result) { "Dashboard metrics cache computation returned null result" }
    }

    private fun purgeIfNeeded(retentionDays: Int) {
        val now = System.currentTimeMillis()
        val lastPurge = lastPurgeAtMs.get()
        
        if (now - lastPurge < PURGE_INTERVAL_MS) return
        
        if (lastPurgeAtMs.compareAndSet(lastPurge, now)) {
            val safeDays = retentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
            val cutoff = now - safeDays * DAY_MS
            db.purgeOlderThan(cutoff)
        }
    }

    companion object {
        const val DASHBOARD_SECTION_KEY = "dashboard_metrics_v2"
        private const val PURGE_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_RETENTION_DAYS = 30
        private const val MAX_RETENTION_DAYS = 365
        private const val HISTORICAL_SIGNATURE_BUCKET_MS = 5L * 60L * 1000L

        private val defaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun buildSignatureFast(
            dataVersion: Long,
            capillaries: List<com.tonio.libre2clock.data.model.CapillaryMeasurement>,
            ranges: List<GlucoseOffsetRange> = emptyList(),
            sensorLogs: List<SensorLog> = emptyList(),
            manualOffset: Int = 0,
            autoAdjust: Boolean = false,
            autoRangeMode: String = "OFF"
        ): String {
            // OPTIMIZACIÓN: Bucle for explícito para evitar la asignación del iterador de sumOf
            val capSig = if (capillaries.isNotEmpty()) {
                var hashSum = 0L
                for (c in capillaries) {
                    hashSum += c.hashCode().toLong()
                }
                "${capillaries.size}-$hashSum-${capillaries[0].timestamp}"
            } else "no-cap"

            val rangeSig = if (ranges.isNotEmpty()) {
                var sum = 0
                for (r in ranges) {
                    sum += r.offset + r.percentage
                }
                "${ranges.size}-$sum"
            } else "no-ranges"

            val logSig = if (sensorLogs.isNotEmpty()) {
                val last = sensorLogs[0]
                "${sensorLogs.size}-${last.serialNumber}-${last.startDate}"
            } else "no-logs"

            val timeBucket = System.currentTimeMillis() / HISTORICAL_SIGNATURE_BUCKET_MS

            // OPTIMIZACIÓN: Capacidad aumentada a 128 para garantizar cero redimensionamientos
            // incluso con timestamps largos o números de serie extensos.
            return buildString(128) {
                append("tb=").append(timeBucket)
                append(";v=").append(dataVersion)
                append(";cp=").append(capSig)
                append(";rg=").append(rangeSig)
                append(";sl=").append(logSig)
                append(";mo=").append(manualOffset)
                append(";aa=").append(autoAdjust)
                append(";am=").append(autoRangeMode)
            }
        }

        fun buildSignature(measurements: List<GlucoseMeasurement>): String {
            if (measurements.isEmpty()) return "empty"

            var rawSum = 0L
            var calibratedSum = 0L
            
            // Bucle for explícito para máxima velocidad y cero asignaciones
            for (m in measurements) {
                rawSum += m.value
                calibratedSum += m.calibratedValue
            }

            // OPTIMIZACIÓN: Acceso por índice es marginalmente más rápido que .first()/.last()
            val first = measurements[0]
            val last = measurements[measurements.size - 1]

            return buildString(64) {
                append("c=").append(measurements.size)
                append(";rS=").append(rawSum)
                append(";cS=").append(calibratedSum)
                append(";f=").append(first.epochSeconds ?: first.factoryTimestamp)
                append(";l=").append(last.epochSeconds ?: last.factoryTimestamp)
            }
        }
    }
}