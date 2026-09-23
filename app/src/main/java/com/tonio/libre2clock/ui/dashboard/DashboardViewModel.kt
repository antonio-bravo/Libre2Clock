package com.tonio.libre2clock.ui.dashboard

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.ActiveSensorInfo
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.data.model.SensorStatus
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.InsulinProcessor
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.util.SensorErrorSummary
import com.tonio.libre2clock.util.buildSensorErrorSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: GlucoseRepository,
    private val preferenceManager: PreferenceManager,
    private val androidContext: android.content.Context
) : ViewModel() {

    private val subscribedSharing = SharingStarted.WhileSubscribed(5000)
    private val dashboardMetricsCache = DashboardMetricsCacheRepository(androidContext)

    private val _isHistoryRefreshing = MutableStateFlow(false)
    val isHistoryRefreshing: StateFlow<Boolean> = _isHistoryRefreshing.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isBatteryOptimized = MutableStateFlow(false)
    val isBatteryOptimized: StateFlow<Boolean> = _isBatteryOptimized.asStateFlow()

    private val _graphWindowDays = MutableStateFlow(1)
    val graphWindowDays: StateFlow<Int> = _graphWindowDays.asStateFlow()

    // --- 1. Current Glucose ---
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
        preferenceManager.sensorLogs,
        preferenceManager.activeSensorSerialNumber
    ) { inputs, rawCapillaries, rawLogs, activeSn ->
        // OPTIMIZACIÓN: Filtramos una sola vez para usar en el contexto y el procesamiento
        val activeCapillaries = rawCapillaries.filter { !it.isDeleted }
        val activeLogs = rawLogs.filter { !it.isDeleted }
        
        inputs.current?.let {
            val calcContext = GlucoseProcessor.buildContext(
                autoRangeOffsetMode = inputs.autoRangeMode,
                userRanges = inputs.ranges,
                capillaryReadings = activeCapillaries,
                sensorLogs = activeLogs,
                activeSensorSn = activeSn
            )
            GlucoseProcessor.process(
                measurement = it,
                manualOffset = inputs.manualOffset,
                userRanges = inputs.ranges,
                autoAdjustEnabled = inputs.autoAdjust,
                autoRangeOffsetMode = inputs.autoRangeMode,
                capillaryReadings = activeCapillaries,
                context = calcContext,
                activeSensorSn = activeSn
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- 2. Sensor Status ---
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

    // --- 3. Graph Data ---
    val graphData: StateFlow<List<GlucoseMeasurement>> = combine(
        combine(
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode,
            preferenceManager.activeSensorSerialNumber
        ) { manualOffset, ranges, autoAdjust, autoRangeMode, activeSn ->
            HistoricalInputs(emptyList(), manualOffset, ranges, autoAdjust, autoRangeMode, activeSn)
        },
        preferenceManager.capillaryReadings,
        preferenceManager.sensorLogs,
        _graphWindowDays,
        repository.dataVersion
    ) { config, rawCapillaries, rawLogs, days, _ ->
        val activeCapillaries = rawCapillaries.filter { !it.isDeleted }
        val activeLogs = rawLogs.filter { !it.isDeleted }
        
        val nowMs = System.currentTimeMillis()
        val roundedEndMs = (nowMs / 30000) * 30000
        val roundedStartMs = roundedEndMs - Duration.ofDays(days.toLong()).toMillis()
        
        val window = repository.getHistoricalGlucoseWindow(roundedStartMs, roundedEndMs, maxItems = 40000)
        
        val sampled = if (window.size > 2000) {
            val step = (window.size / 1500).coerceAtLeast(1)
            val result = ArrayList<GlucoseMeasurement>(1500)
            for (i in window.indices step step) {
                result.add(window[i])
            }
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
            capillaryReadings = activeCapillaries,
            sensorLogs = activeLogs,
            activeSensorSn = config.activeSensorSn
        )

        sampled.map {
            GlucoseProcessor.process(
                measurement = it,
                manualOffset = config.manualOffset,
                userRanges = config.ranges,
                autoAdjustEnabled = config.autoAdjust,
                autoRangeOffsetMode = config.autoRangeMode,
                capillaryReadings = activeCapillaries,
                context = calcContext,
                activeSensorSn = config.activeSensorSn
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- 4. Dashboard Metrics ---
    private data class MetricsConfigPart1(
        val retentionDays: Int,
        val dataVersion: Long,
        val capillaries: List<CapillaryMeasurement>,
        val sensorLogs: List<SensorLog>
    )
    
    private data class MetricsConfigPart2(
        val manualOffset: Int,
        val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode,
        val activeSensorSn: String? = null
    )

    private data class MetricsInputs(
        val retentionDays: Int,
        val dataVersion: Long,
        val capillaries: List<CapillaryMeasurement>,
        val manualOffset: Int,
        val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode,
        val sensorLogs: List<SensorLog>,
        val activeSensorSn: String? = null
    )

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        combine(
            preferenceManager.historyRetentionDays,
            repository.dataVersion,
            preferenceManager.capillaryReadings,
            preferenceManager.sensorLogs
        ) { retention, version, rawCaps, rawLogs ->
            MetricsConfigPart1(
                retention, 
                version, 
                rawCaps.filter { !it.isDeleted }, 
                rawLogs.filter { !it.isDeleted }
            )
        },
        combine(
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode,
            preferenceManager.activeSensorSerialNumber
        ) { offset, ranges, auto, mode, activeSn ->
            MetricsConfigPart2(offset, ranges, auto, mode, activeSn)
        }
    ) { part1, part2 ->
        MetricsInputs(
            retentionDays = part1.retentionDays,
            dataVersion = part1.dataVersion,
            capillaries = part1.capillaries,
            manualOffset = part2.manualOffset,
            ranges = part2.ranges,
            autoAdjust = part2.autoAdjust,
            autoRangeMode = part2.autoRangeMode,
            sensorLogs = part1.sensorLogs,
            activeSensorSn = part2.activeSensorSn
        )
    }.flatMapLatest { inputs -> 
        flow {
            // 1. Emitir de inmediato la última versión guardada en SQLite para respuesta instantánea (0ms)
            val initialCached = dashboardMetricsCache.getLatestCached("historical_metrics_v2")
            if (initialCached != null) {
                emit(initialCached)
            }

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
                val cutoff = Instant.now().minus(Duration.ofDays(90))
                val rawHistorical = repository.getHistoricalGlucoseWindow(cutoff.toEpochMilli(), Instant.now().toEpochMilli(), maxItems = 150000)

                val isDefaultOffset = inputs.manualOffset == 0 && 
                    inputs.ranges.isEmpty() && 
                    !inputs.autoAdjust && 
                    inputs.autoRangeMode == AutoRangeOffsetMode.OFF

                val processed = if (isDefaultOffset) {
                    rawHistorical
                } else {
                    val calcContext = GlucoseProcessor.buildContext(
                        autoRangeOffsetMode = inputs.autoRangeMode,
                        userRanges = inputs.ranges,
                        capillaryReadings = inputs.capillaries,
                        sensorLogs = inputs.sensorLogs,
                        activeSensorSn = inputs.activeSensorSn
                    )
                    
                    rawHistorical.map {
                        GlucoseProcessor.process(
                            measurement = it,
                            manualOffset = inputs.manualOffset,
                            userRanges = inputs.ranges,
                            autoAdjustEnabled = inputs.autoAdjust,
                            autoRangeOffsetMode = inputs.autoRangeMode,
                            capillaryReadings = inputs.capillaries,
                            context = calcContext,
                            activeSensorSn = inputs.activeSensorSn
                        )
                    }
                }
                
                DashboardMetricsCalculator.calculate(processed)
            }
            emit(metrics)
        }.flowOn(Dispatchers.Default)
    }.stateIn(
        viewModelScope, 
        SharingStarted.WhileSubscribed(5000), 
        dashboardMetricsCache.getLatestCached("historical_metrics_v2")
            ?: DashboardMetricsCalculator.calculate(emptyList())
    )

    // --- 5. Insulin & Preferences ---
    // OPTIMIZACIÓN: distinctUntilChanged() evita recomposiciones si la lista de activos no cambia
    val insulinDoses: StateFlow<List<InsulinDose>> = preferenceManager.insulinDoses
        .map { list -> list.filter { !it.isDeleted } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, subscribedSharing, emptyList())

    val manualTdi: StateFlow<Double?> = preferenceManager.manualTdi
        .stateIn(viewModelScope, subscribedSharing, null)
    
    val manualIsf: StateFlow<Double?> = preferenceManager.manualIsf
        .stateIn(viewModelScope, subscribedSharing, null)
    
    val isfRuleConstant: StateFlow<Int> = preferenceManager.isfRuleConstant
        .stateIn(viewModelScope, subscribedSharing, 1800)

    val icRuleConstant: StateFlow<Int> = preferenceManager.icRuleConstant
        .stateIn(viewModelScope, subscribedSharing, 450)

    val targetGlucose: StateFlow<Int> = preferenceManager.targetGlucose
        .stateIn(viewModelScope, subscribedSharing, 80)

    val targetGlucoseLow: StateFlow<Int> = preferenceManager.targetGlucoseLow
        .stateIn(viewModelScope, subscribedSharing, 70)

    val targetGlucoseHigh: StateFlow<Int> = preferenceManager.targetGlucoseHigh
        .stateIn(viewModelScope, subscribedSharing, 180)

    val rapidDurationMinutes: StateFlow<Int> = preferenceManager.rapidDurationMins
        .stateIn(viewModelScope, subscribedSharing, 240)

    val slowDurationMinutes: StateFlow<Int> = preferenceManager.slowDurationMins
        .stateIn(viewModelScope, subscribedSharing, 1440)

    val deductIobForBolus: StateFlow<Boolean> = preferenceManager.deductIobForBolus
        .stateIn(viewModelScope, subscribedSharing, false)

    fun updateDeductIobForBolus(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.saveDeductIobForBolus(enabled) }
    }

    // --- 6. Sensor Error ---
    val currentSensorError: StateFlow<SensorErrorSummary?> = combine(
        preferenceManager.activeSensorSerialNumber,
        preferenceManager.capillaryReadings
    ) { serial, rawCapillaries ->
        if (serial.isNullOrBlank()) return@combine null
        // OPTIMIZACIÓN: Un solo filtro en lugar de dos encadenados
        val sensorCapillaries = rawCapillaries.filter { !it.isDeleted && it.sensorSerialNumber == serial }
        buildSensorErrorSummary(emptyList(), sensorCapillaries).firstOrNull()
    }.stateIn(viewModelScope, subscribedSharing, null)

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
    }.stateIn(viewModelScope, subscribedSharing, emptyList())

    // --- 8. Background Sync ---
    init {
        checkBatteryOptimization()
        viewModelScope.launch {
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
                // ✅ REPARACIÓN MÁXIMA: Limpiamos las cachés internas del repositorio y de ventanas temporales
                // para obligar al sistema a hacer un bypass total de cualquier dato estancado en base de datos.
                repository.clearCache()
                repository.fetchLatestGlucose()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setGraphWindow(days: Int) {
        _graphWindowDays.value = days
    }

    fun checkBatteryOptimization() {
        val powerManager = androidContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        _isBatteryOptimized.value = !powerManager.isIgnoringBatteryOptimizations(androidContext.packageName)
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

    suspend fun getSensorReadingForTime(date: LocalDate, hour: Int, minute: Int): GlucoseMeasurement? {
        val zone = ZoneId.systemDefault()
        val localDateTime = LocalDateTime.of(date, LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        val targetEpochMs = localDateTime.atZone(zone).toInstant().toEpochMilli()
        return repository.findSensorReadingForTimestamp(targetEpochMs) ?: currentGlucose.value
    }

    fun addInsulinDose(dose: InsulinDose) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allDoses = preferenceManager.insulinDoses.first().toMutableList()
                
                allDoses.add(dose.copy(updatedAtMs = System.currentTimeMillis()))
                allDoses.sortByDescending { it.timestamp }
                
                preferenceManager.saveInsulinDoses(allDoses)
            } catch (e: Exception) {
                // Log and swallow exception to prevent app crash
            }
        }
    }

    fun addCapillaryReading(reading: CapillaryMeasurement) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val activeSerial = preferenceManager.activeSensorSerialNumber.first()
                
                val allReadings = preferenceManager.capillaryReadings.first().toMutableList()
                
                val withSensor = reading.copy(
                    sensorSerialNumber = reading.sensorSerialNumber ?: activeSerial,
                    updatedAtMs = System.currentTimeMillis()
                )
                
                allReadings.add(withSensor)
                allReadings.sortByDescending { it.timestamp }
                
                preferenceManager.saveCapillaryReadings(allReadings)
            } catch (e: Exception) {
                // Log and swallow exception to prevent app crash
            }
        }
    }

    // 🚨 CRÍTICO: Funciones de borrado usando Soft Delete
    // NUNCA uses .remove() aquí. Debemos marcar como borrado para que el CloudSyncManager
    // pueda propagar la eliminación a los otros dispositivos.
    fun deleteInsulinDose(doseId: String) {
        viewModelScope.launch {
            val allDoses = preferenceManager.insulinDoses.first().toMutableList()
            val index = allDoses.indexOfFirst { it.id == doseId }
            
            if (index != -1) {
                allDoses[index] = allDoses[index].copy(
                    isDeleted = true,
                    updatedAtMs = System.currentTimeMillis()
                )
                preferenceManager.saveInsulinDoses(allDoses)
            }
        }
    }

    fun deleteCapillaryReading(readingId: String) {
        viewModelScope.launch {
            val allReadings = preferenceManager.capillaryReadings.first().toMutableList()
            val index = allReadings.indexOfFirst { it.id == readingId }
            
            if (index != -1) {
                allReadings[index] = allReadings[index].copy(
                    isDeleted = true,
                    updatedAtMs = System.currentTimeMillis()
                )
                preferenceManager.saveCapillaryReadings(allReadings)
            }
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

        val formatSensorDate = { epochSeconds: Long ->
            val instant = Instant.ofEpochSecond(epochSeconds)
            val zdt = instant.atZone(ZoneId.systemDefault())
            val day = zdt.dayOfMonth
            val rawMonth = zdt.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val month = rawMonth.replace(".", "").take(3)
            val year = zdt.year
            val hour = String.format(Locale.US, "%02d", zdt.hour)
            val minute = String.format(Locale.US, "%02d", zdt.minute)
            "$day $month $year, $hour:$minute"
        }

        val startDateStr = formatSensorDate(info.activationTimestamp)
        val expiryDateStr = formatSensorDate(expiryTime)

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
        val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode
    )

    private data class HistoricalInputs(
        val historical: List<GlucoseMeasurement>,
        val manualOffset: Int,
        val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode,
        val activeSensorSn: String? = null
    )
}