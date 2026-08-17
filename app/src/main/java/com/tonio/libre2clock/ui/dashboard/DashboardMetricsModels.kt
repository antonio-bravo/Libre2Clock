package com.tonio.libre2clock.ui.dashboard

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
data class DisplayMetric(
    val primary: String,
    val secondary: String
)

@Serializable
data class CountMetric(
    val count: Int,
    val offset: Int
)

@Serializable
data class DashboardMetrics(
    val estimatedA1c: DisplayMetric,
    val todayAvg: DisplayMetric,
    val yesterdayAvg: DisplayMetric,
    val weekAvg: DisplayMetric,
    val monthAvg: DisplayMetric,
    val quarterAvg: DisplayMetric,
    val breakfastMonthAvg: DisplayMetric,
    val lunchMonthAvg: DisplayMetric,
    val dinnerMonthAvg: DisplayMetric,
    val breakfastHypos: CountMetric,
    val lunchHypos: CountMetric,
    val dinnerHypos: CountMetric
)

enum class MealSlot { BREAKFAST, LUNCH, DINNER }

object DashboardMetricsCalculator {

    fun calculateLive(measurements: List<GlucoseMeasurement>): DashboardMetrics {
        if (measurements.isEmpty()) return emptyDashboardMetrics()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val startOfYesterday = today.minusDays(1).atStartOfDay(zone).toInstant()

        val todayItems = mutableListOf<GlucoseMeasurement>()
        val yesterdayItems = mutableListOf<GlucoseMeasurement>()

        measurements.forEach { m ->
            // Filter out failure values (<= 40 mg/dL)
            if (m.value <= 40) return@forEach
            val instant = parseMeasurementInstant(m) ?: return@forEach
            
            val beforeToday = instant.isBefore(startOfToday)
            val notBeforeYesterday = !instant.isBefore(startOfYesterday)

            if (!beforeToday) {
                todayItems.add(m)
            } else if (notBeforeYesterday) {
                yesterdayItems.add(m)
            }
        }

        return emptyDashboardMetrics().copy(
            todayAvg = buildDisplayMetric(todayItems),
            yesterdayAvg = buildDisplayMetric(yesterdayItems)
        )
    }

    fun calculateHistorical(measurements: List<GlucoseMeasurement>): DashboardMetrics {
        if (measurements.isEmpty()) return emptyDashboardMetrics()

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val startOfWeekWindow = now.minusSeconds(7L * 24L * 60L * 60L)
        val startOfMonthWindow = now.minusSeconds(30L * 24L * 60L * 60L)
        val startOfA1cWindow = now.minusSeconds(90L * 24L * 60L * 60L)

        val weekItems = mutableListOf<GlucoseMeasurement>()
        val monthItems = mutableListOf<GlucoseMeasurement>()
        val a1cItems = mutableListOf<GlucoseMeasurement>()
        
        val breakfastMonth = mutableListOf<GlucoseMeasurement>()
        val lunchMonth = mutableListOf<GlucoseMeasurement>()
        val dinnerMonth = mutableListOf<GlucoseMeasurement>()

        var minA1cInstant = now
        var maxA1cInstant = Instant.EPOCH

        measurements.forEach { m ->
            // Filter out common sensor failure values (e.g. exactly 40 or 0)
            if (m.value <= 40) return@forEach
            val instant = parseMeasurementInstant(m) ?: return@forEach
            
            if (!instant.isBefore(startOfWeekWindow)) {
                weekItems.add(m)
            }
            if (!instant.isBefore(startOfMonthWindow)) {
                monthItems.add(m)
                when (mealSlotOf(instant, zone)) {
                    MealSlot.BREAKFAST -> breakfastMonth.add(m)
                    MealSlot.LUNCH -> lunchMonth.add(m)
                    MealSlot.DINNER -> dinnerMonth.add(m)
                    null -> {}
                }
            }
            if (!instant.isBefore(startOfA1cWindow)) {
                a1cItems.add(m)
                if (instant.isBefore(minA1cInstant)) minA1cInstant = instant
                if (instant.isAfter(maxA1cInstant)) maxA1cInstant = instant
            }
        }

        val avgRawForA1c = if (a1cItems.isNotEmpty()) a1cItems.map { it.value }.average() else 0.0
        val avgCalibratedForA1c = if (a1cItems.isNotEmpty()) a1cItems.map { it.calibratedValue }.average() else 0.0
        
        val estimatedA1c = if (avgCalibratedForA1c > 10.0 && a1cItems.size > 2) { 
            val gmiRaw = 3.31 + (0.02392 * avgRawForA1c)
            val gmiCalibrated = 3.31 + (0.02392 * avgCalibratedForA1c)
            DisplayMetric(
                primary = String.format(Locale.US, "%.1f%%(%.1f%%)", gmiRaw, gmiCalibrated),
                secondary = ""
            )
        } else {
            DisplayMetric("--", "")
        }

        return DashboardMetrics(
            estimatedA1c = estimatedA1c,
            todayAvg = DisplayMetric("--", ""),
            yesterdayAvg = DisplayMetric("--", ""),
            weekAvg = buildDisplayMetric(weekItems),
            monthAvg = buildDisplayMetric(monthItems),
            quarterAvg = buildDisplayMetric(a1cItems),
            breakfastMonthAvg = buildDisplayMetric(breakfastMonth),
            lunchMonthAvg = buildDisplayMetric(lunchMonth),
            dinnerMonthAvg = buildDisplayMetric(dinnerMonth),
            breakfastHypos = buildHypoCountMetric(breakfastMonth),
            lunchHypos = buildHypoCountMetric(lunchMonth),
            dinnerHypos = buildHypoCountMetric(dinnerMonth)
        )
    }

