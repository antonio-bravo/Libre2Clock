package com.tonio.libre2clock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.ActiveSensorInfo
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.data.model.SensorStatus
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.InsulinProcessor
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.util.SensorErrorSummary
import com.tonio.libre2clock.util.TimestampParser
import com.tonio.libre2clock.util.buildSensorErrorSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

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

    // --- 1. Current Glucose (Optimized Combine) ---
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

    // --- 2. Sensor Status (Ticker is safely cancelled when unsubscribed due to Lazily) ---
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

    // --- 3. Graph Data (Zero-Allocation Sampling) ---
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
        val nowMs = System.currentTimeMillis()
        val roundedEndMs = (nowMs / 30000) * 30000
        val roundedStartMs = roundedEndMs - Duration.ofDays(days.toLong()).toMillis()
        
        val window = repository.getHistoricalGlucoseWindow(roundedStartMs, roundedEndMs, maxItems = 40000)
        
        // OPTIMIZACIÓN: Bucle for con ArrayList pre-asignado es mucho más rápido que filterIndexed
        val sampled = if (window.size > 2000) {
            val step = (window.size / 1500).coerceAtLeast(1)
            val result = ArrayList<GlucoseMeasurement>(1500)
            for (i in window.indices step step) {
                result.add(window[i])
            }
            // Asegurar que el último punto siempre se incluya para continuidad visual
            if (result.lastOrNull() != window.lastOrNull()) {
                result.add(window.last())
            }
            result
        } else {
            window
        }

        val calcContext = GlucoseProcessor.buildContext(
            autoRangeOffsetMode = config.autoRangeMode,
            userRanges = config.ranges,
            capillaryReadings = capillaries,
            sensorLogs = logs
        )

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

    // --- 4. Dashboard Metrics (Unified & Cancellable) ---
    // Data class para evitar el anti-patrón de Array<Any> y casts no seguros
    private data class MetricsInputs(
        val retentionDays: Int,
        val dataVersion: Long,
        val capillaries: List<com.tonio.libre2clock.data.model.CapillaryMeasurement>,
        val manualOffset: Int,
        val ranges: List<com.tonio.libre2clock.data.model.GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode,
        val sensorLogs: List<com.tonio.libre2clock.data.model.SensorLog>
    )

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        preferenceManager.historyRetentionDays,
        repository.dataVersion,
        preferenceManager.capillaryReadings,
        preferenceManager.glucoseOffset,
        preferenceManager.glucoseOffsetRanges,
        preferenceManager.autoAdjustEnabled,
        preferenceManager.autoRangeOffsetMode,
        preferenceManager.sensorLogs
    ) { args: Array<Any?> ->
        MetricsInputs(
            retentionDays = args[0] as Int,
            dataVersion = args[1] as Long,
            capillaries = args[2] as List<CapillaryMeasurement>,
            manualOffset = args[3] as Int,
            ranges = args[4] as List<GlucoseOffsetRange>,
            autoAdjust = args[5] as Boolean,
            autoRangeMode = args[6] as AutoRangeOffsetMode,
            sensorLogs = args[7] as List<SensorLog>
        )
    }.flatMapLatest { inputs -> 
        // OPTIMIZACIÓN CRÍTICA: flatMapLatest cancela el cálculo anterior si los inputs cambian rápidamente
        // evitando picos de CPU por cálculos huérfanos de 150k elementos.
        flow {
            val signature = DashboardMetricsCacheRepository.buildSignatureFast(
                dataVersion = inputs.dataVersion,
                capillaries = inputs.capillaries,
                ranges = inputs.ranges,
                sensorLogs = inputs.sensorLogs,
                manualOffset = inputs.manualOffset,
                autoAdjust = inputs.autoAdjust,
                autoRangeMode = inputs.autoRangeMode.name
            )
            
            val metrics = dashboardMetricsCache.getOrCompute(
                sectionKey = "historical_metrics_v2",
                signature = signature,
                retentionDays = inputs.retentionDays
            ) {
                // Gracias al optimizador O(N) de paso único, ya no necesitamos separar "live" de "histórico".
                // Procesamos la ventana completa de 90 días una sola vez.
                val cutoff = Instant.now().minus(Duration.ofDays(90))
                val rawHistorical = repository.getHistoricalGlucoseWindow(cutoff.toEpochMilli(), Instant.now().toEpochMilli(), maxItems = 150000)

                val calcContext = GlucoseProcessor.buildContext(
                    autoRangeOffsetMode = inputs.autoRangeMode,
                    userRanges = inputs.ranges,
                    capillaryReadings = inputs.capillaries,
                    sensorLogs = inputs.sensorLogs
                )
                
                val processed = rawHistorical.map {
                    GlucoseProcessor.process(
                        measurement = it,
                        manualOffset = inputs.manualOffset,
                        userRanges = inputs.ranges,
                        autoAdjustEnabled = inputs.autoAdjust,
                        autoRangeOffsetMode = inputs.autoRangeMode,
                        capillaryReadings = inputs.capillaries,
                        context = calcContext
                    )
                }
                
                // El calculador optimizado extrae "hoy", "ayer", "semana", etc., de forma nativa.
                DashboardMetricsCalculator.calculate(processed)
            }
            emit(metrics)
        }.flowOn(Dispatchers.Default)
    }.stateIn(
        viewModelScope, 
        SharingStarted.Lazily, 
        DashboardMetricsCalculator.calculate(emptyList())
    )

    // --- 5. Insulin & Preferences (Standard StateIn) ---
    val insulinDoses: StateFlow<List<com.tonio.libre2clock.data.model.InsulinDose>> = preferenceManager.insulinDoses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val manualTdi: StateFlow<Double?> = preferenceManager.manualTdi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val manualIsf: StateFlow<Double?> = preferenceManager.manualIsf
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val isfRuleConstant: StateFlow<Int> = preferenceManager.isfRuleConstant
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1800)

    val icRuleConstant: StateFlow<Int> = preferenceManager.icRuleConstant
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 450)

    val targetGlucose: StateFlow<Int> = preferenceManager.targetGlucose
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 80)

    // --- 6. Sensor Error (Filtered Early) ---
    val currentSensorError: StateFlow<SensorErrorSummary?> = combine(
        preferenceManager.activeSensorSerialNumber,
        preferenceManager.capillaryReadings
    ) { serial, capillaries ->
        if (serial.isNullOrBlank()) return@combine null
        // OPTIMIZACIÓN: Filtrar primero por serial evita procesar miles de capilares de otros sensores
        val sensorCapillaries = capillaries.filter { it.sensorSerialNumber == serial }
        buildSensorErrorSummary(emptyList(), sensorCapillaries).firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- 7. Predicted Glucose ---
    val predictedGlucose: StateFlow<List<Pair<Instant, Int>>> = combine(
        currentGlucose,
        insulinDoses,
        manualTdi,
        manualIsf,
        isfRuleConstant
    ) { current, doses, tdi, mIsf, isfRule ->
        val g = current?.calibratedValue ?: return@combine emptyList()
        val calculatedTdi = tdi ?: InsulinProcessor.calculateAverageDaily(doses, 30)
        val isf = InsulinProcessor.calculateISF(calculatedTdi, isfRule, mIsf)
        
        InsulinProcessor.predictGlucosePath(g, doses, isf)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- 8. Background Sync ---
    init {
        viewModelScope.launch {
            // Nota: Es recomendable que el Repository tenga su propia lógica de caché 
            // para que fetchLatestGlucose() sea un no-op si los datos son recientes.
            while (true) {
                runCatching { repository.fetchLatestGlucose() }
                delay(60_000)
            }
        }
    }

    // --- Actions ---
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
            val activeSerial = preferenceManager.activeSensorSerialNumber.first()
            val withSensor = reading.copy(
                sensorSerialNumber = reading.sensorSerialNumber ?: activeSerial
            )
            currentReadings.add(withSensor)
            currentReadings.sortByDescending { it.timestamp }
            preferenceManager.saveCapillaryReadings(currentReadings)
        }
    }

    // --- Helpers ---
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

        val displayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault())
            .withLocale(Locale.getDefault())
        
        val startDateStr = displayFormatter.format(Instant.ofEpochSecond(info.activationTimestamp))
        val expiryDateStr = displayFormatter.format(Instant.ofEpochSecond(expiryTime))

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