package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.util.TimestampParser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

object InsulinProcessor {

    fun calculateIOB(dose: InsulinDose, atInstant: Instant = Instant.now()): Double {
        val doseInstant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: return 0.0
        val minutesPassed = Duration.between(doseInstant, atInstant).toMinutes()
        
        if (minutesPassed < 0) return 0.0
        if (minutesPassed >= dose.durationMinutes) return 0.0
        
        // Linear decay: IOB = Units * (1 - t/D)
        return dose.units * (1.0 - (minutesPassed.toDouble() / dose.durationMinutes.toDouble()))
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
        val now = LocalDate.now()
        var total = 0.0
        for (i in 0 until days) {
            total += calculateDailyTotal(doses, now.minusDays(i.toLong()))
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
        val now = LocalDate.now()
        var rapidSum = 0.0
        var slowSum = 0.0
        for (i in 0 until days) {
            val split = calculateDailyTotalSplit(doses, now.minusDays(i.toLong()))
            rapidSum += split.rapid
            slowSum += split.slow
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
