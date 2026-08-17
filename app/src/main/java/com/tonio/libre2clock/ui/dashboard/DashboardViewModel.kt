package com.tonio.libre2clock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.ActiveSensorInfo
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.SensorStatus
import com.tonio.libre2clock.util.TimestampParser
import com.tonio.libre2clock.util.buildSensorErrorSummary
import com.tonio.libre2clock.util.SensorErrorSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardViewModel(
    private val repository: GlucoseRepository,
    private val preferenceManager: PreferenceManager,
    private val androidContext: android.content.Context
) : ViewModel() {

    private val dashboardMetricsCache = DashboardMetricsCacheRepository(androidContext)

    private val _isHistoryRefreshing = MutableStateFlow(false)
    val isHistoryRefreshing: StateFlow<Boolean> = _isHistoryRefreshing.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _graphWindowDays = MutableStateFlow(1)
    val graphWindowDays: StateFlow<Int> = _graphWindowDays.asStateFlow()

    val currentGlucose: StateFlow<GlucoseMeasurement?> = combine(
        combine(
            repository.currentGlucose,
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode
        ) { current, manualOffset, ranges, autoAdjust, autoRangeMode ->
            DashboardInputs(current, manualOffset, ranges, autoAdjust, autoRangeMode)
        },
        preferenceManager.capillaryReadings,
        preferenceManager.sensorLogs
    ) { inputs, capillaries, logs ->
        inputs.current?.let {
            val calcContext = GlucoseProcessor.buildContext(
                autoRangeOffsetMode = inputs.autoRangeMode,
                userRanges = inputs.ranges,
                capillaryReadings = capillaries,
                sensorLogs = logs
            )
            GlucoseProcessor.process(
                measurement = it,
                manualOffset = inputs.manualOffset,
                userRanges = inputs.ranges,
                autoAdjustEnabled = inputs.autoAdjust,
                autoRangeOffsetMode = inputs.autoRangeMode,
                capillaryReadings = capillaries,
                context = calcContext
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val sensorStatus: StateFlow<SensorStatus?> = combine(
        repository.activeSensorInfo,
        repository.isDemoMode,
        preferenceManager.sensorDurationDays,
        ticker
    ) { info: ActiveSensorInfo?, demoEnabled: Boolean, duration: Int, _: Unit ->
        calculateSensorStatus(info, demoEnabled, duration)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isDemoMode: StateFlow<Boolean> = repository.isDemoMode
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // OPTIMIZED: Graph data processes its own window independently of the full history
    val graphData: StateFlow<List<GlucoseMeasurement>> = combine(
        combine(
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode
        ) { manualOffset, ranges, autoAdjust, autoRangeMode ->
            HistoricalInputs(emptyList(), manualOffset, ranges, autoAdjust, autoRangeMode)
        },
        preferenceManager.capillaryReadings,
        preferenceManager.sensorLogs,
        _graphWindowDays,
        repository.dataVersion
    ) { config, capillaries, logs, days, _ ->
        val cutoff = Instant.now().minus(java.time.Duration.ofDays(days.toLong()))
        val startEpochMs = cutoff.toEpochMilli()
        val endEpochMs = Instant.now().toEpochMilli()
        
        val window = repository.getHistoricalGlucoseWindow(startEpochMs, endEpochMs, maxItems = 40000)
        
        val calcContext = GlucoseProcessor.buildContext(
            autoRangeOffsetMode = config.autoRangeMode,
            userRanges = config.ranges,
            capillaryReadings = capillaries,
            sensorLogs = logs
        )

        val sampled = if (window.size > 2000) {
            val step = window.size / 1500
            window.filterIndexed { index, _ -> index % step == 0 }
        } else {
            window
        }

        sampled.map {
            GlucoseProcessor.process(
                measurement = it,
                manualOffset = config.manualOffset,
                userRanges = config.ranges,
                autoAdjustEnabled = config.autoAdjust,
                autoRangeOffsetMode = config.autoRangeMode,
                capillaryReadings = capillaries,
                context = calcContext
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Processed history for live metrics (Fast: 2 days)
    private val processedLiveHistory: Flow<List<GlucoseMeasurement>> = combine(
        combine(
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode
        ) { manualOffset, ranges, autoAdjust, autoRangeMode ->
            HistoricalInputs(emptyList(), manualOffset, ranges, autoAdjust, autoRangeMode)
        },
        preferenceManager.capillaryReadings,
        repository.dataVersion
    ) { config, capillaries, _ ->
        val cutoff = Instant.now().minus(java.time.Duration.ofDays(2))
        val startEpochMs = cutoff.toEpochMilli()
        val endEpochMs = Instant.now().toEpochMilli()

        val calcContext = GlucoseProcessor.buildContext(
            autoRangeOffsetMode = config.autoRangeMode,
            userRanges = config.ranges,
            capillaryReadings = capillaries
        )

        repository.getHistoricalGlucoseWindow(startEpochMs, endEpochMs, maxItems = 3000)
            .map {
                GlucoseProcessor.process(
                    measurement = it,
                    manualOffset = config.manualOffset,
                    userRanges = config.ranges,
                    autoAdjustEnabled = config.autoAdjust,
                    autoRangeOffsetMode = config.autoRangeMode,
                    capillaryReadings = capillaries,
                    context = calcContext
                )
            }
    }.flowOn(Dispatchers.Default)

    // Processed history for historical metrics (Slow: 90 days)
    private val processedHistoricalData: Flow<List<GlucoseMeasurement>> = combine(
        combine(
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode
        ) { manualOffset, ranges, autoAdjust, autoRangeMode ->
            HistoricalInputs(emptyList(), manualOffset, ranges, autoAdjust, autoRangeMode)
        },
        preferenceManager.capillaryReadings,
        repository.dataVersion.map { it / 10 }.distinctUntilChanged() // Throttled: only every 10 new readings
    ) { config, capillaries, _ ->
        val cutoff = Instant.now().minus(java.time.Duration.ofDays(90))
        val startEpochMs = cutoff.toEpochMilli()
        val endEpochMs = Instant.now().toEpochMilli()

        val calcContext = GlucoseProcessor.buildContext(
            autoRangeOffsetMode = config.autoRangeMode,
            userRanges = config.ranges,
            capillaryReadings = capillaries
        )

        repository.getHistoricalGlucoseWindow(startEpochMs, endEpochMs, maxItems = 50000)
            .map {
                GlucoseProcessor.process(
                    measurement = it,
                    manualOffset = config.manualOffset,
                    userRanges = config.ranges,
                    autoAdjustEnabled = config.autoAdjust,
                    autoRangeOffsetMode = config.autoRangeMode,
                    capillaryReadings = capillaries,
                    context = calcContext
                )
            }
    }.flowOn(Dispatchers.Default)

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        processedLiveHistory,
        processedHistoricalData,
        preferenceManager.historyRetentionDays,
        repository.dataVersion,
        preferenceManager.capillaryReadings
    ) { live, historical, retentionDays, version, capillaries ->
        val liveMetrics = DashboardMetricsCalculator.calculateLive(live)
        
        val signature = DashboardMetricsCacheRepository.buildSignatureFast(
            measurements = historical,
            dataVersion = version / 10, // Throttled signature
            capillaries = capillaries
        )
        
        val historicalMetrics = dashboardMetricsCache.getOrCompute(
            sectionKey = "historical_metrics_v2",
            signature = signature,
            retentionDays = retentionDays
        ) {
            DashboardMetricsCalculator.calculateHistorical(historical)
        }
        
        historicalMetrics.copy(
            todayAvg = liveMetrics.todayAvg,
            yesterdayAvg = liveMetrics.yesterdayAvg
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope, 
            SharingStarted.Lazily, 
            DashboardMetricsCalculator.calculate(emptyList())
        )

    val insulinDoses: StateFlow<List<com.tonio.libre2clock.data.model.InsulinDose>> = preferenceManager.insulinDoses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val manualTdi: StateFlow<Double?> = preferenceManager.manualTdi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val manualIsf: StateFlow<Double?> = preferenceManager.manualIsf
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val isfRuleConstant: StateFlow<Int> = preferenceManager.isfRuleConstant
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1800)

    val currentSensorError: StateFlow<SensorErrorSummary?> = combine(
        preferenceManager.activeSensorSerialNumber,
        preferenceManager.capillaryReadings
    ) { serial, capillaries ->
        if (serial.isNullOrBlank()) return@combine null
        // Reuse the logic but only for the active sensor
        buildSensorErrorSummary(emptyList(), capillaries)
            .find { it.serialNumber == serial }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Foreground fallback sync while dashboard is open.
        // This keeps UI updated even if the background service is stopped by the OS.
        viewModelScope.launch {
            while (true) {
                runCatching { repository.fetchLatestGlucose() }
                delay(60_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.fetchLatestGlucose()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setGraphWindow(days: Int) {
        _graphWindowDays.value = days
    }

    fun refreshHistoryWindow() {
        viewModelScope.launch {
            _isHistoryRefreshing.value = true
            try {
                repository.refreshHistoricalGlucoseWindow()
            } finally {
                _isHistoryRefreshing.value = false
            }
        }
    }

    fun addInsulinDose(dose: com.tonio.libre2clock.data.model.InsulinDose) {
        viewModelScope.launch {
            val current = insulinDoses.value.toMutableList()
            current.add(dose)
            current.sortByDescending { it.timestamp }
            preferenceManager.saveInsulinDoses(current)
        }
    }

    fun addCapillaryReading(reading: com.tonio.libre2clock.data.model.CapillaryMeasurement) {
        viewModelScope.launch {
            val currentReadings = preferenceManager.capillaryReadings.first().toMutableList()
            val withSensor = reading.copy(
                sensorSerialNumber = reading.sensorSerialNumber ?: preferenceManager.activeSensorSerialNumber.first()
            )
            currentReadings.add(withSensor)
            currentReadings.sortByDescending { it.timestamp }
            preferenceManager.saveCapillaryReadings(currentReadings)
        }
    }

    private fun calculateSensorStatus(info: ActiveSensorInfo?, demoEnabled: Boolean, sensorDurationDays: Int): SensorStatus? {
        if (demoEnabled) {
            return SensorStatus(
                daysRemaining = androidContext.getString(R.string.sensor_remaining_days, sensorDurationDays, 0, 0),
                startDate = androidContext.getString(R.string.sensor_started_label, "Mon, Nov 03, 2025 10:30"),
                expiryDate = androidContext.getString(R.string.sensor_expires_label, "Tue, Nov 18, 2025 10:30"),
                serialNumber = "DEMO-12345"
            )
        }
        
        if (info == null) return null
        
        val expiryTime = info.activationTimestamp + (sensorDurationDays.toLong() * 24 * 60 * 60)
        val now = Instant.now().epochSecond
        val remainingSeconds = expiryTime - now

        val days = (remainingSeconds / (24 * 60 * 60)).toInt()
        val hours = ((remainingSeconds % (24 * 60 * 60)) / 3600).toInt()
        val minutes = ((remainingSeconds % 3600) / 60).toInt()

        val remainingStr = when {
            remainingSeconds <= 0 -> androidContext.getString(R.string.sensor_expired)
            days > 0 -> androidContext.getString(R.string.sensor_remaining_days, days, hours, minutes)
            hours > 0 -> androidContext.getString(R.string.sensor_remaining_hours, hours, minutes)
            else -> androidContext.getString(R.string.sensor_remaining_minutes, minutes)
        }

        val formatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy HH:mm", Locale.US)
            .withZone(ZoneId.systemDefault())
        val startDateStr = formatter.format(Instant.ofEpochSecond(info.activationTimestamp))
        val expiryDateStr = formatter.format(Instant.ofEpochSecond(expiryTime))

        return SensorStatus(
            daysRemaining = remainingStr,
            startDate = androidContext.getString(R.string.sensor_started_label, startDateStr),
            expiryDate = androidContext.getString(R.string.sensor_expires_label, expiryDateStr),
            serialNumber = info.serialNumber
        )
    }

    private data class DashboardInputs(
        val current: GlucoseMeasurement?,
        val manualOffset: Int,
        val ranges: List<com.tonio.libre2clock.data.model.GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode
    )

    private data class HistoricalInputs(
        val historical: List<GlucoseMeasurement>,
        val manualOffset: Int,
        val ranges: List<com.tonio.libre2clock.data.model.GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode
    )
}
