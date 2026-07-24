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
enum class InsulinType {
    RAPID, SLOW
}

@Serializable
data class InsulinDose(
    val units: Double,
    val timestamp: String,
    val type: InsulinType,
    val durationMinutes: Int
)

@Serializable
data class HistoryBackupPayload(
    val historicalGlucoseArchive: List<GlucoseMeasurement> = emptyList(),
    val capillaryReadings: List<CapillaryMeasurement> = emptyList(),
    val insulinDoses: List<InsulinDose> = emptyList()
)
