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
import kotlin.math.min
import kotlin.system.measureTimeMillis
import java.util.concurrent.atomic.AtomicLong

class SettingsSectionCacheRepository(
    context: Context,
    private val json: Json = Companion.defaultJson
) {

    private val db = SectionCacheDatabaseHelper(context.applicationContext)
    
    // OPTIMIZACIÓN 1: AtomicLong para evitar purgas redundantes por condiciones de carrera ("thundering herd")
    private val lastPurgeAtMs = AtomicLong(0L)

    suspend fun getOrComputeRangeInsights(
        signature: String,
        retentionDays: Int,
        calculator: suspend () -> List<RangeOffsetInsight> // OPTIMIZACIÓN 2: 'suspend' permite operaciones pesadas sin bloquear el hilo
    ): List<RangeOffsetInsight> = withContext(Dispatchers.IO) {
        purgeIfNeeded(retentionDays)
        
        var result: List<RangeOffsetInsight>? = null
        var cacheHit = false
        
        val duration = measureTimeMillis {
            val cached = db.getCachedPayload(RANGE_INSIGHTS_SECTION_KEY, signature)
            if (cached != null) {
                try {
                    // OPTIMIZACIÓN 3: try-catch nativo es más rápido que runCatching (evita crear objetos Result/Exception)
                    result = json.decodeFromString<List<RangeOffsetInsight>>(cached)
                    cacheHit = true
                    return@measureTimeMillis
                } catch (e: Exception) {
                    // Caché corrupta o cambio de esquema: se ignora y se recalcula
                }
            }

            val fresh = calculator()
            try {
                db.upsertPayload(RANGE_INSIGHTS_SECTION_KEY, signature, json.encodeToString(fresh))
            } catch (e: Exception) {
                // Fallo de escritura en DB: ignoramos para no interrumpir el flujo, pero devolvemos los datos frescos
            }
            result = fresh
        }

        SectionPerfTelemetry.record(RANGE_INSIGHTS_SECTION_KEY, duration, cacheHit)
        requireNotNull(result) { "Settings range insights cache computation returned null result" }
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

    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        db.clearAll()
    }

    companion object {
        private const val RANGE_INSIGHTS_SECTION_KEY = "settings_range_insights_v1"
        private const val PURGE_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12 horas
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_RETENTION_DAYS = 30
        private const val MAX_RETENTION_DAYS = 365

        // OPTIMIZACIÓN 4: Instancia única de Json para evitar sobrecarga de inicialización del serializador
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun buildRangeInsightsSignature(
            ranges: List<GlucoseOffsetRange>,
            capillaries: List<CapillaryMeasurement>
        ): String {
            val rangeEdge = ranges.joinToString("|") { "${it.min}:${it.max ?: -1}:${it.offset}:${it.percentage}" }
            
            // OPTIMIZACIÓN 5: Evitar la asignación de memoria de .take(24) calculando el hash manualmente
            var capHash = 0
            val limit = min(24, capillaries.size)
            for (i in 0 until limit) {
                capHash = 31 * capHash + capillaries[i].hashCode()
            }
            
            return "ranges=${ranges.size};edge=$rangeEdge;caps=${capillaries.size};capsHash=$capHash"
        }
    }
}