    fun calculate(measurements: List<GlucoseMeasurement>): DashboardMetrics {
        if (measurements.isEmpty()) return emptyDashboardMetrics()

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val startOfYesterday = today.minusDays(1).atStartOfDay(zone).toInstant()
        val startOfWeekWindow = now.minusSeconds(7L * 24L * 60L * 60L)
        val startOfMonthWindow = now.minusSeconds(30L * 24L * 60L * 60L)
        val startOfA1cWindow = now.minusSeconds(90L * 24L * 60L * 60L)

        val todayItems = mutableListOf<GlucoseMeasurement>()
        val yesterdayItems = mutableListOf<GlucoseMeasurement>()
        val weekItems = mutableListOf<GlucoseMeasurement>()
        val monthItems = mutableListOf<GlucoseMeasurement>()
        val a1cItems = mutableListOf<GlucoseMeasurement>()
        
        val breakfastMonth = mutableListOf<GlucoseMeasurement>()
        val lunchMonth = mutableListOf<GlucoseMeasurement>()
        val dinnerMonth = mutableListOf<GlucoseMeasurement>()

        measurements.forEach { m ->
            // CRITICAL: Filter out invalid values (Libre sensors usually report 40 as the minimum floor or failure)
            if (m.value <= 40) return@forEach

            val instant = parseMeasurementInstant(m) ?: return@forEach
            
            // Only group measurements that have a clear date relative to Today
            val beforeToday = instant.isBefore(startOfToday)
            val notBeforeYesterday = !instant.isBefore(startOfYesterday)

            if (!beforeToday) {
                todayItems.add(m)
            } else if (notBeforeYesterday) {
                yesterdayItems.add(m)
            }
            
            if (!instant.isBefore(startOfWeekWindow)) {
                weekItems.add(m)
            }
            
            if (!instant.isBefore(startOfMonthWindow)) {
                monthItems.add(m)
                when (mealSlotOf(instant, zone)) {
                    MealSlot.BREAKFAST -> breakfastMonth.add(m)
                    MealSlot.LUNCH -> lunchMonth.add(m)
                    MealSlot.DINNER -> dinnerMonth.add(m)
                    null -> {}
                }
            }
            
            if (!instant.isBefore(startOfA1cWindow)) {
                a1cItems.add(m)
            }
        }

        // HbA1c / GMI Calculation
        // Standard clinical recommendation is to use at least 14 days of data for a reliable GMI.
        // GMI formula (Standard for CGMs): GMI (%) = 3.31 + 0.02392 * [mean glucose in mg/dL]
        val avgRawForA1c = if (a1cItems.isNotEmpty()) a1cItems.map { it.value }.average() else 0.0
        val avgCalibratedForA1c = if (a1cItems.isNotEmpty()) a1cItems.map { it.calibratedValue }.average() else 0.0
        
        val estimatedA1c = if (avgCalibratedForA1c > 10.0 && a1cItems.size > 2) { 
            val gmiRaw = 3.31 + (0.02392 * avgRawForA1c)
            val gmiCalibrated = 3.31 + (0.02392 * avgCalibratedForA1c)
            DisplayMetric(
                primary = String.format(Locale.US, "%.1f%%(%.1f%%)", gmiRaw, gmiCalibrated),
                secondary = ""
            )
        } else {
            DisplayMetric("--", "")
        }

        return DashboardMetrics(
            estimatedA1c = estimatedA1c,
            todayAvg = buildDisplayMetric(todayItems),
            yesterdayAvg = buildDisplayMetric(yesterdayItems),
            weekAvg = buildDisplayMetric(weekItems),
            monthAvg = buildDisplayMetric(monthItems),
            quarterAvg = buildDisplayMetric(a1cItems),
            breakfastMonthAvg = buildDisplayMetric(breakfastMonth),
            lunchMonthAvg = buildDisplayMetric(lunchMonth),
            dinnerMonthAvg = buildDisplayMetric(dinnerMonth),
            breakfastHypos = buildHypoCountMetric(breakfastMonth),
            lunchHypos = buildHypoCountMetric(lunchMonth),
            dinnerHypos = buildHypoCountMetric(dinnerMonth)
        )
    }

