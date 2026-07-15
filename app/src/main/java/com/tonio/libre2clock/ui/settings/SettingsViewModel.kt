package com.tonio.libre2clock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.PreferenceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    repository: GlucoseRepository
) : ViewModel() {

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), java.time.LocalTime.now().minute)

    val lowGlucoseAlarmEnabled: StateFlow<Boolean> = preferenceManager.lowGlucoseAlarmEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val highGlucoseAlarmEnabled: StateFlow<Boolean> = preferenceManager.highGlucoseAlarmEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentGlucose: StateFlow<GlucoseMeasurement?> = repository.currentGlucose
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
}
