package com.tonio.libre2clock.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.GlucoseMeasurement
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

    val capillaryReadings: StateFlow<List<CapillaryMeasurement>> = preferenceManager.capillaryReadings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchAlertsEnabled: StateFlow<Boolean> = preferenceManager.watchAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    val currentGlucose: StateFlow<GlucoseMeasurement?> = combine(
        repository.currentGlucose,
        preferenceManager.glucoseOffset,
        preferenceManager.glucoseOffsetRanges,
        preferenceManager.autoAdjustEnabled,
        preferenceManager.capillaryReadings
    ) { current, manualOffset, ranges, autoAdjust, capillaries ->
        current?.let {
            GlucoseProcessor.process(it, manualOffset, ranges, autoAdjust, capillaries)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
}
