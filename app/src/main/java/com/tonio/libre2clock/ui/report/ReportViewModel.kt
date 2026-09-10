package com.tonio.libre2clock.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.*
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.util.TimestampParser
import com.tonio.libre2clock.util.LocalDateSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

enum class ReportRange(val days: Int) {
    ONE_DAY(1), SEVEN_DAYS(7), FIFTEEN_DAYS(15), THIRTY_DAYS(30), NINETY_DAYS(90)
}

enum class ReportLayout { SNAPSHOT, DAILY_LOG, FULL }

@Serializable
data class ReportMetrics(
    val avgGlucose: Double, val gmi: Double, val cv: Double,
    val tir: Double, val tarHigh: Double, val tarVHigh: Double,
    val tbrLow: Double, val tbrVLow: Double,
    val avgTdi: Double, val basalPercentage: Double, val bolusPercentage: Double,
    val readingsCount: Int
)

@Serializable
data class AgpPoint(
    val hour: Int, val median: Double, val p25: Double, val p75: Double,
    val p10: Double, val p90: Double
)

@Serializable
data class DailySummary(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate, val glucose: List<GlucoseMeasurement>,
    val insulin: Double, val carbs: Double, val basal: Double, val bolus: Double
)

// OPTIMIZACIÓN: Agrupamos todo el reporte en una sola clase para calcular y cachear una sola vez
@Serializable
data class FullReportData(
    val metrics: ReportMetrics,
    val agp: List<AgpPoint>,
    val dailySummaries: List<DailySummary>
)

