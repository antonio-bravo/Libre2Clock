package com.tonio.libre2clock.ui.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.*
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.util.TimestampParser
import com.tonio.libre2clock.util.LocalDateSerializer
import com.tonio.libre2clock.util.ReportCalculator
import com.tonio.libre2clock.util.ProcessedGlucose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ReportRange(val days: Int) {
    ONE_DAY(1), SEVEN_DAYS(7), FIFTEEN_DAYS(15), THIRTY_DAYS(30), NINETY_DAYS(90)
}

enum class ReportLayout { SNAPSHOT, DAILY_LOG, FULL, AGP_COMPLETE }

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
    val readingsCount: Int,
    val overnightAvg: Double = 0.0,
    val overnightTir: Double = 0.0,
    val overnightTbr: Double = 0.0
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

@Serializable
data class FullReportData(
    val metrics: ReportMetrics,
    val rawMetrics: ReportMetrics? = null,
    val agp: List<AgpPoint>,
    val rawAgp: List<AgpPoint>? = null,
    val dailySummaries: List<DailySummary>,
    val multiPeriodCv: MultiPeriodCv = MultiPeriodCv()
)

class ReportViewModel(
    private val repository: GlucoseRepository,
    private val preferenceManager: PreferenceManager,
    androidContext: Context
) : ViewModel() {

    private val reportCache = ReportSectionCacheRepository(androidContext)

    private val _startDate = MutableStateFlow(LocalDate.now().minusDays(30))
    val startDate: StateFlow<LocalDate> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow(LocalDate.now())
    val endDate: StateFlow<LocalDate> = _endDate.asStateFlow()

    private val _useOffsetValues = MutableStateFlow(true)
    val useOffsetValues: StateFlow<Boolean> = _useOffsetValues.asStateFlow()

    private val _selectedLayout = MutableStateFlow(ReportLayout.FULL)
    val selectedLayout: StateFlow<ReportLayout> = _selectedLayout.asStateFlow()

    private val _compareRawAndCalibrated = MutableStateFlow(true)
    val compareRawAndCalibrated: StateFlow<Boolean> = _compareRawAndCalibrated.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _previewFile = MutableStateFlow<File?>(null)
    val previewFile: StateFlow<File?> = _previewFile.asStateFlow()

    private val _showPdfViewer = MutableStateFlow(false)
    val showPdfViewer: StateFlow<Boolean> = _showPdfViewer.asStateFlow()

    // Ventana de datos optimizada (al menos 90 días para CV%)
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

        val filteredD = doses.filter {
            val instant = TimestampParser.parseFlexibleInstant(it.timestamp)
            instant != null && !instant.isBefore(startInstant) && !instant.isAfter(endInstant)
        }
        filteredG to filteredD
    }.flowOn(Dispatchers.Default).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    // Flujo UNIFICADO: Calcula y cachea todo el reporte de una sola vez mediante ReportCalculator
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

            val selectedRangeGlucose = processedGlucose.filter {
                !it.instant.isBefore(startInstant) && !it.instant.isAfter(endInstant)
            }

            val daysCount = (ChronoUnit.DAYS.between(params.start, params.end) + 1).toInt()

            val multiPeriodCv = MultiPeriodCv(
                cv7d = ReportCalculator.calculateCvForPeriod(processedGlucose, endInstant, 7),
                cv14d = ReportCalculator.calculateCvForPeriod(processedGlucose, endInstant, 14),
                cv30d = ReportCalculator.calculateCvForPeriod(processedGlucose, endInstant, 30),
                cv90d = ReportCalculator.calculateCvForPeriod(processedGlucose, endInstant, 90),
                rawCv7d = ReportCalculator.calculateCvForPeriod(processedGlucose, endInstant, 7, useOffset = false),
                rawCv14d = ReportCalculator.calculateCvForPeriod(processedGlucose, endInstant, 14, useOffset = false),
                rawCv30d = ReportCalculator.calculateCvForPeriod(processedGlucose, endInstant, 30, useOffset = false),
                rawCv90d = ReportCalculator.calculateCvForPeriod(processedGlucose, endInstant, 90, useOffset = false)
            )

            FullReportData(
                metrics = ReportCalculator.calculateMetrics(selectedRangeGlucose, input.doses, daysCount, useOffset = true),
                rawMetrics = ReportCalculator.calculateMetrics(selectedRangeGlucose, input.doses, daysCount, useOffset = false),
                agp = ReportCalculator.calculateAgp(selectedRangeGlucose, zone, useOffset = true),
                rawAgp = ReportCalculator.calculateAgp(selectedRangeGlucose, zone, useOffset = false),
                dailySummaries = ReportCalculator.calculateDailySummaries(selectedRangeGlucose, input.doses, zone),
                multiPeriodCv = multiPeriodCv
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    fun setLayout(layout: ReportLayout) {
        _selectedLayout.value = layout
    }

    fun setCompareRawAndCalibrated(compare: Boolean) {
        _compareRawAndCalibrated.value = compare
    }

    fun setGenerating(generating: Boolean) {
        _isGenerating.value = generating
    }

    fun setPreviewFile(file: File?) {
        _previewFile.value = file
    }

    fun setShowPdfViewer(show: Boolean) {
        _showPdfViewer.value = show
    }

    private data class ReportInput(
        val glucose: List<GlucoseMeasurement>, val doses: List<InsulinDose>,
        val offset: Int, val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean, val capillaries: List<CapillaryMeasurement>
    )

    private data class ReportParams(
        val autoRangeMode: AutoRangeOffsetMode, val retentionDays: Int,
        val start: LocalDate, val end: LocalDate, val activeSensorSn: String? = null
    )
}
