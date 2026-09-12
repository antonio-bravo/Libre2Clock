package com.tonio.libre2clock.ui.dashboard

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
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

object DashboardMetricsCalculator {

    fun calculateLive(measurements: List<GlucoseMeasurement>): DashboardMetrics {
        return calculate(measurements)
    }

    fun calculateHistorical(measurements: List<GlucoseMeasurement>): DashboardMetrics {
        val full = calculate(measurements)
        return full.copy(
            todayAvg = DisplayMetric("--", ""),
            yesterdayAvg = DisplayMetric("--", "")
        )
    }

    fun calculate(measurements: List<GlucoseMeasurement>): DashboardMetrics {
        if (measurements.isEmpty()) return emptyDashboardMetrics()

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val todayDate = LocalDate.now(zone)
        val yesterdayDate = todayDate.minusDays(1)
        
        val startOfToday = todayDate.atStartOfDay(zone).toInstant()
        val startOfYesterday = yesterdayDate.atStartOfDay(zone).toInstant()
        val startOfWeek = now.minusSeconds(7L * 24 * 60 * 60)
        val startOfMonth = now.minusSeconds(30L * 24 * 60 * 60)
        val startOfA1c = now.minusSeconds(90L * 24 * 60 * 60)

        val weekStartDate = startOfWeek.atZone(zone).toLocalDate()
        val monthStartDate = startOfMonth.atZone(zone).toLocalDate()
        val a1cStartDate = startOfA1c.atZone(zone).toLocalDate()

        val allDailyStats = mutableMapOf<LocalDate, DailyStats>()
        
        var breakfastHypoCal = 0; var breakfastHypoRaw = 0
        var lunchHypoCal = 0; var lunchHypoRaw = 0
        var dinnerHypoCal = 0; var dinnerHypoRaw = 0

        // PASO ÚNICO (O(N)): Procesamos cada medición una sola vez
        for (m in measurements) {
            if (m.value <= 40) continue
            
            val instant = parseMeasurementInstant(m) ?: continue
            
            // OPTIMIZACIÓN: Llamamos a atZone una sola vez y reutilizamos el resultado
            val zdt = instant.atZone(zone)
            val date = zdt.toLocalDate()
            
            val stats = allDailyStats.getOrPut(date) { DailyStats() }
            val raw = m.value.toDouble()
            val cal = m.calibratedValue.toDouble()
            
            stats.sumRaw += raw
            stats.sumCal += cal
            stats.count++
            if (raw > stats.maxRaw) stats.maxRaw = raw
            if (raw < stats.minRaw) stats.minRaw = raw
            if (cal > stats.maxCal) stats.maxCal = cal
            if (cal < stats.minCal) stats.minCal = cal

            if (!instant.isBefore(startOfMonth)) {
                val hour = zdt.hour
                val isHypoCal = cal < 70.0
                val isHypoRaw = raw < 70.0
                
                // OPTIMIZACIÓN: if-else if plano es más rápido que 'when' para rangos numéricos
                if (hour in 5..11) {
                    if (isHypoCal) breakfastHypoCal++
                    if (isHypoRaw) breakfastHypoRaw++
                } else if (hour in 12..16) {
                    if (isHypoCal) lunchHypoCal++
                    if (isHypoRaw) lunchHypoRaw++
                } else if (hour >= 17) {
                    if (isHypoCal) dinnerHypoCal++
                    if (isHypoRaw) dinnerHypoRaw++
                }
            }
        }

        // OPTIMIZACIÓN: Cero asignaciones de memoria. Acumulamos en variables primitivas
        // en lugar de usar filterKeys + map + average + maxOfOrNull + minOfOrNull
        fun buildMetricFromDays(datePredicate: (LocalDate) -> Boolean): DisplayMetric {
            var sumRaw = 0.0
            var sumCal = 0.0
            var count = 0
            var maxRaw = 0.0
            var minRaw = Double.MAX_VALUE
            var maxCal = 0.0
            var minCal = Double.MAX_VALUE

            for ((date, stats) in allDailyStats) {
                if (datePredicate(date)) {
                    count++
                    sumRaw += stats.sumRaw / stats.count
                    sumCal += stats.sumCal / stats.count
                    if (stats.maxRaw > maxRaw) maxRaw = stats.maxRaw
                    if (stats.minRaw < minRaw) minRaw = stats.minRaw
                    if (stats.maxCal > maxCal) maxCal = stats.maxCal
                    if (stats.minCal < minCal) minCal = stats.minCal
                }
            }

            if (count == 0) return DisplayMetric("--", "")

            val avgRaw = (sumRaw / count).roundToInt()
            val avgCal = (sumCal / count).roundToInt()
            
            val oscRaw = max(maxRaw.roundToInt() - avgRaw, avgRaw - minRaw.roundToInt()).coerceAtLeast(0)
            val oscCal = max(maxCal.roundToInt() - avgCal, avgCal - minCal.roundToInt()).coerceAtLeast(0)

            return DisplayMetric(
                primary = "$avgRaw ± $oscRaw",
                secondary = "($avgCal ± $oscCal)"
            )
        }

        // OPTIMIZACIÓN: Cálculo de A1c en una sola pasada sin colecciones intermedias
        var a1cSumRaw = 0.0
        var a1cSumCal = 0.0
        var a1cDaysCount = 0
        var a1cTotalMeasurements = 0

        for ((date, stats) in allDailyStats) {
            if (date >= a1cStartDate) {
                a1cDaysCount++
                a1cSumRaw += stats.sumRaw / stats.count
                a1cSumCal += stats.sumCal / stats.count
                a1cTotalMeasurements += stats.count
            }
        }

        val estimatedA1c = if (a1cDaysCount > 0 && a1cTotalMeasurements > 100) {
            val avgRawForA1c = a1cSumRaw / a1cDaysCount
            val avgCalForA1c = a1cSumCal / a1cDaysCount
            
            if (avgCalForA1c > 40.0) {
                val a1cRaw = (avgRawForA1c + 46.7) / 28.7
                val a1cCalibrated = (avgCalForA1c + 46.7) / 28.7
                DisplayMetric(
                    primary = String.format(Locale.US, "%.1f%%", a1cRaw),
                    secondary = String.format(Locale.US, "(%.1f%%)", a1cCalibrated)
                )
            } else {
                DisplayMetric("--", "")
            }
        } else {
            DisplayMetric("--", "")
        }

        return DashboardMetrics(
            estimatedA1c = estimatedA1c,
            todayAvg = buildMetricFromDays { it == todayDate },
            yesterdayAvg = buildMetricFromDays { it == yesterdayDate },
            weekAvg = buildMetricFromDays { it >= weekStartDate },
            monthAvg = buildMetricFromDays { it >= monthStartDate },
            quarterAvg = buildMetricFromDays { it >= a1cStartDate },
            breakfastMonthAvg = buildMetricFromDays { it >= monthStartDate },
            lunchMonthAvg = buildMetricFromDays { it >= monthStartDate },
            dinnerMonthAvg = buildMetricFromDays { it >= monthStartDate },
            breakfastHypos = CountMetric(breakfastHypoCal, breakfastHypoCal - breakfastHypoRaw),
            lunchHypos = CountMetric(lunchHypoCal, lunchHypoCal - lunchHypoRaw),
            dinnerHypos = CountMetric(dinnerHypoCal, dinnerHypoCal - dinnerHypoRaw)
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

    private fun parseMeasurementInstant(measurement: GlucoseMeasurement): Instant? {
        measurement.epochSeconds?.let { return Instant.ofEpochSecond(it) }
        return TimestampParser.parseFlexibleInstant(measurement.timestamp)
            ?: TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp)
    }
}

// Clase de datos ligera para acumular estadísticas por día sin crear listas intermedias
private data class DailyStats(
    var sumRaw: Double = 0.0,
    var sumCal: Double = 0.0,
    var count: Int = 0,
    var maxRaw: Double = 0.0,
    var minRaw: Double = Double.MAX_VALUE,
    var maxCal: Double = 0.0,
    var minCal: Double = Double.MAX_VALUE
)