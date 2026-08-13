package com.tonio.libre2clock.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.*
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

enum class ReportRange(val days: Int) {
    ONE_DAY(1),
    SEVEN_DAYS(7),
    FIFTEEN_DAYS(15),
    THIRTY_DAYS(30),
    NINETY_DAYS(90)
}

enum class ReportLayout {
    SNAPSHOT, // Summary + AGP
    DAILY_LOG, // Mini charts for each day
    FULL // Everything
}

@Serializable
data class ReportMetrics(
    val avgGlucose: Double,
    val gmi: Double,
    val cv: Double,
    val tir: Double,
    val tarHigh: Double,
    val tarVHigh: Double,
    val tbrLow: Double,
    val tbrVLow: Double,
    val avgTdi: Double,
    val basalPercentage: Double,
    val bolusPercentage: Double,
    val readingsCount: Int
)

@Serializable
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
    private val preferenceManager: PreferenceManager,
    androidContext: android.content.Context
) : ViewModel() {

    private val reportCache = ReportSectionCacheRepository(androidContext)

    private val _startDate = MutableStateFlow(LocalDate.now().minusDays(30))
    val startDate: StateFlow<LocalDate> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow(LocalDate.now())
    val endDate: StateFlow<LocalDate> = _endDate.asStateFlow()

    private val _useOffsetValues = MutableStateFlow(true)
    val useOffsetValues: StateFlow<Boolean> = _useOffsetValues.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // 1. Optimized Base Window: Only filter the archive once when range changes
    private val windowedData: Flow<Pair<List<GlucoseMeasurement>, List<InsulinDose>>> = combine(
        repository.historicalGlucose,
        preferenceManager.insulinDoses,
        _startDate,
        _endDate
    ) { glucose, doses, start, end ->
        val zone = ZoneId.systemDefault()
        val startInstant = start.atStartOfDay(zone).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(zone).toInstant()
        
        val filteredG = glucose.filter { 
            val instant = parseInstant(it)
            instant != null && !instant.isBefore(startInstant) && !instant.isAfter(endInstant)
        }
        val filteredD = doses.filter { 
            val instant = TimestampParser.parseFlexibleInstant(it.timestamp)
            instant != null && !instant.isBefore(startInstant) && !instant.isAfter(endInstant)
        }
        filteredG to filteredD
    }.flowOn(Dispatchers.Default).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val reportMetrics: StateFlow<ReportMetrics?> = combine(
        combine(
            windowedData,
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.capillaryReadings
        ) { data, offset, ranges, auto, caps ->
            ReportMetricsBaseInput(data.first, data.second, offset, ranges, auto, caps)
        },
        combine(
            preferenceManager.autoRangeOffsetMode,
            preferenceManager.historyRetentionDays,
            _startDate,
            _endDate,
            _useOffsetValues
        ) { mode, retention, start, end, useOffset ->
            ReportMetricsParams(mode, retention, start, end, useOffset)
        }
    ) { base, params ->
        val signature = ReportSectionCacheRepository.buildSignature(
            glucose = base.glucose,
            doses = base.doses,
            offset = base.offset,
            ranges = base.ranges,
            autoAdjustEnabled = base.autoAdjust,
            capillaries = base.capillaries,
            autoRangeMode = params.autoRangeMode.name,
            extraTag = "metrics:${params.start}:${params.end}:${params.useOffset}"
        )
        reportCache.getOrComputeReportMetrics(
            signature = signature,
            retentionDays = params.retentionDays
        ) {
            calculateMetrics(
                windowedGlucose = base.glucose,
                windowedDoses = base.doses,
                offset = base.offset,
                ranges = base.ranges,
                auto = base.autoAdjust,
                caps = base.capillaries,
                autoRangeMode = params.autoRangeMode,
                daysCount = (ChronoUnit.DAYS.between(params.start, params.end) + 1).toInt(),
                useOffset = params.useOffset
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val agpData: StateFlow<List<AgpPoint>> = combine(
        combine(
            windowedData,
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.capillaryReadings
        ) { data, offset, ranges, auto, caps ->
            ReportMetricsBaseInput(data.first, data.second, offset, ranges, auto, caps)
        },
        preferenceManager.autoRangeOffsetMode,
        preferenceManager.historyRetentionDays,
        _useOffsetValues
    ) { base, autoRangeMode, retentionDays, useOffset ->
        val signature = ReportSectionCacheRepository.buildSignature(
            glucose = base.glucose,
            doses = base.doses,
            offset = base.offset,
            ranges = base.ranges,
            autoAdjustEnabled = base.autoAdjust,
            capillaries = base.capillaries,
            autoRangeMode = autoRangeMode.name,
            extraTag = "agp:${useOffset}"
        )
        reportCache.getOrComputeAgp(
            signature = signature,
            retentionDays = retentionDays
        ) {
            calculateAgpData(
                windowedGlucose = base.glucose,
                offset = base.offset,
                ranges = base.ranges,
                auto = base.autoAdjust,
                caps = base.capillaries,
                autoRangeMode = autoRangeMode,
                useOffset = useOffset
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailySummaries: StateFlow<List<DailySummary>> = combine(
        combine(
            windowedData,
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.capillaryReadings
        ) { data, offset, ranges, auto, caps ->
            ReportMetricsBaseInput(data.first, data.second, offset, ranges, auto, caps)
        },
        preferenceManager.autoRangeOffsetMode,
        preferenceManager.historyRetentionDays
    ) { base, autoRangeMode, retentionDays ->
        val signature = ReportSectionCacheRepository.buildSignature(
            glucose = base.glucose,
            doses = base.doses,
            offset = base.offset,
            ranges = base.ranges,
            autoAdjustEnabled = base.autoAdjust,
            capillaries = base.capillaries,
            autoRangeMode = autoRangeMode.name,
            extraTag = "daily"
        )
        reportCache.getOrComputeDailySummaries(
            signature = signature,
            retentionDays = retentionDays
        ) {
            calculateDailySummaries(
                windowedGlucose = base.glucose,
                windowedDoses = base.doses,
                offset = base.offset,
                ranges = base.ranges,
                auto = base.autoAdjust,
                caps = base.capillaries,
                autoRangeMode = autoRangeMode
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRange(range: ReportRange) {
        _endDate.value = LocalDate.now()
        _startDate.value = LocalDate.now().minusDays(range.days.toLong())
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        _startDate.value = start
        _endDate.value = end
    }

    fun setUseOffsetValues(useOffset: Boolean) {
        _useOffsetValues.value = useOffset
    }

    fun setGenerating(generating: Boolean) {
        _isGenerating.value = generating
    }

    private fun calculateMetrics(
        windowedGlucose: List<GlucoseMeasurement>,
        windowedDoses: List<InsulinDose>,
        offset: Int,
        ranges: List<GlucoseOffsetRange>,
        auto: Boolean,
        caps: List<CapillaryMeasurement>,
        autoRangeMode: AutoRangeOffsetMode,
        daysCount: Int,
        useOffset: Boolean
    ): ReportMetrics {
        if (windowedGlucose.isEmpty()) return ReportMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)

        val processed = windowedGlucose.map {
            GlucoseProcessor.process(
                measurement = it,
                manualOffset = offset,
                userRanges = ranges,
                autoAdjustEnabled = auto,
                autoRangeOffsetMode = autoRangeMode,
                capillaryReadings = caps
            )
        }
        val values = if (useOffset) processed.map { it.calibratedValue.toDouble() } else processed.map { it.value.toDouble() }
        
        val avg = values.average()
        val stdDev = calculateStdDev(values, avg)
        val cv = if (avg > 0) (stdDev / avg) * 100 else 0.0
        val gmi = if (avg > 0) (avg + 46.7) / 28.7 else 0.0

        val count = values.size.toDouble()
        val tir = values.count { it in 70.0..180.0 } / count * 100
        val tarHigh = values.count { it in 181.0..250.0 } / count * 100
        val tarVHigh = values.count { it > 250.0 } / count * 100
        val tbrLow = values.count { it in 54.0..69.0 } / count * 100
        val tbrVLow = values.count { it < 54.0 } / count * 100

        val totalInsulin = windowedDoses.sumOf { it.units }
        val basal = windowedDoses.filter { it.type == InsulinType.SLOW }.sumOf { it.units }
        val bolus = windowedDoses.filter { it.type == InsulinType.RAPID }.sumOf { it.units }

        return ReportMetrics(
            avgGlucose = avg, gmi = gmi, cv = cv,
            tir = tir, tarHigh = tarHigh, tarVHigh = tarVHigh,
            tbrLow = tbrLow, tbrVLow = tbrVLow,
            avgTdi = totalInsulin / daysCount.coerceAtLeast(1),
            basalPercentage = if (totalInsulin > 0) (basal / totalInsulin) * 100 else 0.0,
            bolusPercentage = if (totalInsulin > 0) (bolus / totalInsulin) * 100 else 0.0,
            readingsCount = values.size
        )
    }

    private fun calculateAgpData(
        windowedGlucose: List<GlucoseMeasurement>,
        offset: Int, ranges: List<GlucoseOffsetRange>, auto: Boolean, caps: List<CapillaryMeasurement>,
        autoRangeMode: AutoRangeOffsetMode,
        useOffset: Boolean
    ): List<AgpPoint> {
        val zone = ZoneId.systemDefault()
        
        val readingsByHour = windowedGlucose
            .map { m ->
                val instant = parseInstant(m)!!
                val processed = GlucoseProcessor.process(
                    measurement = m,
                    manualOffset = offset,
                    userRanges = ranges,
                    autoAdjustEnabled = auto,
                    autoRangeOffsetMode = autoRangeMode,
                    capillaryReadings = caps
                )
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
        windowedGlucose: List<GlucoseMeasurement>, windowedDoses: List<InsulinDose>,
        offset: Int,
        ranges: List<GlucoseOffsetRange>,
        auto: Boolean,
        caps: List<CapillaryMeasurement>,
        autoRangeMode: AutoRangeOffsetMode
    ): List<DailySummary> {
        val zone = ZoneId.systemDefault()
        
        val glucoseByDate = windowedGlucose
            .map {
                GlucoseProcessor.process(
                    measurement = it,
                    manualOffset = offset,
                    userRanges = ranges,
                    autoAdjustEnabled = auto,
                    autoRangeOffsetMode = autoRangeMode,
                    capillaryReadings = caps
                )
            }
            .groupBy { parseInstant(it)!!.atZone(zone).toLocalDate() }
            
        val dosesByDate = windowedDoses
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

    private data class ReportMetricsBaseInput(
        val glucose: List<GlucoseMeasurement>,
        val doses: List<InsulinDose>,
        val offset: Int,
        val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val capillaries: List<CapillaryMeasurement>
    )

    private data class ReportMetricsParams(
        val autoRangeMode: AutoRangeOffsetMode,
        val retentionDays: Int,
        val start: LocalDate,
        val end: LocalDate,
        val useOffset: Boolean
    )
}
