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
    val breakfastMonthAvg: DisplayMetric,
    val lunchMonthAvg: DisplayMetric,
    val dinnerMonthAvg: DisplayMetric,
    val breakfastHypos: CountMetric,
    val lunchHypos: CountMetric,
    val dinnerHypos: CountMetric
)

enum class MealSlot { BREAKFAST, LUNCH, DINNER }

object DashboardMetricsCalculator {

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
            // CRITICAL: Filter out invalid values and extreme outliers
            // Note: Keep a broader range to allow clinical validation
            if (m.value <= 0) return@forEach

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

        // HbA1c (GMI) Calculation
        // Standard clinical recommendation is to use at least 14 days of data for a reliable GMI.
        // We use the 90-day window. If it's empty, we don't show the estimate.
        val avgRawForA1c = if (a1cItems.isNotEmpty()) a1cItems.map { it.value }.average() else 0.0
        val avgCalibratedForA1c = if (a1cItems.isNotEmpty()) a1cItems.map { it.calibratedValue }.average() else 0.0
        
        val estimatedA1c = if (avgCalibratedForA1c > 10.0 && a1cItems.size > 2) { 
            val a1cRaw = (avgRawForA1c + 46.7) / 28.7
            val a1cCalibrated = (avgCalibratedForA1c + 46.7) / 28.7
            DisplayMetric(
                primary = String.format(Locale.US, "%.1f%%(%.1f%%)", a1cRaw, a1cCalibrated),
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

        val rawValues = measurements.map { it.value }
        val calibratedValues = measurements.map { it.calibratedValue }
        
        val avgRaw = rawValues.average().roundToInt()
        val avgCalibrated = calibratedValues.average().roundToInt()
        
        val maxRaw = rawValues.maxOrNull() ?: avgRaw
        val minRaw = rawValues.minOrNull() ?: avgRaw
        val oscRaw = maxOf(maxRaw - avgRaw, avgRaw - minRaw).coerceAtLeast(0)

        val maxCal = calibratedValues.maxOrNull() ?: avgCalibrated
        val minCal = calibratedValues.minOrNull() ?: avgCalibrated
        val oscCal = maxOf(maxCal - avgCalibrated, avgCalibrated - minCal).coerceAtLeast(0)

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
        return TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp) ?: TimestampParser.parseFlexibleInstant(measurement.timestamp)
    }
}