    private fun emptyDashboardMetrics() = DashboardMetrics(
        estimatedA1c = DisplayMetric("--", ""),
        todayAvg = DisplayMetric("--", ""),
        yesterdayAvg = DisplayMetric("--", ""),
        weekAvg = DisplayMetric("--", ""),
        monthAvg = DisplayMetric("--", ""),
        quarterAvg = DisplayMetric("--", ""),
        breakfastMonthAvg = DisplayMetric("--", ""),
        lunchMonthAvg = DisplayMetric("--", ""),
        dinnerMonthAvg = DisplayMetric("--", ""),
        breakfastHypos = CountMetric(0, 0),
        lunchHypos = CountMetric(0, 0),
        dinnerHypos = CountMetric(0, 0)
    )

    private fun buildDisplayMetric(measurements: List<GlucoseMeasurement>): DisplayMetric {
        if (measurements.isEmpty()) {
            return DisplayMetric(primary = "--", secondary = "")
        }

        var sumRaw = 0.0
        var sumCal = 0.0
        var maxRaw = Double.MIN_VALUE
        var minRaw = Double.MAX_VALUE
        var maxCal = Double.MIN_VALUE
        var minCal = Double.MAX_VALUE

        measurements.forEach { m ->
            val rv = m.value.toDouble()
            val cv = m.calibratedValue.toDouble()
            sumRaw += rv
            sumCal += cv
            if (rv > maxRaw) maxRaw = rv
            if (rv < minRaw) minRaw = rv
            if (cv > maxCal) maxCal = cv
            if (cv < minCal) minCal = cv
        }

        val count = measurements.size
        val avgRaw = (sumRaw / count).roundToInt()
        val avgCalibrated = (sumCal / count).roundToInt()
        
        val oscRaw = maxOf(maxRaw.roundToInt() - avgRaw, avgRaw - minRaw.roundToInt()).coerceAtLeast(0)
        val oscCal = maxOf(maxCal.roundToInt() - avgCalibrated, avgCalibrated - minCal.roundToInt()).coerceAtLeast(0)

        return DisplayMetric(
            primary = "Avg $avgRaw ± $oscRaw",
            secondary = "(avg $avgCalibrated ± $oscCal)"
        )
    }

    private fun buildHypoCountMetric(measurements: List<GlucoseMeasurement>): CountMetric {
        if (measurements.isEmpty()) return CountMetric(count = 0, offset = 0)

        val calibratedHypoCount = measurements.count { it.calibratedValue < 70 }
        val rawHypoCount = measurements.count { it.value < 70 }
        return CountMetric(
            count = calibratedHypoCount,
            offset = calibratedHypoCount - rawHypoCount
        )
    }

    private fun mealSlotOf(instant: Instant, zone: ZoneId): MealSlot? {
        val time = instant.atZone(zone).toLocalTime()
        return when {
            time >= LocalTime.of(5, 0) && time < LocalTime.of(12, 0) -> MealSlot.BREAKFAST
            time >= LocalTime.of(12, 0) && time < LocalTime.of(17, 0) -> MealSlot.LUNCH
            time >= LocalTime.of(17, 0) -> MealSlot.DINNER
            else -> null
        }
    }

    private fun parseMeasurementInstant(measurement: GlucoseMeasurement): Instant? {
        measurement.epochSeconds?.let { return Instant.ofEpochSecond(it) }
        return TimestampParser.parseFlexibleInstant(measurement.timestamp)
            ?: TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp)
    }
}
