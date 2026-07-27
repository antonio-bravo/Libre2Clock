package com.tonio.libre2clock.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.*
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

enum class ReportRange(val days: Int) {
    ONE_DAY(1),
    SEVEN_DAYS(7),
    FOURTEEN_DAYS(14),
    THIRTY_DAYS(30),
    NINETY_DAYS(90)
}

enum class ReportLayout {
    SNAPSHOT, // Summary + AGP
    DAILY_LOG, // Mini charts for each day
    FULL // Everything
}

data class ReportMetrics(
    val avgGlucose: Double,
    val gmi: Double,
    val cv: Double, // Coefficient of Variation
    val tir: Double,      // 70-180
    val tarHigh: Double,  // 181-250
    val tarVHigh: Double, // > 250
    val tbrLow: Double,   // 54-69
    val tbrVLow: Double,  // < 54
    val avgTdi: Double,
    val basalPercentage: Double,
    val bolusPercentage: Double,
    val readingsCount: Int
)

data class AgpPoint(
    val hour: Int,
    val median: Double,
    val p25: Double,
    val p75: Double,
    val p10: Double,
    val p90: Double
)

data class DailySummary(
    val date: LocalDate,
    val glucose: List<GlucoseMeasurement>,
    val insulin: Double,
    val carbs: Double,
    val basal: Double,
    val bolus: Double
)

