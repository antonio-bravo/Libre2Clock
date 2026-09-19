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
data class TimeInRangesHours(
    val tirHours: Double = 0.0,
    val tarHighHours: Double = 0.0,
    val tarVHighHours: Double = 0.0,
    val tbrLowHours: Double = 0.0,
    val tbrVLowHours: Double = 0.0
)

@Serializable
data class AgpTargetsStatus(
    val isTirMet: Boolean = false,
    val isTbrMet: Boolean = false,
    val isTbrVLowMet: Boolean = false,
    val isTarMet: Boolean = false,
    val isTarVHighMet: Boolean = false,
    val isCvMet: Boolean = false
)

@Serializable
data class ReportMetrics(
    val avgGlucose: Double,
    val gmi: Double,
    val cv: Double,
    val stdDev: Double = 0.0,
    val activeSensorPercent: Double = 0.0,
    val tir: Double,
    val tarHigh: Double,
    val tarVHigh: Double,
    val tbrLow: Double,
    val tbrVLow: Double,
    val timeInRangesHours: TimeInRangesHours = TimeInRangesHours(),
    val targetsStatus: AgpTargetsStatus = AgpTargetsStatus(),
    val avgTdi: Double,
    val basalPercentage: Double,
    val bolusPercentage: Double,
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

@Serializable
data class MultiPeriodCv(
    val cv7d: Double = 0.0,
    val cv14d: Double = 0.0,
    val cv30d: Double = 0.0,
    val cv90d: Double = 0.0,
    val rawCv7d: Double = 0.0,
    val rawCv14d: Double = 0.0,
    val rawCv30d: Double = 0.0,
    val rawCv90d: Double = 0.0
)

// OPTIMIZACIÓN: Agrupamos todo el reporte en una sola clase para calcular y cachear una sola vez
@Serializable
data class FullReportData(
    val metrics: ReportMetrics,
    val rawMetrics: ReportMetrics? = null,
    val agp: List<AgpPoint>,
    val rawAgp: List<AgpPoint>? = null,
    val dailySummaries: List<DailySummary>,
    val multiPeriodCv: MultiPeriodCv = MultiPeriodCv()
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

    // 1. Ventana de datos optimizada (Asegura al menos 90 días para el desglose temporal de CV%)
    private val windowedData: Flow<Pair<List<GlucoseMeasurement>, List<InsulinDose>>> = combine(
        _startDate, _endDate, preferenceManager.insulinDoses, repository.historicalGlucose
    ) { start, end, doses, _ ->
        val zone = ZoneId.systemDefault()
        val effectiveStart = if (ChronoUnit.DAYS.between(start, end) < 90) end.minusDays(90) else start
        val startInstant = effectiveStart.atStartOfDay(zone).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(zone).toInstant()

        val filteredG = repository.getHistoricalGlucoseWindow(
            startEpochMs = startInstant.toEpochMilli(),
            endEpochMs = endInstant.toEpochMilli(),
            maxItems = 20000
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
            _startDate, _endDate, preferenceManager.activeSensorSerialNumber
        ) { mode, retention, start, end, activeSn ->
            ReportParams(mode, retention, start, end, activeSn)
        }
    ) { input: ReportInput, params: ReportParams ->
        val signature = ReportSectionCacheRepository.buildSignature(
            glucose = input.glucose, doses = input.doses, offset = input.offset,
            ranges = input.ranges, autoAdjustEnabled = input.autoAdjust,
            capillaries = input.capillaries, autoRangeMode = params.autoRangeMode.name,
            extraTag = "full_report:${params.start}:${params.end}:${params.activeSensorSn}"
        )
        
        reportCache.getOrComputeFullReport(
            signature = signature,
            retentionDays = params.retentionDays
        ) {
            val zone = ZoneId.systemDefault()
            val calcContext = GlucoseProcessor.buildContext(
                autoRangeOffsetMode = params.autoRangeMode,
                userRanges = input.ranges,
                capillaryReadings = input.capillaries,
                activeSensorSn = params.activeSensorSn
            )

            // OPTIMIZACIÓN CRÍTICA: Procesar la glucosa UNA SOLA VEZ
            val processedGlucose = input.glucose.mapNotNull { m ->
                val instant = TimestampParser.parseMeasurementInstant(m) ?: return@mapNotNull null
                
                val processed = GlucoseProcessor.process(
                    measurement = m, manualOffset = input.offset, userRanges = input.ranges,
                    autoAdjustEnabled = input.autoAdjust, autoRangeOffsetMode = params.autoRangeMode,
                    capillaryReadings = input.capillaries, context = calcContext,
                    activeSensorSn = params.activeSensorSn
                )
                ProcessedGlucose(instant, processed.value.toDouble(), processed.calibratedValue.toDouble())
            }

            val startInstant = params.start.atStartOfDay(zone).toInstant()
            val endInstant = params.end.plusDays(1).atStartOfDay(zone).toInstant()

            // Filtrar glucosa para el rango seleccionado
            val selectedRangeGlucose = processedGlucose.filter {
                !it.instant.isBefore(startInstant) && !it.instant.isAfter(endInstant)
            }

            val daysCount = (ChronoUnit.DAYS.between(params.start, params.end) + 1).toInt()

            val multiPeriodCv = MultiPeriodCv(
                cv7d = calculateCvForPeriod(processedGlucose, endInstant, 7),
                cv14d = calculateCvForPeriod(processedGlucose, endInstant, 14),
                cv30d = calculateCvForPeriod(processedGlucose, endInstant, 30),
                cv90d = calculateCvForPeriod(processedGlucose, endInstant, 90),
                rawCv7d = calculateCvForPeriod(processedGlucose, endInstant, 7, useOffset = false),
                rawCv14d = calculateCvForPeriod(processedGlucose, endInstant, 14, useOffset = false),
                rawCv30d = calculateCvForPeriod(processedGlucose, endInstant, 30, useOffset = false),
                rawCv90d = calculateCvForPeriod(processedGlucose, endInstant, 90, useOffset = false)
            )

            FullReportData(
                metrics = calculateMetricsOptimized(selectedRangeGlucose, input.doses, daysCount, useOffset = true),
                rawMetrics = calculateMetricsOptimized(selectedRangeGlucose, input.doses, daysCount, useOffset = false),
                agp = calculateAgpOptimized(selectedRangeGlucose, zone, useOffset = true),
                rawAgp = calculateAgpOptimized(selectedRangeGlucose, zone, useOffset = false),
                dailySummaries = calculateDailySummariesOptimized(selectedRangeGlucose, input.doses, zone),
                multiPeriodCv = multiPeriodCv
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Exponemos los datos individuales para que la UI no tenga que cambiar mucho
    val reportMetrics: StateFlow<ReportMetrics?> = reportData.map { it?.metrics }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val rawReportMetrics: StateFlow<ReportMetrics?> = reportData.map { it?.rawMetrics }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val agpData: StateFlow<List<AgpPoint>> = reportData.map { it?.agp ?: emptyList() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rawAgpData: StateFlow<List<AgpPoint>> = reportData.map { it?.rawAgp ?: emptyList() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dailySummaries: StateFlow<List<DailySummary>> = reportData.map { it?.dailySummaries ?: emptyList() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val multiPeriodCv: StateFlow<MultiPeriodCv> = reportData.map { it?.multiPeriodCv ?: MultiPeriodCv() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MultiPeriodCv())

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

    private fun calculateCvForPeriod(
        processed: List<ProcessedGlucose>,
        endInstant: Instant,
        days: Long,
        useOffset: Boolean = true
    ): Double {
        val cutoff = endInstant.minus(days, ChronoUnit.DAYS)
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (pg in processed) {
            if (!pg.instant.isBefore(cutoff) && !pg.instant.isAfter(endInstant)) {
                val v = if (useOffset) pg.calibratedValue else pg.rawValue
                sum += v
                sumSq += v * v
                count++
            }
        }

        if (count == 0) return 0.0
        val avg = sum / count
        val variance = (sumSq / count) - (avg * avg)
        val stdDev = if (variance > 0) sqrt(variance) else 0.0
        return if (avg > 0) (stdDev / avg) * 100.0 else 0.0
    }

    private fun calculateMetricsOptimized(
        processed: List<ProcessedGlucose>,
        doses: List<InsulinDose>,
        daysCount: Int,
        useOffset: Boolean
    ): ReportMetrics {
        if (processed.isEmpty()) {
            return ReportMetrics(
                avgGlucose = 0.0, gmi = 0.0, cv = 0.0, stdDev = 0.0, activeSensorPercent = 0.0,
                tir = 0.0, tarHigh = 0.0, tarVHigh = 0.0, tbrLow = 0.0, tbrVLow = 0.0,
                timeInRangesHours = TimeInRangesHours(), targetsStatus = AgpTargetsStatus(),
                avgTdi = 0.0, basalPercentage = 0.0, bolusPercentage = 0.0, readingsCount = 0
            )
        }

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

        val tirPct = (tir / count) * 100
        val tarHighPct = (tarHigh / count) * 100
        val tarVHighPct = (tarVHigh / count) * 100
        val tbrLowPct = (tbrLow / count) * 100
        val tbrVLowPct = (tbrVLow / count) * 100

        // Horas por día dedicadas a cada rango (basado en 24h por día)
        val tirHours = (tirPct / 100.0) * 24.0
        val tarHighHours = (tarHighPct / 100.0) * 24.0
        val tarVHighHours = (tarVHighPct / 100.0) * 24.0
        val tbrLowHours = (tbrLowPct / 100.0) * 24.0
        val tbrVLowHours = (tbrVLowPct / 100.0) * 24.0

        val timeInRangesHours = TimeInRangesHours(
            tirHours = tirHours,
            tarHighHours = tarHighHours,
            tarVHighHours = tarVHighHours,
            tbrLowHours = tbrLowHours,
            tbrVLowHours = tbrVLowHours
        )

        // Estado de cumplimiento de objetivos clínicos del consenso internacional
        val targetsStatus = AgpTargetsStatus(
            isTirMet = tirPct >= 70.0,
            isTbrMet = (tbrLowPct + tbrVLowPct) <= 4.0,
            isTbrVLowMet = tbrVLowPct <= 1.0,
            isTarMet = (tarHighPct + tarVHighPct) <= 25.0,
            isTarVHighMet = tarVHighPct <= 5.0,
            isCvMet = cv <= 36.0
        )

        // Cálculo de porcentaje de sensor activo
        // Si hay más de 96 lecturas/día (intervalos de <15 min, p. ej. 1 min = 1440 lecturas/día), usamos 1440; sino 96 (15 min).
        val safeDays = daysCount.coerceAtLeast(1)
        val maxExpected15Min = safeDays * 96.0
        val maxExpected1Min = safeDays * 1440.0
        val expectedReadings = if (count > maxExpected15Min * 1.2) maxExpected1Min else maxExpected15Min
        val activeSensorPct = ((count / expectedReadings) * 100.0).coerceAtMost(100.0)

        var totalInsulin = 0.0; var basal = 0.0; var bolus = 0.0
        for (d in doses) {
            totalInsulin += d.units
            if (d.type == InsulinType.SLOW) basal += d.units else bolus += d.units
        }

        return ReportMetrics(
            avgGlucose = avg,
            gmi = gmi,
            cv = cv,
            stdDev = stdDev,
            activeSensorPercent = activeSensorPct,
            tir = tirPct,
            tarHigh = tarHighPct,
            tarVHigh = tarVHighPct,
            tbrLow = tbrLowPct,
            tbrVLow = tbrVLowPct,
            timeInRangesHours = timeInRangesHours,
            targetsStatus = targetsStatus,
            avgTdi = totalInsulin / safeDays,
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
        val start: LocalDate, val end: LocalDate, val activeSensorSn: String? = null
    )

    private class DailySummaryBuilder(val date: LocalDate) {
        val glucoseList = mutableListOf<GlucoseMeasurement>()
        var totalInsulin = 0.0; var totalCarbs = 0.0; var basal = 0.0; var bolus = 0.0

        fun addGlucose(pg: ProcessedGlucose) {
            glucoseList.add(
                GlucoseMeasurement(
                    factoryTimestamp = pg.instant.toString(),
                    timestamp = pg.instant.toString(),
                    value = pg.rawValue.toInt(),
                    valueInMgPerDl = pg.rawValue.toInt(),
                    calibratedValue = pg.calibratedValue.toInt(),
                    epochSeconds = pg.instant.epochSecond
                )
            )
        }
        
        fun addDose(d: InsulinDose) {
            totalInsulin += d.units
            totalCarbs += d.carbs ?: 0.0
            if (d.type == InsulinType.SLOW) basal += d.units else bolus += d.units
        }

        fun build(): DailySummary {
            return DailySummary(
                date = date,
                glucose = glucoseList.sortedBy { it.epochSeconds ?: 0L },
                insulin = totalInsulin,
                carbs = totalCarbs,
                basal = basal,
                bolus = bolus
            )
        }
    }
}