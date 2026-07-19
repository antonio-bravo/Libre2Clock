package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.SensorStatus
import kotlinx.coroutines.flow.Flow

interface GlucoseRepository {
    val currentGlucose: Flow<GlucoseMeasurement?>
    val historicalGlucose: Flow<List<GlucoseMeasurement>>
    val sensorStatus: Flow<SensorStatus?>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun fetchLatestGlucose(): Result<GlucoseMeasurement>
    suspend fun refreshHistoricalGlucoseWindow(): Result<GlucoseMeasurement>
    fun enableDemoMode()
    fun disableDemoMode()
}
