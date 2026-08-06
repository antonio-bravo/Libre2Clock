package com.tonio.libre2clock.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.RangeOffsetInsight
import com.tonio.libre2clock.data.model.WatchNotificationMode
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    private val repository: GlucoseRepository
) : ViewModel() {

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage = _backupStatusMessage.asStateFlow()

    val glucoseOffset: StateFlow<Int> = preferenceManager.glucoseOffset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val glucoseOffsetRanges: StateFlow<List<GlucoseOffsetRange>> = preferenceManager.glucoseOffsetRanges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoAdjustEnabled: StateFlow<Boolean> = preferenceManager.autoAdjustEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoRangeOffsetMode: StateFlow<AutoRangeOffsetMode> = preferenceManager.autoRangeOffsetMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoRangeOffsetMode.OFF)

    val capillaryReadings: StateFlow<List<CapillaryMeasurement>> = preferenceManager.capillaryReadings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchAlertsEnabled: StateFlow<Boolean> = preferenceManager.watchAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val watchNotificationMode: StateFlow<WatchNotificationMode> = preferenceManager.watchNotificationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatchNotificationMode.OFF)

    val watchAlertIntervalMinutes: StateFlow<Int> = preferenceManager.watchAlertIntervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    val watchAlertStartMinute: StateFlow<Int> = preferenceManager.watchAlertStartMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lowGlucoseAlarmEnabled: StateFlow<Boolean> = preferenceManager.lowGlucoseAlarmEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val highGlucoseAlarmEnabled: StateFlow<Boolean> = preferenceManager.highGlucoseAlarmEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val useCalibratedForAlarms: StateFlow<Boolean> = preferenceManager.useCalibratedForAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lastHistoryBackupRequestAt: StateFlow<Long?> = preferenceManager.lastHistoryBackupRequestAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val historyRetentionDays: StateFlow<Int> = preferenceManager.historyRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 90)

    val isDemoMode: StateFlow<Boolean> = preferenceManager.isDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rapidDurationMins: StateFlow<Int> = preferenceManager.rapidDurationMins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 240)

    val slowDurationMins: StateFlow<Int> = preferenceManager.slowDurationMins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1440)

    val icRuleConstant: StateFlow<Int> = preferenceManager.icRuleConstant
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 450)

    val isfRuleConstant: StateFlow<Int> = preferenceManager.isfRuleConstant
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1800)

    val manualTdi: StateFlow<Double?> = preferenceManager.manualTdi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val manualIsf: StateFlow<Double?> = preferenceManager.manualIsf
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val targetGlucose: StateFlow<Int> = preferenceManager.targetGlucose
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    val insulinDoses: StateFlow<List<com.tonio.libre2clock.data.model.InsulinDose>> = preferenceManager.insulinDoses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchNotificationSchedules: StateFlow<List<com.tonio.libre2clock.data.model.AlarmSchedule>> = preferenceManager.watchNotificationSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val glucoseAlarmSchedules: StateFlow<List<com.tonio.libre2clock.data.model.AlarmSchedule>> = preferenceManager.glucoseAlarmSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batteryLowThreshold: StateFlow<Int> = preferenceManager.batteryLowThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    val batteryCriticalThreshold: StateFlow<Int> = preferenceManager.batteryCriticalThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val disableFastRefreshOnSlowCharge: StateFlow<Boolean> = preferenceManager.disableFastRefreshOnSlowCharge
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val currentGlucose: StateFlow<GlucoseMeasurement?> = combine(
        combine(
            repository.currentGlucose,
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode
        ) { current, manualOffset, ranges, autoAdjust, autoRangeMode ->
            CurrentGlucoseInputs(current, manualOffset, ranges, autoAdjust, autoRangeMode)
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rangeOffsetInsights: StateFlow<List<RangeOffsetInsight>> = combine(
        preferenceManager.glucoseOffsetRanges,
        preferenceManager.capillaryReadings
    ) { ranges, capillaries ->
        ranges.mapNotNull { range ->
            val estimate = GlucoseProcessor.estimateOffsetsForRange(range, capillaries) ?: return@mapNotNull null
            val points = capillaries.mapNotNull { reading ->
                val sensor = reading.sensorValue ?: return@mapNotNull null
                if (sensor == 0) return@mapNotNull null
                if (sensor < range.min) return@mapNotNull null
                if (range.max != null && sensor >= range.max) return@mapNotNull null
                sensor to reading.value
            }
            if (points.isEmpty()) return@mapNotNull null

            val avgSensor = points.map { it.first.toDouble() }.average()
            val avgCapillary = points.map { it.second.toDouble() }.average()

            val currentMae = points.map { (sensor, capillary) ->
                abs(sensor - capillary).toDouble()
            }.average()

            // Raw sensor deviation (absolute baseline)
            val currentDeviationPct = points.map { (sensor, capillary) ->
                if (capillary <= 0) 0.0 else (abs(sensor - capillary).toDouble() / capillary) * 100.0
            }.average()

            // Signed error (BIAS) of the RAW sensor
            val signedRawBias = points.map { (sensor, capillary) ->
                if (capillary <= 0) 0.0 else ((sensor - capillary).toDouble() / capillary) * 100.0
            }.average()

            // Signed error with CURRENT user offsets applied
            val signedCalibratedError = points.map { (sensor, capillary) ->
                val calibrated = sensor + range.offset + (sensor * (range.percentage / 100.0))
                if (capillary <= 0) 0.0 else ((calibrated - capillary) / capillary) * 100.0
            }.average()

            val suggestedMae = points.map { (sensor, capillary) ->
                val predicted = sensor + estimate.offset + (sensor * (estimate.percentage / 100.0))
                abs(predicted - capillary)
            }.average()
            val suggestedDeviationPct = points.map { (sensor, capillary) ->
                if (capillary <= 0) 0.0 else (abs((sensor + estimate.offset + (sensor * (estimate.percentage / 100.0))) - capillary) / capillary) * 100.0
            }.average()

            RangeOffsetInsight(
                min = range.min,
                max = range.max,
                sampleCount = estimate.sampleCount,
                suggestedOffset = estimate.offset,
                suggestedPercentage = estimate.percentage,
                currentMae = currentMae,
                suggestedMae = suggestedMae,
                currentDeviationPct = currentDeviationPct,
                suggestedDeviationPct = suggestedDeviationPct,
                avgSensorValue = avgSensor,
                avgCapillaryValue = avgCapillary,
                signedCalibratedDeviationPct = signedCalibratedError,
                signedRawDeviationPct = signedRawBias
            )
        }.sortedBy { it.min }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateOffset(offset: Int) {
        viewModelScope.launch {
            preferenceManager.saveGlucoseOffset(offset)
        }
    }

    fun updateAutoAdjustEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.saveAutoAdjustEnabled(enabled)
        }
    }

    fun updateAutoRangeOffsetMode(mode: AutoRangeOffsetMode) {
        viewModelScope.launch {
            preferenceManager.saveAutoRangeOffsetMode(mode)
        }
    }

    fun addCapillaryReading(reading: CapillaryMeasurement) {
        viewModelScope.launch {
            val currentReadings = capillaryReadings.value.toMutableList()
            currentReadings.add(reading)
            currentReadings.sortByDescending { it.timestamp }
            preferenceManager.saveCapillaryReadings(currentReadings)
        }
    }

    fun removeCapillaryReading(reading: CapillaryMeasurement) {
        viewModelScope.launch {
            val currentReadings = capillaryReadings.value.toMutableList()
            currentReadings.remove(reading)
            preferenceManager.saveCapillaryReadings(currentReadings)
        }
    }

    fun updateWatchAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                preferenceManager.initializeWatchAlertStartMinuteIfMissing()
            }
            preferenceManager.saveWatchAlertsEnabled(enabled)
        }
    }

    fun updateWatchNotificationMode(mode: WatchNotificationMode) {
        viewModelScope.launch {
            if (mode != WatchNotificationMode.OFF) {
                preferenceManager.initializeWatchAlertStartMinuteIfMissing()
            }
            preferenceManager.saveWatchNotificationMode(mode)
        }
    }

    fun updateWatchAlertIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            preferenceManager.saveWatchAlertIntervalMinutes(minutes)
        }
    }

    fun updateWatchAlertStartMinute(minute: Int) {
        viewModelScope.launch {
            preferenceManager.saveWatchAlertStartMinute(minute)
        }
    }

    fun updateLowGlucoseAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.saveLowGlucoseAlarmEnabled(enabled)
        }
    }

    fun updateHighGlucoseAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.saveHighGlucoseAlarmEnabled(enabled)
        }
    }

    fun updateUseCalibratedForAlarms(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.saveUseCalibratedForAlarms(enabled)
        }
    }

    fun requestHistoryBackupNow() {
        viewModelScope.launch {
            val requested = preferenceManager.requestHistoryCloudBackupIfDue(force = true)
            _backupStatusMessage.value = if (requested) {
                "Google backup requested."
            } else {
                "No Google backup request was sent."
            }
        }
    }

    fun updateHistoryRetentionDays(days: Int) {
        viewModelScope.launch {
            preferenceManager.saveHistoryRetentionDays(days)
        }
    }

    fun updateDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                repository.enableDemoMode()
            } else {
                repository.disableDemoMode()
            }
        }
    }

    fun requestPartialHistoryBackup(
        includeHistoricalGlucose: Boolean,
        includeCapillaryReadings: Boolean,
        includeInsulinDoses: Boolean = true
    ) {
        viewModelScope.launch {
            val requested = preferenceManager.requestPartialHistoryCloudBackup(
                includeHistoricalGlucose = includeHistoricalGlucose,
                includeCapillaryReadings = includeCapillaryReadings,
                includeInsulinDoses = includeInsulinDoses
            )
            _backupStatusMessage.value = if (requested) {
                "Partial Google backup requested."
            } else {
                "Partial Google backup request failed."
            }
        }
    }

    fun restorePartialHistoryFromBackup(
        includeHistoricalGlucose: Boolean,
        includeCapillaryReadings: Boolean,
        includeInsulinDoses: Boolean = true
    ) {
        viewModelScope.launch {
            val restored = preferenceManager.restorePartialHistoryFromBackup(
                includeHistoricalGlucose = includeHistoricalGlucose,
                includeCapillaryReadings = includeCapillaryReadings,
                includeInsulinDoses = includeInsulinDoses
            )
            _backupStatusMessage.value = if (restored) {
                "Partial restore completed."
            } else {
                "Partial restore failed or no backup data was found."
            }
        }
    }

    fun exportLocalBackupToDownloads() {
        viewModelScope.launch {
            val result = preferenceManager.exportHistoryBackupToDownloads()
            _backupStatusMessage.value = result.fold(
                onSuccess = { path -> "Local backup exported to $path" },
                onFailure = { error -> error.message ?: "Local backup export failed." }
            )
        }
    }

    fun restoreLocalBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = preferenceManager.restoreHistoryBackupFromUri(uri)
            _backupStatusMessage.value = result.fold(
                onSuccess = { "Local backup restored and merged." },
                onFailure = { error -> error.message ?: "Local backup restore failed." }
            )
        }
    }

    fun clearBackupStatusMessage() {
        _backupStatusMessage.value = null
    }

    fun addRange(range: GlucoseOffsetRange) {
        viewModelScope.launch {
            val currentRanges = glucoseOffsetRanges.value.toMutableList()
            currentRanges.add(range)
            currentRanges.sortBy { it.min }
            preferenceManager.saveGlucoseOffsetRanges(currentRanges)
        }
    }

    fun addDefaultRange() {
        viewModelScope.launch {
            val currentRanges = glucoseOffsetRanges.value.toMutableList()
            val newRange = GlucoseOffsetRange(0, 0, 0)
            currentRanges.add(newRange)
            currentRanges.sortBy { it.min }
            preferenceManager.saveGlucoseOffsetRanges(currentRanges)
        }
    }

    fun removeRange(range: GlucoseOffsetRange) {
        viewModelScope.launch {
            val currentRanges = glucoseOffsetRanges.value.toMutableList()
            currentRanges.remove(range)
            preferenceManager.saveGlucoseOffsetRanges(currentRanges)
        }
    }

    fun updateRange(oldRange: GlucoseOffsetRange, newRange: GlucoseOffsetRange) {
        viewModelScope.launch {
            val currentRanges = glucoseOffsetRanges.value.toMutableList()
            val index = currentRanges.indexOf(oldRange)
            if (index != -1) {
                currentRanges[index] = newRange
                currentRanges.sortBy { it.min }
                preferenceManager.saveGlucoseOffsetRanges(currentRanges)
            }
        }
    }

    fun applySuggestedRangeOffsets(minSamples: Int = 2) {
        viewModelScope.launch {
            val insightsByKey = rangeOffsetInsights.value
                .filter { it.sampleCount >= minSamples }
                .associateBy { insightKey(it.min, it.max) }

            val updated = glucoseOffsetRanges.value.map { range ->
                val insight = insightsByKey[insightKey(range.min, range.max)]
                if (insight != null) {
                    range.copy(
                        offset = insight.suggestedOffset,
                        percentage = insight.suggestedPercentage
                    )
                } else {
                    range
                }
            }
            preferenceManager.saveGlucoseOffsetRanges(updated)
        }
    }

    private fun insightKey(min: Int, max: Int?): String = "$min:${max ?: "inf"}"

    private data class CurrentGlucoseInputs(
        val current: GlucoseMeasurement?,
        val manualOffset: Int,
        val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode
    )

    fun updateRapidDuration(minutes: Int) {
        viewModelScope.launch { preferenceManager.saveRapidDurationMins(minutes) }
    }

    fun updateSlowDuration(minutes: Int) {
        viewModelScope.launch { preferenceManager.saveSlowDurationMins(minutes) }
    }

    fun updateIcRuleConstant(constant: Int) {
        viewModelScope.launch { preferenceManager.saveIcRuleConstant(constant) }
    }

    fun updateIsfRuleConstant(constant: Int) {
        viewModelScope.launch { preferenceManager.saveIsfRuleConstant(constant) }
    }

    fun updateManualTdi(tdi: Double?) {
        viewModelScope.launch { preferenceManager.saveManualTdi(tdi) }
    }

    fun updateManualIsf(isf: Double?) {
        viewModelScope.launch { preferenceManager.saveManualIsf(isf) }
    }

    fun updateTargetGlucose(target: Int) {
        viewModelScope.launch { preferenceManager.saveTargetGlucose(target) }
    }

    fun addInsulinDose(dose: com.tonio.libre2clock.data.model.InsulinDose) {
        viewModelScope.launch {
            val current = insulinDoses.value.toMutableList()
            current.add(dose)
            current.sortByDescending { it.timestamp }
            preferenceManager.saveInsulinDoses(current)
        }
    }

    fun removeInsulinDose(dose: com.tonio.libre2clock.data.model.InsulinDose) {
        viewModelScope.launch {
            val current = insulinDoses.value.toMutableList()
            current.remove(dose)
            preferenceManager.saveInsulinDoses(current)
        }
    }

    fun updateInsulinDose(oldDose: com.tonio.libre2clock.data.model.InsulinDose, newDose: com.tonio.libre2clock.data.model.InsulinDose) {
        viewModelScope.launch {
            val current = insulinDoses.value.toMutableList()
            val index = current.indexOf(oldDose)
            if (index != -1) {
                current[index] = newDose
                current.sortByDescending { it.timestamp }
                preferenceManager.saveInsulinDoses(current)
            }
        }
    }

    fun addWatchSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = watchNotificationSchedules.value.toMutableList()
            current.add(schedule)
            preferenceManager.saveWatchNotificationSchedules(current)
        }
    }

    fun updateWatchSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = watchNotificationSchedules.value.toMutableList()
            val index = current.indexOfFirst { it.id == schedule.id }
            if (index != -1) {
                current[index] = schedule
                preferenceManager.saveWatchNotificationSchedules(current)
            }
        }
    }

    fun removeWatchSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = watchNotificationSchedules.value.toMutableList()
            current.removeIf { it.id == schedule.id }
            preferenceManager.saveWatchNotificationSchedules(current)
        }
    }

    fun addAlarmSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = glucoseAlarmSchedules.value.toMutableList()
            current.add(schedule)
            preferenceManager.saveGlucoseAlarmSchedules(current)
        }
    }

    fun updateAlarmSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = glucoseAlarmSchedules.value.toMutableList()
            val index = current.indexOfFirst { it.id == schedule.id }
            if (index != -1) {
                current[index] = schedule
                preferenceManager.saveGlucoseAlarmSchedules(current)
            }
        }
    }

    fun removeAlarmSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = glucoseAlarmSchedules.value.toMutableList()
            current.removeIf { it.id == schedule.id }
            preferenceManager.saveGlucoseAlarmSchedules(current)
        }
    }

    fun updateBatteryLowThreshold(threshold: Int) {
        viewModelScope.launch { preferenceManager.saveBatteryLowThreshold(threshold) }
    }

    fun updateBatteryCriticalThreshold(threshold: Int) {
        viewModelScope.launch { preferenceManager.saveBatteryCriticalThreshold(threshold) }
    }

    fun updateDisableFastRefreshOnSlowCharge(disabled: Boolean) {
        viewModelScope.launch { preferenceManager.saveDisableFastRefreshOnSlowCharge(disabled) }
    }
}
