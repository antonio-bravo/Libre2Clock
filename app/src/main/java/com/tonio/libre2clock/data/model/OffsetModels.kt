package com.tonio.libre2clock.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GlucoseOffsetRange(
    val min: Int,
    val max: Int?,
    val offset: Int,
    val percentage: Int = 0
)

@Serializable
data class CapillaryMeasurement(
    val value: Int,
    val timestamp: String,
    val sensorValue: Int? = null,
    val delta: Int? = null
)

@Serializable
data class HistoryBackupPayload(
    val historicalGlucoseArchive: List<GlucoseMeasurement> = emptyList(),
    val capillaryReadings: List<CapillaryMeasurement> = emptyList()
)
