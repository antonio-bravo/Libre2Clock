package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.util.TimestampParser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

import kotlin.math.roundToInt

object InsulinProcessor {

    fun calculateIOB(dose: InsulinDose, atInstant: Instant = Instant.now()): Double {
        val doseInstant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: return 0.0
        val minutesPassed = Duration.between(doseInstant, atInstant).toMinutes().toInt()
        
        if (minutesPassed <= 0) return dose.units
        if (minutesPassed >= dose.durationMinutes) return 0.0
        
        return if (dose.type == InsulinType.RAPID) {
            // Personalized 4-stage algorithm for Rapid Insulin
            val factorRestante: Double = when {
                // Tramo 1: Hora 1 (0 a 59 min)
                minutesPassed < 60 -> {
                    1.0 - (0.0033 * minutesPassed)
                }
                // Tramo 2: Hora 2 y 3 (60 a 179 min)
                minutesPassed in 60..179 -> {
                    val progreso = (minutesPassed - 60).toDouble() / 120.0
                    0.802 - (0.53885 * progreso)
                }
                // Tramo 3: Ventana crítica (180 a 184 min)
                minutesPassed in 180..184 -> {
                    val minDesde3Horas = minutesPassed - 180
                    0.26315 - (0.013155 * minDesde3Horas)
                }
                // Tramo 4: Cola final (185 a 239 min)
                else -> {
                    val progresoFinal = (minutesPassed - 184).toDouble() / (dose.durationMinutes - 184).toDouble()
                    0.21053 * (1.0 - progresoFinal)
                }
            }
            (dose.units * factorRestante).roundToInt().toDouble()
        } else {
            // Linear decay for SLOW/Basal: IOB = Units * (1 - t/D)
            val timeFraction = minutesPassed.toDouble() / dose.durationMinutes.toDouble()
            dose.units * (1.0 - timeFraction)
        }
    }

    fun calculateTotalIOB(doses: List<InsulinDose>, atInstant: Instant = Instant.now()): Double {
        return doses.sumOf { calculateIOB(it, atInstant) }
    }

    fun calculateDailyTotal(doses: List<InsulinDose>, date: LocalDate, type: InsulinType? = null): Double {
        val zone = ZoneId.systemDefault()
        return doses.filter { dose ->
            val doseDate = TimestampParser.parseFlexibleInstant(dose.timestamp)
                ?.atZone(zone)?.toLocalDate()
            doseDate == date && (type == null || dose.type == type)
        }.sumOf { it.units }
    }

    fun calculateAverageDaily(doses: List<InsulinDose>, days: Int): Double {
        if (doses.isEmpty() || days <= 0) return 0.0
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now()
        val cutoff = now.minusDays((days - 1).toLong())
        // Parse each timestamp once instead of re-scanning/re-parsing the full list per day.
        val total = doses.sumOf { dose ->
            val doseDate = TimestampParser.parseFlexibleInstant(dose.timestamp)?.atZone(zone)?.toLocalDate()
            if (doseDate != null && doseDate in cutoff..now) dose.units else 0.0
        }
        return total / days
    }

    data class SplitTotal(val rapid: Double, val slow: Double) {
        val total: Double get() = rapid + slow
    }

    fun calculateDailyTotalSplit(doses: List<InsulinDose>, date: LocalDate): SplitTotal {
        return SplitTotal(
            rapid = calculateDailyTotal(doses, date, InsulinType.RAPID),
            slow = calculateDailyTotal(doses, date, InsulinType.SLOW)
        )
    }

    fun calculateAverageDailySplit(doses: List<InsulinDose>, days: Int): SplitTotal {
        if (doses.isEmpty() || days <= 0) return SplitTotal(0.0, 0.0)
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now()
        val cutoff = now.minusDays((days - 1).toLong())
        // Parse each timestamp once instead of re-scanning/re-parsing the full list per day/type.
        var rapidSum = 0.0
        var slowSum = 0.0
        for (dose in doses) {
            val doseDate = TimestampParser.parseFlexibleInstant(dose.timestamp)?.atZone(zone)?.toLocalDate() ?: continue
            if (doseDate !in cutoff..now) continue
            when (dose.type) {
                InsulinType.RAPID -> rapidSum += dose.units
                InsulinType.SLOW -> slowSum += dose.units
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

    fun formatDualValue(real: Double, calibrated: Double): String {
        return "%.2f(%.2f)".format(real, calibrated)
    }

    fun isBasalExpiringSoon(doses: List<InsulinDose>, now: Instant = Instant.now(), warningWindowMinutes: Int = 120): Boolean {
        val lastSlowDose = doses.filter { it.type == InsulinType.SLOW }
            .maxByOrNull { TimestampParser.parseFlexibleInstant(it.timestamp) ?: Instant.MIN } ?: return false
            
        val doseInstant = TimestampParser.parseFlexibleInstant(lastSlowDose.timestamp) ?: return false
        val expiryInstant = doseInstant.plus(Duration.ofMinutes(lastSlowDose.durationMinutes.toLong()))
        
        val minutesRemaining = Duration.between(now, expiryInstant).toMinutes()
        return minutesRemaining in 0..warningWindowMinutes.toLong()
    }
}
