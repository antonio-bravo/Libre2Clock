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

enum class MealSlot { BREAKFAST, LUNCH, DINNER }

object DashboardMetricsCalculator {

    // Delegamos a la función principal optimizada. 
    // Al ser O(N) de paso único, es tan rápida que no vale la pena mantener lógica duplicada.
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

        // Único mapa para agrupar estadísticas por día (evita múltiples groupBy)
        val allDailyStats = mutableMapOf<LocalDate, DailyStats>()
        
        // Contadores específicos para hipos por comida (solo mes)
        var breakfastHypoCal = 0; var breakfastHypoRaw = 0
        var lunchHypoCal = 0; var lunchHypoRaw = 0
        var dinnerHypoCal = 0; var dinnerHypoRaw = 0

        // PASO ÚNICO (O(N)): Procesamos cada medición una sola vez
        for (m in measurements) {
            if (m.value <= 40) continue // Filtrar fallos de sensor
            
            val instant = parseMeasurementInstant(m) ?: continue
            val date = instant.atZone(zone).toLocalDate()
            
            val stats = allDailyStats.getOrPut(date) { DailyStats() }
            val raw = m.value.toDouble()
            val cal = m.calibratedValue.toDouble()
            
            // Acumular estadísticas diarias
            stats.sumRaw += raw
            stats.sumCal += cal
            stats.count++
            if (raw > stats.maxRaw) stats.maxRaw = raw
            if (raw < stats.minRaw) stats.minRaw = raw
            if (cal > stats.maxCal) stats.maxCal = cal
            if (cal < stats.minCal) stats.minCal = cal

            // Contar hipos por comida (solo si está dentro de la ventana mensual)
            if (!instant.isBefore(startOfMonth)) {
                val hour = instant.atZone(zone).toLocalTime().hour
                val isHypoCal = cal < 70.0
                val isHypoRaw = raw < 70.0
                
                when {
                    hour in 5..11 -> {
                        if (isHypoCal) breakfastHypoCal++
                        if (isHypoRaw) breakfastHypoRaw++
                    }
                    hour in 12..16 -> {
                        if (isHypoCal) lunchHypoCal++
                        if (isHypoRaw) lunchHypoRaw++
                    }
                    hour >= 17 -> {
                        if (isHypoCal) dinnerHypoCal++
                        if (isHypoRaw) dinnerHypoRaw++
                    }
                }
            }
        }

        // Función helper para calcular métricas a partir de un subconjunto de días
        fun buildMetricFromDays(datePredicate: (LocalDate) -> Boolean): DisplayMetric {
            val matchingDays = allDailyStats.filterKeys { datePredicate(it) }.values
            if (matchingDays.isEmpty()) return DisplayMetric("--", "")

            // Promedio de los promedios diarios (evita sesgo por días con más mediciones)
            val avgOfDailyAvgsRaw = matchingDays.map { it.sumRaw / it.count }.average()
            val avgOfDailyAvgsCal = matchingDays.map { it.sumCal / it.count }.average()
            
            // Oscilación basada en los extremos globales de los días coincidentes
            val maxRaw = matchingDays.maxOfOrNull { it.maxRaw } ?: 0.0
            val minRaw = matchingDays.minOfOrNull { it.minRaw } ?: 0.0
            val maxCal = matchingDays.maxOfOrNull { it.maxCal } ?: 0.0
            val minCal = matchingDays.minOfOrNull { it.minCal } ?: 0.0
            
            val avgRawInt = avgOfDailyAvgsRaw.roundToInt()
            val avgCalInt = avgOfDailyAvgsCal.roundToInt()
            
            val oscRaw = max(maxRaw.roundToInt() - avgRawInt, avgRawInt - minRaw.roundToInt()).coerceAtLeast(0)
            val oscCal = max(maxCal.roundToInt() - avgCalInt, avgCalInt - minCal.roundToInt()).coerceAtLeast(0)

            return DisplayMetric("$avgRawInt ± $oscRaw ($avgCalInt ± $oscCal)", "")
        }

        // 1. Cálculo de A1c (requiere lógica específica)
        val a1cDays = allDailyStats.filterKeys { it >= a1cStartDate }.values
        val avgRawForA1c = if (a1cDays.isNotEmpty()) a1cDays.map { it.sumRaw / it.count }.average() else 0.0
        val avgCalForA1c = if (a1cDays.isNotEmpty()) a1cDays.map { it.sumCal / it.count }.average() else 0.0
        
        val estimatedA1c = if (avgCalForA1c > 40.0 && a1cDays.sumOf { it.count } > 100) { 
            val a1cRaw = (avgRawForA1c + 46.7) / 28.7
            val a1cCalibrated = (avgCalForA1c + 46.7) / 28.7
            DisplayMetric(
                primary = String.format(Locale.US, "%.1f%% (%.1f%%)", a1cRaw, a1cCalibrated),
                secondary = ""
            )
        } else {
            DisplayMetric("--", "")
        }

        // 2. Construcción del objeto final usando el helper optimizado
        return DashboardMetrics(
            estimatedA1c = estimatedA1c,
            todayAvg = buildMetricFromDays { it == todayDate },
            yesterdayAvg = buildMetricFromDays { it == yesterdayDate },
            weekAvg = buildMetricFromDays { it >= weekStartDate },
            monthAvg = buildMetricFromDays { it >= monthStartDate },
            quarterAvg = buildMetricFromDays { it >= a1cStartDate },
            breakfastMonthAvg = buildMetricFromDays { it >= monthStartDate }, // Mismo filtro, los datos ya están en el mapa
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