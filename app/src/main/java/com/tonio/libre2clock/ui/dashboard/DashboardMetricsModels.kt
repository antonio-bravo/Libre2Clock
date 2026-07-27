package com.tonio.libre2clock.ui.dashboard

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.util.TimestampParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class DisplayMetric(
    val primary: String,
    val secondary: String
)

data class CountMetric(
    val count: Int,
    val offset: Int
)

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
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val startOfYesterday = today.minusDays(1).atStartOfDay(zone).toInstant()
        val startOfWeekWindow = now.minusSeconds(7L * 24L * 60L * 60L)
        val startOfMonthWindow = now.minusSeconds(30L * 24L * 60L * 60L)
        val startOfA1cWindow = now.minusSeconds(90L * 24L * 60L * 60L)

        val dated = measurements.mapNotNull { m ->
            parseMeasurementInstant(m)?.let { instant -> instant to m }
        }

        val todayItems = dated.filter { (instant, _) -> !instant.isBefore(startOfToday) }.map { it.second }
        val yesterdayItems = dated.filter { (instant, _) -> instant >= startOfYesterday && instant < startOfToday }.map { it.second }
        val weekItems = dated.filter { (instant, _) -> !instant.isBefore(startOfWeekWindow) }.map { it.second }
        val monthItems = dated.filter { (instant, _) -> !instant.isBefore(startOfMonthWindow) }.map { it.second }

        val breakfastMonth = dated.filter { (instant, _) ->
            !instant.isBefore(startOfMonthWindow) && mealSlotOf(instant, zone) == MealSlot.BREAKFAST
        }.map { it.second }
        val lunchMonth = dated.filter { (instant, _) ->
            !instant.isBefore(startOfMonthWindow) && mealSlotOf(instant, zone) == MealSlot.LUNCH
        }.map { it.second }
        val dinnerMonth = dated.filter { (instant, _) ->
            !instant.isBefore(startOfMonthWindow) && mealSlotOf(instant, zone) == MealSlot.DINNER
        }.map { it.second }

        val a1cWindowItems = dated.filter { (instant, _) -> !instant.isBefore(startOfA1cWindow) }.map { it.second }
        val allForA1c = if (a1cWindowItems.isNotEmpty()) a1cWindowItems else measurements
        
        val avgRawForA1c = if (allForA1c.isNotEmpty()) allForA1c.map { it.value }.average() else 0.0
        val avgCalibratedForA1c = if (allForA1c.isNotEmpty()) allForA1c.map { it.calibratedValue }.average() else 0.0
        
        val estimatedA1c = if (avgCalibratedForA1c > 0.0) {
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
