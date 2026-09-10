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
    // Thread-safe purge tracking para evitar purgas redundantes por condiciones de carrera
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
                    // try-catch nativo es más rápido que runCatching (evita crear objetos Result/Exception)
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
                // Fallo de escritura en DB: registramos o ignoramos, pero devolvemos los datos frescos
            }
            result = fresh
        }

        SectionPerfTelemetry.record(section = sectionKey, durationMs = duration, cacheHit = cacheHit)
        requireNotNull(result) { "Dashboard metrics cache computation returned null result" }
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
        const val DASHBOARD_SECTION_KEY = "dashboard_metrics_v2"
        private const val PURGE_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12 horas
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_RETENTION_DAYS = 30
        private const val MAX_RETENTION_DAYS = 365
        private const val HISTORICAL_SIGNATURE_BUCKET_MS = 5L * 60L * 1000L // 5 minutos

        // Instancia única para evitar sobrecarga de inicialización del serializador
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * Builds a signature without requiring the full measurements list to avoid expensive DB fetches
         * when checking for cache hits.
         */
        fun buildSignatureFast(
            dataVersion: Long,
            capillaries: List<com.tonio.libre2clock.data.model.CapillaryMeasurement>,
            ranges: List<GlucoseOffsetRange> = emptyList(),
            sensorLogs: List<SensorLog> = emptyList(),
            manualOffset: Int = 0,
            autoAdjust: Boolean = false,
            autoRangeMode: String = "OFF"
        ): String {
            val capSig = if (capillaries.isNotEmpty()) {
                val hashSum = capillaries.sumOf { it.hashCode().toLong() }
                "${capillaries.size}-$hashSum-${capillaries.first().timestamp}"
            } else "no-cap"

            val rangeSig = if (ranges.isNotEmpty()) {
                val sum = ranges.sumOf { it.offset + it.percentage }
                "${ranges.size}-$sum"
            } else "no-ranges"

            val logSig = if (sensorLogs.isNotEmpty()) {
                val last = sensorLogs.first()
                "${sensorLogs.size}-${last.serialNumber}-${last.startDate}"
            } else "no-logs"

            val timeBucket = System.currentTimeMillis() / HISTORICAL_SIGNATURE_BUCKET_MS

            // Pre-asignación de capacidad (64 chars) para evitar redimensionamientos del StringBuilder
            return buildString(64) {
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

            // OPTIMIZACIÓN: Un solo bucle es más rápido que dos llamadas a sumOf() en listas de 50k elementos
            var rawSum = 0L
            var calibratedSum = 0L
            for (m in measurements) {
                rawSum += m.value
                calibratedSum += m.calibratedValue
            }

            val first = measurements.first()
            val last = measurements.last()

            // Pre-asignación de capacidad (48 chars)
            return buildString(48) {
                append("c=").append(measurements.size)
                append(";rS=").append(rawSum)
                append(";cS=").append(calibratedSum)
                append(";f=").append(first.epochSeconds ?: first.factoryTimestamp)
                append(";l=").append(last.epochSeconds ?: last.factoryTimestamp)
            }
        }
    }
}