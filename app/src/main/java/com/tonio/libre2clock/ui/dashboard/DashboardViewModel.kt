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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardViewModel(
    private val repository: GlucoseRepository,
    private val preferenceManager: PreferenceManager,
    private val androidContext: android.content.Context
) : ViewModel() {

    private val _isHistoryRefreshing = MutableStateFlow(false)
    val isHistoryRefreshing: StateFlow<Boolean> = _isHistoryRefreshing.asStateFlow()

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
        preferenceManager.capillaryReadings
    ) { inputs, capillaries ->
        inputs.current?.let {
            GlucoseProcessor.process(
                measurement = it,
                manualOffset = inputs.manualOffset,
                userRanges = inputs.ranges,
                autoAdjustEnabled = inputs.autoAdjust,
                autoRangeOffsetMode = inputs.autoRangeMode,
                capillaryReadings = capillaries
            )
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val sensorStatus: StateFlow<SensorStatus?> = combine(
        repository.activeSensorInfo,
        repository.isDemoMode,
        ticker
    ) { info: ActiveSensorInfo?, demoEnabled: Boolean, _: Unit ->
        calculateSensorStatus(info, demoEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isDemoMode: StateFlow<Boolean> = repository.isDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val historicalData: StateFlow<List<GlucoseMeasurement>> = combine(
        combine(
            repository.historicalGlucose,
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode
        ) { historical, manualOffset, ranges, autoAdjust, autoRangeMode ->
            HistoricalInputs(historical, manualOffset, ranges, autoAdjust, autoRangeMode)
        },
        preferenceManager.capillaryReadings
    ) { inputs, capillaries ->
        inputs.historical.map {
            GlucoseProcessor.process(
                measurement = it,
                manualOffset = inputs.manualOffset,
                userRanges = inputs.ranges,
                autoAdjustEnabled = inputs.autoAdjust,
                autoRangeOffsetMode = inputs.autoRangeMode,
                capillaryReadings = capillaries
            )
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val graphData: StateFlow<List<GlucoseMeasurement>> = historicalData
        .map { data ->
            val cutoff = Instant.now().minus(java.time.Duration.ofHours(24))
            data.filter { m ->
                val instant = TimestampParser.parseFlexibleInstant(m.factoryTimestamp) ?: TimestampParser.parseFlexibleInstant(m.timestamp)
                instant?.isAfter(cutoff) == true
            }
        }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardMetrics: StateFlow<DashboardMetrics> = historicalData
        .map { DashboardMetricsCalculator.calculate(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope, 
            SharingStarted.WhileSubscribed(5000), 
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

    init {
        // startSync() was removed to optimize performance and avoid redundant work. 
        // GlucoseForegroundService already handles background sync.
    }

    fun refresh() {
        viewModelScope.launch {
            repository.fetchLatestGlucose()
        }
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

    private fun calculateSensorStatus(info: ActiveSensorInfo?, demoEnabled: Boolean): SensorStatus? {
        if (demoEnabled) {
            return SensorStatus(
                daysRemaining = androidContext.getString(R.string.sensor_remaining_days, 14, 0, 0),
                startDate = androidContext.getString(R.string.sensor_started_label, "Mon, Nov 03, 2025 10:30"),
                expiryDate = androidContext.getString(R.string.sensor_expires_label, "Mon, Nov 17, 2025 10:30"),
                serialNumber = "DEMO-12345"
            )
        }
        
        if (info == null) return null
        
        val expiryTime = info.activationTimestamp + (14 * 24 * 60 * 60)
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
