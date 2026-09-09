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
            // Filter out common sensor failure values (e.g. exactly 40 or 0)
            if (m.value <= 40) return@forEach
            val instant = parseMeasurementInstant(m) ?: return@forEach
            
            // Only group measurements that have a clear date relative to Today
            val isYesterday = instant.isBefore(startOfToday) && !instant.isBefore(startOfYesterday)
            val isToday = !instant.isBefore(startOfToday)

            if (isToday) {
                todayItems.add(m)
            } else if (isYesterday) {
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

        var a1cRawSum = 0L
        var a1cCalibratedSum = 0L

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
                a1cRawSum += m.value
                a1cCalibratedSum += m.calibratedValue
            }
        }

        val dailyAverages = a1cItems.groupBy { m ->
            parseMeasurementInstant(m)?.atZone(zone)?.toLocalDate()
        }.filterKeys { it != null }.values.map { day ->
            day.map { it.value.toDouble() }.average() to day.map { it.calibratedValue.toDouble() }.average()
        }

        val avgRawForA1c = if (dailyAverages.isNotEmpty()) dailyAverages.map { it.first }.average() else 0.0
        val avgCalibratedForA1c = if (dailyAverages.isNotEmpty()) dailyAverages.map { it.second }.average() else 0.0
        
        val estimatedA1c = if (avgCalibratedForA1c > 40.0 && a1cItems.size > 100) { 
            // ADAG formula: HbA1c (%) = (mean_glucose + 46.7) / 28.7
            val a1cRaw = (avgRawForA1c + 46.7) / 28.7
            val a1cCalibrated = (avgCalibratedForA1c + 46.7) / 28.7
            DisplayMetric(
                primary = String.format(Locale.US, "%.1f%% (%.1f%%)", a1cCalibrated, a1cRaw),
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
            val isYesterday = instant.isBefore(startOfToday) && !instant.isBefore(startOfYesterday)
            val isToday = !instant.isBefore(startOfToday)

            if (isToday) {
                todayItems.add(m)
            } else if (isYesterday) {
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

        val dailyAverages = a1cItems.groupBy { m ->
            parseMeasurementInstant(m)?.atZone(zone)?.toLocalDate()
        }.filterKeys { it != null }.values.map { day ->
            day.map { it.value.toDouble() }.average() to day.map { it.calibratedValue.toDouble() }.average()
        }

        val avgRawForA1c = if (dailyAverages.isNotEmpty()) dailyAverages.map { it.first }.average() else 0.0
        val avgCalibratedForA1c = if (dailyAverages.isNotEmpty()) dailyAverages.map { it.second }.average() else 0.0
        
        val estimatedA1c = if (avgCalibratedForA1c > 40.0 && a1cItems.size > 100) { 
            val a1cRaw = (avgRawForA1c + 46.7) / 28.7
            val a1cCalibrated = (avgCalibratedForA1c + 46.7) / 28.7
            DisplayMetric(
                primary = String.format(Locale.US, "%.1f%% (%.1f%%)", a1cCalibrated, a1cRaw),
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

        val zone = ZoneId.systemDefault()
        val dailyGroups = measurements.groupBy { m ->
            parseMeasurementInstant(m)?.atZone(zone)?.toLocalDate()
        }.filterKeys { it != null }

        if (dailyGroups.isEmpty()) return DisplayMetric("--", "")

        val dailyAveragesRaw = mutableListOf<Double>()
        val dailyAveragesCal = mutableListOf<Double>()
        var maxRaw = Double.MIN_VALUE
        var minRaw = Double.MAX_VALUE
        var maxCal = Double.MIN_VALUE
        var minCal = Double.MAX_VALUE

        dailyGroups.values.forEach { dayPoints ->
            val rawSum = dayPoints.sumOf { it.value.toDouble() }
            val calSum = dayPoints.sumOf { it.calibratedValue.toDouble() }
            val count = dayPoints.size
            dailyAveragesRaw.add(rawSum / count)
            dailyAveragesCal.add(calSum / count)

            dayPoints.forEach { m ->
                val rv = m.value.toDouble()
                val cv = m.calibratedValue.toDouble()
                if (rv > maxRaw) maxRaw = rv
                if (rv < minRaw) minRaw = rv
                if (cv > maxCal) maxCal = cv
                if (cv < minCal) minCal = cv
            }
        }

        val avgRaw = dailyAveragesRaw.average().roundToInt()
        val avgCalibrated = dailyAveragesCal.average().roundToInt()
        
        val oscRaw = maxOf(maxRaw.roundToInt() - avgRaw, avgRaw - minRaw.roundToInt()).coerceAtLeast(0)
        val oscCal = maxOf(maxCal.roundToInt() - avgCalibrated, avgCalibrated - minCal.roundToInt()).coerceAtLeast(0)

        return DisplayMetric(
            primary = "$avgCalibrated ± $oscCal ($avgRaw ± $oscRaw)",
            secondary = ""
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
