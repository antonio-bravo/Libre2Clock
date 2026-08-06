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
    val durationMinutes: Int,
    val carbs: Double? = null
)

@Serializable
data class HistoryBackupPayload(
    val historicalGlucoseArchive: List<GlucoseMeasurement> = emptyList(),
    val capillaryReadings: List<CapillaryMeasurement> = emptyList(),
    val insulinDoses: List<InsulinDose> = emptyList(),
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
    val disableFastRefreshOnSlowCharge: Boolean? = null
)
