package com.tonio.libre2clock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.SensorStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: GlucoseRepository
) : ViewModel() {

    val currentGlucose: StateFlow<GlucoseMeasurement?> = repository.currentGlucose
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sensorStatus: StateFlow<SensorStatus?> = repository.sensorStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val historicalData: StateFlow<List<GlucoseMeasurement>> = repository.historicalGlucose
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