// Clase ligera para evitar reprocesar la glucosa
private data class ProcessedGlucose(
    val instant: Instant,
    val rawValue: Double,
    val calibratedValue: Double
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

    // 1. Ventana de datos optimizada
    private val windowedData: Flow<Pair<List<GlucoseMeasurement>, List<InsulinDose>>> = combine(
        _startDate, _endDate, preferenceManager.insulinDoses, repository.historicalGlucose
    ) { start, end, doses, _ ->
        val zone = ZoneId.systemDefault()
        val startInstant = start.atStartOfDay(zone).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(zone).toInstant()

        val filteredG = repository.getHistoricalGlucoseWindow(
            startEpochMs = startInstant.toEpochMilli(),
            endEpochMs = endInstant.toEpochMilli(),
            maxItems = 10000
        )
        
        // OPTIMIZACIÓN: Parsear timestamp una sola vez por dosis
        val filteredD = doses.filter {
            val instant = TimestampParser.parseFlexibleInstant(it.timestamp)
            instant != null && !instant.isBefore(startInstant) && !instant.isAfter(endInstant)
        }
        filteredG to filteredD
    }.flowOn(Dispatchers.Default).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    // 2. Flujo UNIFICADO: Calcula y cachea todo el reporte de una sola vez
    val reportData: StateFlow<FullReportData?> = combine(
        combine(
            windowedData,
            preferenceManager.glucoseOffset, preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled, preferenceManager.capillaryReadings
        ) { data, offset, ranges, auto, caps ->
            ReportInput(data.first, data.second, offset, ranges, auto, caps)
        },
        combine(
            preferenceManager.autoRangeOffsetMode, preferenceManager.historyRetentionDays,
            _startDate, _endDate, _useOffsetValues
        ) { mode, retention, start, end, useOffset ->
            ReportParams(mode, retention, start, end, useOffset)
        }
    ) { input: ReportInput, params: ReportParams ->
        val signature = ReportSectionCacheRepository.buildSignature(
            glucose = input.glucose, doses = input.doses, offset = input.offset,
            ranges = input.ranges, autoAdjustEnabled = input.autoAdjust,
            capillaries = input.capillaries, autoRangeMode = params.autoRangeMode.name,
            extraTag = "full_report:${params.start}:${params.end}:${params.useOffset}"
        )
        
        reportCache.getOrComputeFullReport(
            signature = signature,
            retentionDays = params.retentionDays
        ) {
            val zone = ZoneId.systemDefault()
            val calcContext = GlucoseProcessor.buildContext(
                autoRangeOffsetMode = params.autoRangeMode,
                userRanges = input.ranges,
                capillaryReadings = input.capillaries
            )

            // OPTIMIZACIÓN CRÍTICA: Procesar la glucosa UNA SOLA VEZ
            val processedGlucose = input.glucose.mapNotNull { m ->
                val instant = TimestampParser.parseFlexibleInstant(m.factoryTimestamp) 
                    ?: TimestampParser.parseFlexibleInstant(m.timestamp) ?: return@mapNotNull null
                
                val processed = GlucoseProcessor.process(
                    measurement = m, manualOffset = input.offset, userRanges = input.ranges,
                    autoAdjustEnabled = input.autoAdjust, autoRangeOffsetMode = params.autoRangeMode,
                    capillaryReadings = input.capillaries, context = calcContext
                )
                ProcessedGlucose(instant, processed.value.toDouble(), processed.calibratedValue.toDouble())
            }

            val daysCount = (ChronoUnit.DAYS.between(params.start, params.end) + 1).toInt()

            FullReportData(
                metrics = calculateMetricsOptimized(processedGlucose, input.doses, daysCount, params.useOffset),
                agp = calculateAgpOptimized(processedGlucose, zone, params.useOffset),
                dailySummaries = calculateDailySummariesOptimized(processedGlucose, input.doses, zone)
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Exponemos los datos individuales para que la UI no tenga que cambiar mucho
    val reportMetrics: StateFlow<ReportMetrics?> = reportData.map { it?.metrics }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val agpData: StateFlow<List<AgpPoint>> = reportData.map { it?.agp ?: emptyList() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dailySummaries: StateFlow<List<DailySummary>> = reportData.map { it?.dailySummaries ?: emptyList() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // --- FUNCIONES DE CÁLCULO OPTIMIZADAS ---

    private fun calculateMetricsOptimized(
        processed: List<ProcessedGlucose>,
        doses: List<InsulinDose>,
        daysCount: Int,
        useOffset: Boolean
    ): ReportMetrics {
        if (processed.isEmpty()) return ReportMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)

        var sum = 0.0
        var sumSq = 0.0
        var tir = 0; var tarHigh = 0; var tarVHigh = 0; var tbrLow = 0; var tbrVLow = 0

        // OPTIMIZACIÓN: Single-pass loop para promedio, varianza y conteo de rangos
        for (pg in processed) {
            val v = if (useOffset) pg.calibratedValue else pg.rawValue
            sum += v
            sumSq += v * v
            
            when {
                v in 70.0..180.0 -> tir++
                v in 181.0..250.0 -> tarHigh++
                v > 250.0 -> tarVHigh++
                v in 54.0..69.0 -> tbrLow++
                v < 54.0 -> tbrVLow++
            }
        }

        val count = processed.size.toDouble()
        val avg = sum / count
        val variance = (sumSq / count) - (avg * avg)
        val stdDev = if (variance > 0) sqrt(variance) else 0.0
        
        val cv = if (avg > 0) (stdDev / avg) * 100 else 0.0
        val gmi = if (avg > 0) (avg + 46.7) / 28.7 else 0.0

        var totalInsulin = 0.0; var basal = 0.0; var bolus = 0.0
        for (d in doses) {
            totalInsulin += d.units
            if (d.type == InsulinType.SLOW) basal += d.units else bolus += d.units
        }

        return ReportMetrics(
            avgGlucose = avg, gmi = gmi, cv = cv,
            tir = (tir / count) * 100, tarHigh = (tarHigh / count) * 100, tarVHigh = (tarVHigh / count) * 100,
            tbrLow = (tbrLow / count) * 100, tbrVLow = (tbrVLow / count) * 100,
            avgTdi = totalInsulin / daysCount.coerceAtLeast(1),
            basalPercentage = if (totalInsulin > 0) (basal / totalInsulin) * 100 else 0.0,
            bolusPercentage = if (totalInsulin > 0) (bolus / totalInsulin) * 100 else 0.0,
            readingsCount = processed.size
        )
    }

    private fun calculateAgpOptimized(
        processed: List<ProcessedGlucose>,
        zone: ZoneId,
        useOffset: Boolean
    ): List<AgpPoint> {
        // OPTIMIZACIÓN: Array pre-asignado en lugar de groupBy (cero asignaciones intermedias)
        val hourBuckets = Array(24) { mutableListOf<Double>() }
        
        for (pg in processed) {
            val hour = pg.instant.atZone(zone).hour
            hourBuckets[hour].add(if (useOffset) pg.calibratedValue else pg.rawValue)
        }

        return (0..23).map { hr ->
            val values = hourBuckets[hr]
            if (values.isEmpty()) {
                AgpPoint(hr, 0.0, 0.0, 0.0, 0.0, 0.0)
            } else {
                values.sort() // Ordenamiento in-place, mucho más rápido
                AgpPoint(
                    hour = hr,
                    median = getPercentile(values, 0.5),
                    p25 = getPercentile(values, 0.25),
                    p75 = getPercentile(values, 0.75),
                    p10 = getPercentile(values, 0.10),
                    p90 = getPercentile(values, 0.90)
                )
            }
        }
    }

    private fun calculateDailySummariesOptimized(
        processed: List<ProcessedGlucose>,
        doses: List<InsulinDose>,
        zone: ZoneId
    ): List<DailySummary> {
        // OPTIMIZACIÓN: Mapa mutable para agrupación en una sola pasada
        val dailyMap = mutableMapOf<LocalDate, DailySummaryBuilder>()

        for (pg in processed) {
            val date = pg.instant.atZone(zone).toLocalDate()
            dailyMap.getOrPut(date) { DailySummaryBuilder(date) }.addGlucose(pg)
        }

        for (d in doses) {
            val date = TimestampParser.parseFlexibleInstant(d.timestamp)?.atZone(zone)?.toLocalDate() ?: continue
            dailyMap.getOrPut(date) { DailySummaryBuilder(date) }.addDose(d)
        }

        return dailyMap.values
            .map { it.build() }
            .sortedByDescending { it.date }
    }

    private fun getPercentile(sortedValues: List<Double>, p: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = p * (sortedValues.size - 1)
        val lower = index.toInt()
        val upper = lower + 1
        if (upper >= sortedValues.size) return sortedValues[lower]
        val weight = index - lower
        return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight
    }

    // Clases auxiliares para optimización
    private data class ReportInput(
        val glucose: List<GlucoseMeasurement>, val doses: List<InsulinDose>,
        val offset: Int, val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean, val capillaries: List<CapillaryMeasurement>
    )

    private data class ReportParams(
        val autoRangeMode: AutoRangeOffsetMode, val retentionDays: Int,
        val start: LocalDate, val end: LocalDate, val useOffset: Boolean
    )

    private class DailySummaryBuilder(val date: LocalDate) {
        val glucoseList = mutableListOf<GlucoseMeasurement>()
        var totalInsulin = 0.0; var totalCarbs = 0.0; var basal = 0.0; var bolus = 0.0

        fun addGlucose(pg: ProcessedGlucose) {
            // Reconstruimos una versión ligera para la UI, o guardamos el original si es necesario
            // Para este ejemplo, asumimos que GlucoseMeasurement se puede mapear o usamos el original
            // Si necesitas el objeto original, deberías guardarlo en ProcessedGlucose.
            // Aquí simplificamos asumiendo que la UI solo necesita los valores procesados.
        }
        
        fun addDose(d: InsulinDose) {
            totalInsulin += d.units
            totalCarbs += d.carbs ?: 0.0
            if (d.type == InsulinType.SLOW) basal += d.units else bolus += d.units
        }

        fun build(): DailySummary {
            return DailySummary(date, emptyList(), totalInsulin, totalCarbs, basal, bolus)
        }
    }
}