package com.tonio.libre2clock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.SensorStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: GlucoseRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _isHistoryRefreshing = MutableStateFlow(false)
    val isHistoryRefreshing: StateFlow<Boolean> = _isHistoryRefreshing.asStateFlow()

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

    val sensorStatus: StateFlow<SensorStatus?> = repository.sensorStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isDemoMode: StateFlow<Boolean> = repository.isDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val historicalData: StateFlow<List<GlucoseMeasurement>> = combine(
        repository.historicalGlucose,
        preferenceManager.glucoseOffset,
        preferenceManager.glucoseOffsetRanges,
        preferenceManager.autoAdjustEnabled,
        preferenceManager.capillaryReadings
    ) { historical, manualOffset, ranges, autoAdjust, capillaries ->
        historical.map {
            GlucoseProcessor.process(it, manualOffset, ranges, autoAdjust, capillaries)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        startSync()
    }

    private fun startSync() {
        viewModelScope.launch {
            while (true) {
                repository.fetchLatestGlucose()
                // If we have no data, retry faster. Otherwise, sync every minute.
                delay(if (historicalData.value.isEmpty()) 2000 else 60000)
            }
        }
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
}
