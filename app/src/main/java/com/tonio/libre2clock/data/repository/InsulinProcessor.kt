package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.util.TimestampParser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object InsulinProcessor {

    /**
     * Calculates IOB for a single dose.
     */
    fun calculateIOB(dose: InsulinDose, atInstant: Instant = Instant.now()): Double {
        val doseInstant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: return 0.0
        return calculateIOBFromInstant(dose, doseInstant, atInstant)
    }

    /**
     * Internal optimized IOB calculator that reuses an already-parsed Instant.
     * Crucial for avoiding redundant parsing in loops (e.g., predictGlucosePath).
     */
    private fun calculateIOBFromInstant(dose: InsulinDose, doseInstant: Instant, atInstant: Instant): Double {
        val minutesPassed = Duration.between(doseInstant, atInstant).toMinutes().toInt()
        
        if (minutesPassed <= 0) return dose.units
        if (minutesPassed >= dose.durationMinutes) return 0.0
        
        return if (dose.type == InsulinType.RAPID) {
            val factorRestante: Double = when {
                minutesPassed < 60 -> 1.0 - (0.0033 * minutesPassed)
                minutesPassed in 60..179 -> {
                    val progreso = (minutesPassed - 60).toDouble() / 120.0
                    0.802 - (0.53885 * progreso)
                }
                minutesPassed in 180..184 -> {
                    val minDesde3Horas = minutesPassed - 180
                    0.26315 - (0.013155 * minDesde3Horas)
                }
                else -> {
                    val progresoFinal = (minutesPassed - 184).toDouble() / (dose.durationMinutes - 184).toDouble()
                    0.21053 * (1.0 - progresoFinal)
                }
            }
            // Preservamos el comportamiento original de redondeo
            (dose.units * factorRestante).roundToInt().toDouble()
        } else {
            val timeFraction = minutesPassed.toDouble() / dose.durationMinutes.toDouble()
            dose.units * (1.0 - timeFraction)
        }
    }

    fun calculateTotalIOB(doses: List<InsulinDose>, atInstant: Instant = Instant.now()): Double {
        if (doses.isEmpty()) return 0.0
        var total = 0.0
        for (dose in doses) {
            total += calculateIOB(dose, atInstant)
        }
        return total
    }

    /**
     * OPTIMIZACIÓN: Usa comparaciones de Instant en lugar de convertir a LocalDate en cada iteración.
     * Es órdenes de magnitud más rápido.
     */
    fun calculateDailyTotal(doses: List<InsulinDose>, date: LocalDate, type: InsulinType? = null): Double {
        if (doses.isEmpty()) return 0.0
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()

        var sum = 0.0
        for (dose in doses) {
            if (type != null && dose.type != type) continue
            
            val instant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: continue
            if (!instant.isBefore(startOfDay) && instant.isBefore(endOfDay)) {
                sum += dose.units
            }
        }
        return sum
    }

    fun calculateAverageDaily(doses: List<InsulinDose>, days: Int): Double {
        if (doses.isEmpty() || days <= 0) return 0.0
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val cutoffInstant = now.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
        val endInstant = now.plusDays(1).atStartOfDay(zone).toInstant()

        var total = 0.0
        for (dose in doses) {
            val instant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: continue
            if (!instant.isBefore(cutoffInstant) && instant.isBefore(endInstant)) {
                total += dose.units
            }
        }
        return total / days
    }

    data class SplitTotal(val rapid: Double, val slow: Double) {
        val total: Double get() = rapid + slow
    }

    /**
     * OPTIMIZACIÓN CRÍTICA: Pasada única (Single-Pass). 
     * El código original llamaba a calculateDailyTotal dos veces, parseando timestamps el doble de veces.
     */
    fun calculateDailyTotalSplit(doses: List<InsulinDose>, date: LocalDate): SplitTotal {
        if (doses.isEmpty()) return SplitTotal(0.0, 0.0)
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()

        var rapidSum = 0.0
        var slowSum = 0.0

        for (dose in doses) {
            val instant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: continue
            if (!instant.isBefore(startOfDay) && instant.isBefore(endOfDay)) {
                if (dose.type == InsulinType.RAPID) {
                    rapidSum += dose.units
                } else if (dose.type == InsulinType.SLOW) {
                    slowSum += dose.units
                }
            }
        }
        return SplitTotal(rapidSum, slowSum)
    }

    /**
     * OPTIMIZACIÓN: Pasada única con comparación de Instants (evita toLocalDate() en el bucle).
     */
    fun calculateAverageDailySplit(doses: List<InsulinDose>, days: Int): SplitTotal {
        if (doses.isEmpty() || days <= 0) return SplitTotal(0.0, 0.0)
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val cutoffInstant = now.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
        val endInstant = now.plusDays(1).atStartOfDay(zone).toInstant()

        var rapidSum = 0.0
        var slowSum = 0.0

        for (dose in doses) {
            val instant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: continue
            if (!instant.isBefore(cutoffInstant) && instant.isBefore(endInstant)) {
                if (dose.type == InsulinType.RAPID) {
                    rapidSum += dose.units
                } else if (dose.type == InsulinType.SLOW) {
                    slowSum += dose.units
                }
            }
        }
        return SplitTotal(rapidSum / days, slowSum / days)
    }

    fun calculateISF(tdi: Double, isfConstant: Int, manualIsf: Double?): Double {
        if (manualIsf != null) return manualIsf
        if (tdi <= 0.0) return 0.0
        return isfConstant.toDouble() / tdi
    }

    data class BolusBreakdown(
        val carbDose: Double,
        val correctionDose: Double,
        val total: Double
    )

    fun getSuggestedBolusDetailed(
        carbs: Double,
        currentGlucose: Int,
        targetGlucose: Int,
        tdi: Double,
        icConstant: Int,
        isf: Double,
        isBasalExpiringSoon: Boolean
    ): BolusBreakdown {
        if (tdi <= 0.0 && isf <= 0.0) return BolusBreakdown(0.0, 0.0, 0.0)
        
        val icRatio = if (tdi > 0) icConstant / tdi else 0.0
        val carbDose = if (icRatio > 0) carbs / icRatio else 0.0
        val correctionDose = if (isf > 0) (currentGlucose - targetGlucose).toDouble() / isf else 0.0
        
        var total = carbDose + correctionDose
        if (isBasalExpiringSoon) {
            total *= 1.20
        }
        
        return BolusBreakdown(
            carbDose = carbDose,
            correctionDose = max(0.0, correctionDose),
            total = max(0.0, total)
        )
    }

    fun getSuggestedBolus(
        carbs: Double,
        currentGlucose: Int,
        targetGlucose: Int,
        tdi: Double,
        icConstant: Int,
        isf: Double,
        isBasalExpiringSoon: Boolean
    ): Double {
        return getSuggestedBolusDetailed(carbs, currentGlucose, targetGlucose, tdi, icConstant, isf, isBasalExpiringSoon).total
    }

    /**
     * OPTIMIZACIÓN: Añadido Locale.US para evitar que en países con coma decimal (ej. España) 
     * se genere "1,50(2,00)" en lugar de "1.50(2.00)", lo cual podría romper parsers o UIs.
     */
    fun formatDualValue(real: Double, calibrated: Double): String {
        return String.format(Locale.US, "%.2f(%.2f)", real, calibrated)
    }

    /**
     * OPTIMIZACIÓN: Pasada única (Single-Pass) para encontrar la dosis lenta más reciente.
     * Evita filter() + maxByOrNull() que crean listas intermedias y parsean 2 veces.
     */
    fun isBasalExpiringSoon(doses: List<InsulinDose>, now: Instant = Instant.now(), warningWindowMinutes: Int = 120): Boolean {
        var latestSlowDose: InsulinDose? = null
        var maxInstant = Instant.MIN

        for (dose in doses) {
            if (dose.type == InsulinType.SLOW) {
                val instant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: continue
                if (instant > maxInstant) {
                    maxInstant = instant
                    latestSlowDose = dose
                }
            }
        }

        val dose = latestSlowDose ?: return false
        val expiryInstant = maxInstant.plus(Duration.ofMinutes(dose.durationMinutes.toLong()))
        val minutesRemaining = Duration.between(now, expiryInstant).toMinutes()
        
        return minutesRemaining in 0..warningWindowMinutes.toLong()
    }

    /**
     * OPTIMIZACIÓN CRÍTICA: Pre-parsea los timestamps UNA SOLA VEZ.
     * El código original llamaba a calculateTotalIOB en cada paso del bucle, 
     * lo que provocaba parsear todos los timestamps 12 veces (una por paso).
     */
    fun predictGlucosePath(
        currentGlucose: Int,
        doses: List<InsulinDose>,
        isf: Double,
        steps: Int = 12,
        intervalMinutes: Long = 15
    ): List<Pair<Instant, Int>> {
        if (doses.isEmpty() || isf <= 0.0) return emptyList()
        
        // Pre-parseo: (Instant, InsulinDose). Filtramos los nulos una sola vez.
        val parsedDoses = doses.mapNotNull { dose ->
            TimestampParser.parseFlexibleInstant(dose.timestamp)?.let { it to dose }
        }
        if (parsedDoses.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<Instant, Int>>()
        val now = Instant.now()
        
        // Calcular IOB inicial usando los datos ya parseados
        var lastIOB = 0.0
        for ((instant, dose) in parsedDoses) {
            lastIOB += calculateIOBFromInstant(dose, instant, now)
        }
        
        var predictedG = currentGlucose.toDouble()

        for (i in 1..steps) {
            val futureTime = now.plus(Duration.ofMinutes(i * intervalMinutes))
            var futureIOB = 0.0
            
            // Cálculo inline usando los datos pre-parseados (evita llamadas a función y re-parseo)
            for ((instant, dose) in parsedDoses) {
                futureIOB += calculateIOBFromInstant(dose, instant, futureTime)
            }
            
            val absorbedInInterval = lastIOB - futureIOB
            val drop = absorbedInInterval * isf
            
            predictedG -= drop
            result.add(futureTime to predictedG.roundToInt().coerceAtLeast(40))
            
            lastIOB = futureIOB
            if (futureIOB <= 0.0) break // Optimización: si no queda insulina, no tiene sentido seguir prediciendo
        }
        
        return result
    }
}