class ReportViewModel(
    private val repository: GlucoseRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(ReportRange.SEVEN_DAYS)
    val selectedRange: StateFlow<ReportRange> = _selectedRange.asStateFlow()

    private val _useOffsetValues = MutableStateFlow(true)
    val useOffsetValues: StateFlow<Boolean> = _useOffsetValues.asStateFlow()

    val reportMetrics: StateFlow<ReportMetrics?> = combine(
        repository.historicalGlucose,
        preferenceManager.insulinDoses,
        preferenceManager.glucoseOffset,
        preferenceManager.glucoseOffsetRanges,
        preferenceManager.autoAdjustEnabled,
        preferenceManager.capillaryReadings,
        _selectedRange,
        _useOffsetValues
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        calculateMetrics(
            args[0] as List<GlucoseMeasurement>,
            args[1] as List<InsulinDose>,
            args[2] as Int,
            args[3] as List<GlucoseOffsetRange>,
            args[4] as Boolean,
            args[5] as List<CapillaryMeasurement>,
            args[6] as ReportRange,
            args[7] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val agpData: StateFlow<List<AgpPoint>> = combine(
        repository.historicalGlucose,
        preferenceManager.glucoseOffset,
        preferenceManager.glucoseOffsetRanges,
        preferenceManager.autoAdjustEnabled,
        preferenceManager.capillaryReadings,
        _selectedRange,
        _useOffsetValues
    ) { args ->
        calculateAgpData(
            args[0] as List<GlucoseMeasurement>,
            args[1] as Int,
            args[2] as List<GlucoseOffsetRange>,
            args[3] as Boolean,
            args[4] as List<CapillaryMeasurement>,
            args[5] as ReportRange,
            args[6] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailySummaries: StateFlow<List<DailySummary>> = combine(
        repository.historicalGlucose,
        preferenceManager.insulinDoses,
        preferenceManager.glucoseOffset,
        preferenceManager.glucoseOffsetRanges,
        preferenceManager.autoAdjustEnabled,
        preferenceManager.capillaryReadings,
        _selectedRange
    ) { args ->
        calculateDailySummaries(
            args[0] as List<GlucoseMeasurement>,
            args[1] as List<InsulinDose>,
            args[2] as Int,
            args[3] as List<GlucoseOffsetRange>,
            args[4] as Boolean,
            args[5] as List<CapillaryMeasurement>,
            args[6] as ReportRange
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRange(range: ReportRange) {
        _selectedRange.value = range
    }

    fun setUseOffsetValues(useOffset: Boolean) {
        _useOffsetValues.value = useOffset
    }

    private fun calculateMetrics(
        rawGlucose: List<GlucoseMeasurement>,
        doses: List<InsulinDose>,
        offset: Int,
        ranges: List<GlucoseOffsetRange>,
        auto: Boolean,
        caps: List<CapillaryMeasurement>,
        range: ReportRange,
        useOffset: Boolean
    ): ReportMetrics {
        val cutoff = Instant.now().minus(range.days.toLong(), ChronoUnit.DAYS)
        val glucose = rawGlucose.filter { m ->
            val instant = parseInstant(m)
            instant?.isAfter(cutoff) == true
        }.map { GlucoseProcessor.process(it, offset, ranges, auto, caps) }

        if (glucose.isEmpty()) return ReportMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)

        val values = if (useOffset) glucose.map { it.calibratedValue.toDouble() } else glucose.map { it.value.toDouble() }
        val avg = values.average()
        val stdDev = calculateStdDev(values, avg)
        val cv = if (avg > 0) (stdDev / avg) * 100 else 0.0
        val gmi = if (avg > 0) (avg + 46.7) / 28.7 else 0.0

        val readingsCount = values.size
        val tir = values.count { it in 70.0..180.0 }.toDouble() / readingsCount * 100
        val tarHigh = values.count { it in 181.0..250.0 }.toDouble() / readingsCount * 100
        val tarVHigh = values.count { it > 250.0 }.toDouble() / readingsCount * 100
        val tbrLow = values.count { it in 54.0..69.0 }.toDouble() / readingsCount * 100
        val tbrVLow = values.count { it < 54.0 }.toDouble() / readingsCount * 100

        // Insulin
        val filteredDoses = doses.filter { d ->
            val instant = TimestampParser.parseFlexibleInstant(d.timestamp)
            instant?.isAfter(cutoff) == true
        }
        val totalInsulin = filteredDoses.sumOf { it.units }
        val basal = filteredDoses.filter { it.type == InsulinType.SLOW }.sumOf { it.units }
        val bolus = filteredDoses.filter { it.type == InsulinType.RAPID }.sumOf { it.units }

        return ReportMetrics(
            avgGlucose = avg, gmi = gmi, cv = cv,
            tir = tir, tarHigh = tarHigh, tarVHigh = tarVHigh,
            tbrLow = tbrLow, tbrVLow = tbrVLow,
            avgTdi = totalInsulin / range.days,
            basalPercentage = if (totalInsulin > 0) (basal / totalInsulin) * 100 else 0.0,
            bolusPercentage = if (totalInsulin > 0) (bolus / totalInsulin) * 100 else 0.0,
            readingsCount = readingsCount
        )
    }

    private fun calculateAgpData(
        rawGlucose: List<GlucoseMeasurement>,
        offset: Int, ranges: List<GlucoseOffsetRange>, auto: Boolean, caps: List<CapillaryMeasurement>,
        range: ReportRange, useOffset: Boolean
    ): List<AgpPoint> {
        val cutoff = Instant.now().minus(range.days.toLong(), ChronoUnit.DAYS)
        val zone = ZoneId.systemDefault()
        
        val readingsByHour = rawGlucose.filter { parseInstant(it)?.isAfter(cutoff) == true }
            .map { m ->
                val instant = parseInstant(m)!!
                val processed = GlucoseProcessor.process(m, offset, ranges, auto, caps)
                val value = if (useOffset) processed.calibratedValue else processed.value
                instant.atZone(zone).hour to value.toDouble()
            }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.map { it.second }.sorted() }

        return (0..23).map { hr ->
            val values = readingsByHour[hr] ?: emptyList()
            if (values.isEmpty()) AgpPoint(hr, 0.0, 0.0, 0.0, 0.0, 0.0)
            else AgpPoint(
                hour = hr,
                median = getPercentile(values, 0.5),
                p25 = getPercentile(values, 0.25),
                p75 = getPercentile(values, 0.75),
                p10 = getPercentile(values, 0.10),
                p90 = getPercentile(values, 0.90)
            )
        }
    }

    private fun calculateDailySummaries(
        rawGlucose: List<GlucoseMeasurement>, doses: List<InsulinDose>,
        offset: Int, ranges: List<GlucoseOffsetRange>, auto: Boolean, caps: List<CapillaryMeasurement>,
        range: ReportRange
    ): List<DailySummary> {
        val cutoff = Instant.now().minus(range.days.toLong(), ChronoUnit.DAYS)
        val zone = ZoneId.systemDefault()
        
        val glucoseByDate = rawGlucose.filter { parseInstant(it)?.isAfter(cutoff) == true }
            .map { GlucoseProcessor.process(it, offset, ranges, auto, caps) }
            .groupBy { parseInstant(it)!!.atZone(zone).toLocalDate() }
            
        val dosesByDate = doses.filter { TimestampParser.parseFlexibleInstant(it.timestamp)?.isAfter(cutoff) == true }
            .groupBy { TimestampParser.parseFlexibleInstant(it.timestamp)!!.atZone(zone).toLocalDate() }

        val dates = glucoseByDate.keys.union(dosesByDate.keys).sortedDescending()
        
        return dates.map { date ->
            val g = glucoseByDate[date] ?: emptyList()
            val d = dosesByDate[date] ?: emptyList()
            DailySummary(
                date = date,
                glucose = g.sortedBy { parseInstant(it) },
                insulin = d.sumOf { it.units },
                carbs = d.sumOf { it.carbs ?: 0.0 },
                basal = d.filter { it.type == InsulinType.SLOW }.sumOf { it.units },
                bolus = d.filter { it.type == InsulinType.RAPID }.sumOf { it.units }
            )
        }
    }

    private fun parseInstant(m: GlucoseMeasurement) = TimestampParser.parseFlexibleInstant(m.factoryTimestamp) ?: TimestampParser.parseFlexibleInstant(m.timestamp)

    private fun calculateStdDev(values: List<Double>, avg: Double): Double {
        if (values.size < 2) return 0.0
        val sumSq = values.sumOf { (it - avg) * (it - avg) }
        return sqrt(sumSq / (values.size - 1))
    }

    private fun getPercentile(sortedValues: List<Double>, p: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = (p * (sortedValues.size - 1))
        val lower = index.toInt()
        val upper = lower + 1
        if (upper >= sortedValues.size) return sortedValues[lower]
        val weight = index - lower
        return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight
    }
}
