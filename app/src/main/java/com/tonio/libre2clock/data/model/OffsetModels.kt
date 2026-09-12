package com.tonio.libre2clock.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class GlucoseOffsetRange(
    val min: Int = 0,
    val max: Int? = null,
    val offset: Int = 0,
    val percentage: Int = 0
)

@Serializable
enum class WatchNotificationMode {
    OFF,
    PERIODIC_ONLY,
    SCHEDULES_ONLY,
    PERIODIC_AND_SCHEDULES
}

@Serializable
enum class AutoRangeOffsetMode {
    OFF,
    GLOBAL,
    BY_RANGE
}

@Serializable
data class RangeOffsetInsight(
    val min: Int,
    val max: Int?,
    val sampleCount: Int,
    val suggestedOffset: Int,
    val suggestedPercentage: Int,
    val currentMae: Double,
    val suggestedMae: Double,
    val currentDeviationPct: Double,
    val suggestedDeviationPct: Double,
    val avgCapillaryValue: Double = 0.0,
    val avgSensorValue: Double = 0.0,
    val signedCalibratedDeviationPct: Double = 0.0,
    val signedRawDeviationPct: Double = 0.0
)

@Serializable
data class CapillaryMeasurement(
    val id: String = UUID.randomUUID().toString(),
    val value: Int = 0,
    val timestamp: String = "",
    val sensorValue: Int? = null,
    val delta: Int? = null,
    val sensorSerialNumber: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Serializable
enum class InsulinType {
    RAPID, SLOW
}

@Serializable
data class InsulinDose(
    val id: String = UUID.randomUUID().toString(),
    val units: Double = 0.0,
    val timestamp: String = "",
    val type: InsulinType = InsulinType.RAPID,
    val durationMinutes: Int = 0,
    val carbs: Double? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Serializable
data class SensorLog(
    val serialNumber: String = "",
    val startDate: String = "",
    val expiryDate: String = "",
    val endDate: String? = null,
    val actualDaysUsed: Double? = null,
    val hasFailed: Boolean = false,
    val errorCode: String? = null,
    val notes: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Serializable
data class HistoryBackupPayload(
    val historicalGlucoseArchive: List<GlucoseMeasurement> = emptyList(),
    val capillaryReadings: List<CapillaryMeasurement> = emptyList(),
    val insulinDoses: List<InsulinDose> = emptyList(),
    val sensorLogs: List<SensorLog> = emptyList(),
    // Metadata
    val settingsUpdatedAtMs: Long? = null,
    // Glucose Config
    val glucoseOffset: Int? = null,
    val glucoseOffsetRanges: List<GlucoseOffsetRange>? = null,
    val autoAdjustEnabled: Boolean? = null,
    val autoRangeOffsetsEnabled: Boolean? = null,
    val autoRangeOffsetMode: AutoRangeOffsetMode? = null,
    // Insulin Config
    val rapidDurationMins: Int? = null,
    val slowDurationMins: Int? = null,
    val icRuleConstant: Int? = null,
    val isfRuleConstant: Int? = null,
    val manualTdi: Double? = null,
    val manualIsf: Double? = null,
    val targetGlucose: Int? = null,
    // Alert Config
    val watchAlertsEnabled: Boolean? = null,
    val watchNotificationMode: WatchNotificationMode? = null,
    val watchAlertIntervalMinutes: Int? = null,
    val watchAlertStartMinute: Int? = null,
    val lowGlucoseAlarmEnabled: Boolean? = null,
    val highGlucoseAlarmEnabled: Boolean? = null,
    val useCalibratedForAlarms: Boolean? = null,
    // App Config
    val historyRetentionDays: Int? = null,
    // Schedules
    val watchNotificationSchedules: List<AlarmSchedule> = emptyList(),
    val glucoseAlarmSchedules: List<AlarmSchedule> = emptyList(),
    // Battery Optimization
    val batteryLowThreshold: Int? = null,
    val batteryCriticalThreshold: Int? = null,
    val disableFastRefreshOnSlowCharge: Boolean? = null,
    val sensorDurationDays: Int? = null
)
