package com.tonio.libre2clock.ui.dashboard

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max // <-- CORRECCIÓN: Usamos max en lugar de maxOf
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    val nightMonthAvg: DisplayMetric,
    val breakfastHypos: CountMetric,
    val lunchHypos: CountMetric,
    val dinnerHypos: CountMetric,
    val nightHypos: CountMetric,
    val cv7d: DisplayMetric = DisplayMetric("--", ""),
    val cv14d: DisplayMetric = DisplayMetric("--", ""),
    val cv30d: DisplayMetric = DisplayMetric("--", ""),
    val cv90d: DisplayMetric = DisplayMetric("--", ""),
    val tirTbr7d: DisplayMetric = DisplayMetric("--", ""),
    val tirTbr14d: DisplayMetric = DisplayMetric("--", ""),
    val tirTbr30d: DisplayMetric = DisplayMetric("--", ""),
    val tirTbr90d: DisplayMetric = DisplayMetric("--", "")
)

object DashboardMetricsCalculator {

    fun calculateLive(measurements: List<GlucoseMeasurement>): DashboardMetrics = calculate(measurements)

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
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)

        val weekStart = today.minusDays(6)
        val monthStart = today.minusDays(29)
        val quarterStart = today.minusDays(89)

        val allDailyStats = mutableMapOf<LocalDate, DailyStats>()
        val breakfastDailyStats = mutableMapOf<LocalDate, DailyStats>()
        val lunchDailyStats = mutableMapOf<LocalDate, DailyStats>()
        val dinnerDailyStats = mutableMapOf<LocalDate, DailyStats>()
        val nightDailyStats = mutableMapOf<LocalDate, DailyStats>()

        var breakfastHypoCal = 0; var breakfastHypoRaw = 0
        var lunchHypoCal = 0; var lunchHypoRaw = 0
        var dinnerHypoCal = 0; var dinnerHypoRaw = 0
        var nightHypoCal = 0; var nightHypoRaw = 0

        // PASO ÚNICO (O(N)): Procesamos cada medición una sola vez
        for (m in measurements) {
            val rawVal = m.value
            if (rawVal <= 40) continue

            val instant = parseMeasurementInstant(m) ?: continue

            val zdt = instant.atZone(zone)
            val date = zdt.toLocalDate()

            val raw = rawVal.toDouble()
            val cal = m.calibratedValue.toDouble()

            // Actualizar estadísticas globales del día
            val allStats = allDailyStats.getOrPut(date) { DailyStats() }
            allStats.add(raw, cal)

            // Actualizar estadísticas de comidas y hipos (solo si es dentro del último mes)
            if (date >= monthStart) {
                val hour = zdt.hour
                val isHypoCal = cal < 70.0
                val isHypoRaw = raw < 70.0

                // Los 4 intervalos cubren las 24 horas completas
                val mealStatsMap = when (hour) {
                    in 5..11 -> {
                        if (isHypoCal) breakfastHypoCal++
                        if (isHypoRaw) breakfastHypoRaw++
                        breakfastDailyStats
                    }
                    in 12..16 -> {
                        if (isHypoCal) lunchHypoCal++
                        if (isHypoRaw) lunchHypoRaw++
                        lunchDailyStats
                    }
                    in 17..23 -> {
                        if (isHypoCal) dinnerHypoCal++
                        if (isHypoRaw) dinnerHypoRaw++
                        dinnerDailyStats
                    }
                    in 0..4 -> {
                        if (isHypoCal) nightHypoCal++
                        if (isHypoRaw) nightHypoRaw++
                        nightDailyStats
                    }
                    else -> null // Nunca se alcanza, pero lo dejamos por seguridad
                }

                mealStatsMap?.getOrPut(date) { DailyStats() }?.add(raw, cal)
            }
        }

        // =========================================================================
        // FUNCIÓN HELPER: Promedio de medias diarias (evita sesgo de escaneo)
        // =========================================================================
        fun buildMetricFromDays(
            targetStatsMap: Map<LocalDate, DailyStats> = allDailyStats,
            datePredicate: (LocalDate) -> Boolean
        ): DisplayMetric {
            var sumOfDailyAvgsRaw = 0.0
            var sumOfDailyAvgsCal = 0.0
            var validDaysCount = 0

            // Para la oscilación (±), sí queremos los extremos absolutos del periodo
            var globalMaxRaw = 0.0
            var globalMinRaw = Double.MAX_VALUE
            var globalMaxCal = 0.0
            var globalMinCal = Double.MAX_VALUE

            for ((date, stats) in targetStatsMap) {
                if (datePredicate(date) && stats.count > 0) {
                    validDaysCount++
                    // AQUÍ ESTÁ LA CLAVE: Sumamos la media del día, no la suma cruda
                    sumOfDailyAvgsRaw += (stats.sumRaw / stats.count)
                    sumOfDailyAvgsCal += (stats.sumCal / stats.count)

                    if (stats.maxRaw > globalMaxRaw) globalMaxRaw = stats.maxRaw
                    if (stats.minRaw < globalMinRaw) globalMinRaw = stats.minRaw
                    if (stats.maxCal > globalMaxCal) globalMaxCal = stats.maxCal
                    if (stats.minCal < globalMinCal) globalMinCal = stats.minCal
                }
            }

            if (validDaysCount == 0) return DisplayMetric("--", "")

            val avgRaw = (sumOfDailyAvgsRaw / validDaysCount).roundToInt()
            val avgCal = (sumOfDailyAvgsCal / validDaysCount).roundToInt()

            // CORRECCIÓN: Usamos max() en lugar de maxOf()
            val oscRaw = max(globalMaxRaw.roundToInt() - avgRaw, avgRaw - globalMinRaw.roundToInt()).coerceAtLeast(0)
            val oscCal = max(globalMaxCal.roundToInt() - avgCal, avgCal - globalMinCal.roundToInt()).coerceAtLeast(0)

            return DisplayMetric(
                primary = "$avgRaw ± $oscRaw",
                secondary = "($avgCal ± $oscCal)"
            )
        }

        // =========================================================================
        // CÁLCULO DE HbA1c ESTIMADA (eA1c / GMI)
        // =========================================================================
        var a1cSumAvgRaw = 0.0
        var a1cSumAvgCal = 0.0
        var a1cDaysCount = 0
        var a1cTotalMeasurements = 0

        for ((date, stats) in allDailyStats) {
            if (date >= quarterStart) {
                if (stats.count > 0) {
                    a1cDaysCount++
                    a1cSumAvgRaw += (stats.sumRaw / stats.count)
                    a1cSumAvgCal += (stats.sumCal / stats.count)
                }
                a1cTotalMeasurements += stats.count
            }
        }

        val estimatedA1c = if (a1cDaysCount > 0 && a1cTotalMeasurements >= 10) {
            val avgRawForA1c = a1cSumAvgRaw / a1cDaysCount
            val avgCalForA1c = a1cSumAvgCal / a1cDaysCount

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

        val twoWeeksStart = today.minusDays(13)

        fun buildCvFromMeasurements(datePredicate: (LocalDate) -> Boolean): DisplayMetric {
            var sumRaw = 0.0
            var sumSqRaw = 0.0
            var countRaw = 0

            var sumCal = 0.0
            var sumSqCal = 0.0
            var countCal = 0

            for (m in measurements) {
                val rawVal = m.value
                if (rawVal <= 40) continue
                val instant = parseMeasurementInstant(m) ?: continue
                val date = instant.atZone(zone).toLocalDate()

                if (datePredicate(date)) {
                    val raw = rawVal.toDouble()
                    val cal = m.calibratedValue.toDouble()

                    sumRaw += raw
                    sumSqRaw += raw * raw
                    countRaw++

                    sumCal += cal
                    sumSqCal += cal * cal
                    countCal++
                }
            }

            if (countRaw < 5 || countCal < 5) return DisplayMetric("--", "")

            val avgRaw = sumRaw / countRaw
            val varRaw = (sumSqRaw / countRaw) - (avgRaw * avgRaw)
            val stdDevRaw = if (varRaw > 0) sqrt(varRaw) else 0.0
            val cvRaw = if (avgRaw > 0) (stdDevRaw / avgRaw) * 100.0 else 0.0

            val avgCal = sumCal / countCal
            val varCal = (sumSqCal / countCal) - (avgCal * avgCal)
            val stdDevCal = if (varCal > 0) sqrt(varCal) else 0.0
            val cvCal = if (avgCal > 0) (stdDevCal / avgCal) * 100.0 else 0.0

            return DisplayMetric(
                primary = String.format(Locale.US, "%.1f%%", cvRaw),
                secondary = String.format(Locale.US, "(%.1f%%)", cvCal)
            )
        }

        fun buildTirTbrFromMeasurements(datePredicate: (LocalDate) -> Boolean): DisplayMetric {
            var count = 0
            var tirRawCount = 0
            var tbrRawCount = 0
            var tirCalCount = 0
            var tbrCalCount = 0

            for (m in measurements) {
                val rawVal = m.value
                if (rawVal <= 40) continue
                val instant = parseMeasurementInstant(m) ?: continue
                val date = instant.atZone(zone).toLocalDate()

                if (datePredicate(date)) {
                    count++
                    val calVal = m.calibratedValue

                    if (rawVal in 70..180) tirRawCount++
                    if (rawVal < 70) tbrRawCount++

                    if (calVal in 70..180) tirCalCount++
                    if (calVal < 70) tbrCalCount++
                }
            }

            if (count < 5) return DisplayMetric("--", "")

            val tirRawPct = (tirRawCount.toDouble() / count) * 100.0
            val tbrRawPct = (tbrRawCount.toDouble() / count) * 100.0
            val tirCalPct = (tirCalCount.toDouble() / count) * 100.0
            val tbrCalPct = (tbrCalCount.toDouble() / count) * 100.0

            return DisplayMetric(
                primary = String.format(Locale.US, "%.0f%% (%.0f%%)", tirRawPct, tirCalPct),
                secondary = String.format(Locale.US, "%.0f%% (%.0f%%)", tbrRawPct, tbrCalPct)
            )
        }

        return DashboardMetrics(
            estimatedA1c = estimatedA1c,
            todayAvg = buildMetricFromDays { it == today },
            yesterdayAvg = buildMetricFromDays { it == yesterday },
            weekAvg = buildMetricFromDays { it >= weekStart },
            monthAvg = buildMetricFromDays { it >= monthStart },
            quarterAvg = buildMetricFromDays { it >= quarterStart },
            breakfastMonthAvg = buildMetricFromDays(breakfastDailyStats) { it >= monthStart },
            lunchMonthAvg = buildMetricFromDays(lunchDailyStats) { it >= monthStart },
            dinnerMonthAvg = buildMetricFromDays(dinnerDailyStats) { it >= monthStart },
            nightMonthAvg = buildMetricFromDays(nightDailyStats) { it >= monthStart },
            breakfastHypos = CountMetric(breakfastHypoCal, breakfastHypoCal - breakfastHypoRaw),
            lunchHypos = CountMetric(lunchHypoCal, lunchHypoCal - lunchHypoRaw),
            dinnerHypos = CountMetric(dinnerHypoCal, dinnerHypoCal - dinnerHypoRaw),
            nightHypos = CountMetric(nightHypoCal, nightHypoCal - nightHypoRaw),
            cv7d = buildCvFromMeasurements { it >= weekStart },
            cv14d = buildCvFromMeasurements { it >= twoWeeksStart },
            cv30d = buildCvFromMeasurements { it >= monthStart },
            cv90d = buildCvFromMeasurements { it >= quarterStart },
            tirTbr7d = buildTirTbrFromMeasurements { it >= weekStart },
            tirTbr14d = buildTirTbrFromMeasurements { it >= twoWeeksStart },
            tirTbr30d = buildTirTbrFromMeasurements { it >= monthStart },
            tirTbr90d = buildTirTbrFromMeasurements { it >= quarterStart }
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
        nightMonthAvg = DisplayMetric("--", ""),
        breakfastHypos = CountMetric(0, 0),
        lunchHypos = CountMetric(0, 0),
        dinnerHypos = CountMetric(0, 0),
        nightHypos = CountMetric(0, 0),
        cv7d = DisplayMetric("--", ""),
        cv14d = DisplayMetric("--", ""),
        cv30d = DisplayMetric("--", ""),
        cv90d = DisplayMetric("--", ""),
        tirTbr7d = DisplayMetric("--", ""),
        tirTbr14d = DisplayMetric("--", ""),
        tirTbr30d = DisplayMetric("--", ""),
        tirTbr90d = DisplayMetric("--", "")
    )

    private fun parseMeasurementInstant(measurement: GlucoseMeasurement): Instant? {
        return TimestampParser.parseMeasurementInstant(measurement)
    }
}

private class DailyStats {
    var sumRaw = 0.0
    var sumCal = 0.0
    var count = 0
    var maxRaw = 0.0
    var minRaw = Double.MAX_VALUE
    var maxCal = 0.0
    var minCal = Double.MAX_VALUE

    fun add(raw: Double, cal: Double) {
        sumRaw += raw
        sumCal += cal
        count++

        if (raw > maxRaw) maxRaw = raw
        else if (raw < minRaw) minRaw = raw

        if (cal > maxCal) maxCal = cal
        else if (cal < minCal) minCal = cal
    }
}