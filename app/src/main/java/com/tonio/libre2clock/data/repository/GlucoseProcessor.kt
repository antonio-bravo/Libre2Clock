package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.util.TimestampParser
import java.time.Duration
import java.time.Instant
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

        val measurementInstant = measurementTimestamp?.let(::parseTimestampToInstant) ?: return 0
        
        // Optimization: Capillary readings are usually sorted by timestamp descending.
        // We find the one with the smallest time difference.
        var bestAdjustment = 0
        var minDiffMinutes = Long.MAX_VALUE

        for (reading in capillaryReadings) {
            val readingInstant = parseTimestampToInstant(reading.timestamp) ?: continue
            val diffMinutes = abs(Duration.between(measurementInstant, readingInstant).toMinutes())
            
            if (diffMinutes < minDiffMinutes) {
                minDiffMinutes = diffMinutes
                bestAdjustment = reading.delta
                    ?: reading.sensorValue?.let { reading.value - it }
                    ?: (reading.value - rawValue)
            }
            
            // If we found a very close match or we are moving further away in time, we could break,
            // but for simplicity and safety with small lists, a single pass is fine.
            // With large lists, we'd use a binary search or a TreeMap.
        }

        return if (minDiffMinutes <= maxHoursDifference * 60) bestAdjustment else 0
    }

    private fun parseTimestampToInstant(timestamp: String): Instant? {
        return TimestampParser.parseFlexibleInstant(timestamp)
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
