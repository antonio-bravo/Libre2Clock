package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Utility to process glucose data based on specific adjustment logic
 * found in community Python scripts.
 */
object GlucoseProcessor {

    /**
     * Applies the range-based offset and a user-defined manual offset to a raw glucose measurement.
     * @param measurement The raw measurement from the API
     * @param manualOffset A manual offset provided by the user (default 0)
     * @param userRanges Custom offset ranges provided by the user
     */
    fun process(
        measurement: GlucoseMeasurement,
        manualOffset: Int = 0,
        userRanges: List<GlucoseOffsetRange> = emptyList(),
        autoAdjustEnabled: Boolean = false,
        capillaryReadings: List<CapillaryMeasurement> = emptyList()
    ): GlucoseMeasurement {
        val rawValue = measurement.value

        val calibratedValue = getCalibratedValue(
            rawValue = rawValue,
            manualOffset = manualOffset,
            userRanges = userRanges,
            autoAdjustEnabled = autoAdjustEnabled,
            capillaryReadings = capillaryReadings,
            measurementTimestamp = measurement.timestamp
        )

        return measurement.copy(
            calibratedValue = calibratedValue
        )
    }

    /**
     * Calculates the calibrated value based on raw value and offsets.
     */
    fun getCalibratedValue(
        rawValue: Int,
        manualOffset: Int = 0,
        userRanges: List<GlucoseOffsetRange> = emptyList(),
        autoAdjustEnabled: Boolean = false,
        capillaryReadings: List<CapillaryMeasurement> = emptyList(),
        measurementTimestamp: String? = null
    ): Int {
        val matchingRange = userRanges.find { range ->
            rawValue >= range.min && (range.max == null || rawValue < range.max)
        }

        val rangeFixedOffset = matchingRange?.offset ?: 0
        val rangePercentageOffset = matchingRange?.let { range ->
            (rawValue * (range.percentage / 100.0)).roundToInt()
        } ?: 0
        val autoAdjustment = if (autoAdjustEnabled) {
            getAutoAdjustment(rawValue, measurementTimestamp, capillaryReadings)
        } else {
            0
        }

        return rawValue + rangeFixedOffset + rangePercentageOffset + manualOffset + autoAdjustment
    }

    fun getAutoAdjustment(
        rawValue: Int,
        measurementTimestamp: String?,
        capillaryReadings: List<CapillaryMeasurement>,
        maxHoursDifference: Long = 6
    ): Int {
        if (capillaryReadings.isEmpty()) return 0

        val measurementInstant = measurementTimestamp?.let(::parseTimestampToInstant)
        val candidates = capillaryReadings.mapNotNull { reading ->
            val readingInstant = parseTimestampToInstant(reading.timestamp) ?: return@mapNotNull null
            val hoursDifference = measurementInstant?.let {
                abs(Duration.between(it, readingInstant).toMinutes().toDouble() / 60.0)
            } ?: 0.0
            if (hoursDifference <= maxHoursDifference) {
                reading to hoursDifference
            } else {
                null
            }
        }

        val bestMatch = candidates.minByOrNull { it.second }
        return bestMatch?.let { (reading, _) ->
            reading.delta
                ?: reading.sensorValue?.let { reading.value - it }
                ?: (reading.value - rawValue)
        } ?: 0
    }

    private fun parseTimestampToInstant(timestamp: String): Instant? {
        return try {
            Instant.parse(timestamp)
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            } catch (_: DateTimeParseException) {
                try {
                LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                } catch (_: DateTimeParseException) {
                    try {
                        LocalTime.parse(timestamp, DateTimeFormatter.ofPattern("HH:mm"))
                            .atDate(LocalDateTime.now().toLocalDate())
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }

    /**
     * Formats the glucose display as original(calibrated).
     */
    fun formatDualValue(rawValue: Int, calibratedValue: Int): String {
        return "$rawValue($calibratedValue)"
    }

    /**
     * Maps the trend arrow integer to a string description or icon reference
     * following the 1-6 mapping logic.
     * 1: Falling Quickly (↓)
     * 2: Falling (↘)
     * 3: Stable (→)
     * 4: Rising (↗)
     * 5: Rising Quickly (↑)
     * 6: Not Determined
     */
    fun getTrendArrowSymbol(trend: Int?): String {
        return when (trend) {
            1 -> "↓"
            2 -> "↘"
            3 -> "→"
            4 -> "↗"
            5 -> "↑"
            else -> "→"
        }
    }
}
