package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
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
        autoRangeOffsetMode: AutoRangeOffsetMode = AutoRangeOffsetMode.OFF,
        capillaryReadings: List<CapillaryMeasurement> = emptyList(),
        context: CalculationContext? = null
    ): GlucoseMeasurement {
        val rawValue = measurement.value

        val calibratedValue = getCalibratedValue(
            rawValue = rawValue,
            manualOffset = manualOffset,
            userRanges = userRanges,
            autoAdjustEnabled = autoAdjustEnabled,
            autoRangeOffsetMode = autoRangeOffsetMode,
            capillaryReadings = capillaryReadings,
            measurementTimestamp = measurement.timestamp,
            context = context
        )

        return measurement.copy(
            calibratedValue = calibratedValue
        )
    }

    /**
     * Holds pre-calculated data to avoid redundant loops over large lists.
     */
    data class CalculationContext(
        val globalEstimate: RangeOffsetEstimate?,
        val rangeEstimates: Map<GlucoseOffsetRange, RangeOffsetEstimate>,
        val capillariesByTimestamp: List<Pair<Instant, CapillaryMeasurement>>
    )

    fun buildContext(
        autoRangeOffsetMode: AutoRangeOffsetMode,
        userRanges: List<GlucoseOffsetRange>,
        capillaryReadings: List<CapillaryMeasurement>
    ): CalculationContext {
        val globalEstimate = if (autoRangeOffsetMode == AutoRangeOffsetMode.GLOBAL) {
            estimateGlobalOffsets(capillaryReadings)
        } else null

        val rangeEstimates = if (autoRangeOffsetMode == AutoRangeOffsetMode.BY_RANGE) {
            userRanges.associateWith { range ->
                estimateOffsetsForRange(range, capillaryReadings)
            }.filterValues { it != null } as Map<GlucoseOffsetRange, RangeOffsetEstimate>
        } else emptyMap()

        val capsByTime = capillaryReadings.mapNotNull { r ->
            parseTimestampToInstant(r.timestamp)?.let { it to r }
        }.sortedByDescending { it.first }

        return CalculationContext(globalEstimate, rangeEstimates, capsByTime)
    }

    /**
     * Calculates the calibrated value based on raw value and offsets.
     */
    fun getCalibratedValue(
        rawValue: Int,
        manualOffset: Int = 0,
        userRanges: List<GlucoseOffsetRange> = emptyList(),
        autoAdjustEnabled: Boolean = false,
        autoRangeOffsetMode: AutoRangeOffsetMode = AutoRangeOffsetMode.OFF,
        capillaryReadings: List<CapillaryMeasurement> = emptyList(),
        measurementTimestamp: String? = null,
        context: CalculationContext? = null
    ): Int {
        val matchingRange = userRanges.find { range ->
            rawValue >= range.min && (range.max == null || rawValue < range.max)
        }

        val selectedEstimate = when (autoRangeOffsetMode) {
            AutoRangeOffsetMode.OFF -> null
            AutoRangeOffsetMode.GLOBAL -> context?.globalEstimate ?: estimateGlobalOffsets(capillaryReadings)
            AutoRangeOffsetMode.BY_RANGE -> {
                if (context != null && matchingRange != null) {
                    context.rangeEstimates[matchingRange]
                } else {
                    matchingRange?.let { estimateOffsetsForRange(it, capillaryReadings) }
                }
            }
        }

        val rangeFixedOffset = if (selectedEstimate != null) {
            selectedEstimate.offset
        } else {
            matchingRange?.offset ?: 0
        }
        val rangePercentageOffset = if (selectedEstimate != null) {
            (rawValue * (selectedEstimate.percentage / 100.0)).roundToInt()
        } else {
            matchingRange?.let { range ->
                (rawValue * (range.percentage / 100.0)).roundToInt()
            } ?: 0
        }
        val autoAdjustment = if (autoAdjustEnabled) {
            getAutoAdjustment(rawValue, measurementTimestamp, capillaryReadings, context = context)
        } else {
            0
        }

        return rawValue + rangeFixedOffset + rangePercentageOffset + manualOffset + autoAdjustment
    }

    fun getAutoAdjustment(
        rawValue: Int,
        measurementTimestamp: String?,
        capillaryReadings: List<CapillaryMeasurement>,
        maxHoursDifference: Long = 6,
        context: CalculationContext? = null
    ): Int {
        val capsToUse = context?.capillariesByTimestamp ?: emptyList()
        if (capsToUse.isEmpty() && capillaryReadings.isEmpty()) return 0

        val measurementInstant = measurementTimestamp?.let(::parseTimestampToInstant) ?: return 0
        
        var bestAdjustment = 0
        var minDiffMinutes = Long.MAX_VALUE

        if (context != null) {
            // Optimized path using pre-parsed timestamps
            for ((readingInstant, reading) in context.capillariesByTimestamp) {
                val diffMinutes = abs(Duration.between(measurementInstant, readingInstant).toMinutes())
                if (diffMinutes < minDiffMinutes) {
                    minDiffMinutes = diffMinutes
                    bestAdjustment = reading.delta
                        ?: reading.sensorValue?.let { reading.value - it }
                        ?: (reading.value - rawValue)
                }
                // Stop early if we start moving away in time (assuming sorted list)
                if (diffMinutes > minDiffMinutes + 60) break 
            }
        } else {
            for (reading in capillaryReadings) {
                val readingInstant = parseTimestampToInstant(reading.timestamp) ?: continue
                val diffMinutes = abs(Duration.between(measurementInstant, readingInstant).toMinutes())
                if (diffMinutes < minDiffMinutes) {
                    minDiffMinutes = diffMinutes
                    bestAdjustment = reading.delta
                        ?: reading.sensorValue?.let { reading.value - it }
                        ?: (reading.value - rawValue)
                }
            }
        }

        return if (minDiffMinutes <= maxHoursDifference * 60) bestAdjustment else 0
    }

    data class RangeOffsetEstimate(
        val offset: Int,
        val percentage: Int,
        val sampleCount: Int
    )

    fun estimateOffsetsForRange(
        range: GlucoseOffsetRange,
        capillaryReadings: List<CapillaryMeasurement>
    ): RangeOffsetEstimate? {
        return estimateOffsetsInternal(capillaryReadings) { sensor ->
            sensor >= range.min && (range.max == null || sensor < range.max)
        }
    }

    fun estimateGlobalOffsets(
        capillaryReadings: List<CapillaryMeasurement>
    ): RangeOffsetEstimate? {
        return estimateOffsetsInternal(capillaryReadings) { true }
    }

    private fun estimateOffsetsInternal(
        capillaryReadings: List<CapillaryMeasurement>,
        sensorFilter: (Int) -> Boolean
    ): RangeOffsetEstimate? {
        val points = capillaryReadings.mapNotNull { reading ->
            val sensor = reading.sensorValue ?: return@mapNotNull null
            if (sensor == 0) return@mapNotNull null
            if (!sensorFilter(sensor)) return@mapNotNull null
            sensor.toDouble() to (reading.value - sensor).toDouble()
        }

        if (points.isEmpty()) return null
        if (points.size == 1) {
            val delta = points.first().second.roundToInt()
            return RangeOffsetEstimate(offset = delta, percentage = 0, sampleCount = 1)
        }

        val meanX = points.map { it.first }.average()
        val meanY = points.map { it.second }.average()

        var varianceX = 0.0
        var covariance = 0.0
        points.forEach { (x, y) ->
            val dx = x - meanX
            varianceX += dx * dx
            covariance += dx * (y - meanY)
        }

        val slope = if (varianceX > 1e-9) covariance / varianceX else 0.0
        val intercept = meanY - slope * meanX

        return RangeOffsetEstimate(
            offset = intercept.roundToInt(),
            percentage = (slope * 100.0).roundToInt(),
            sampleCount = points.size
        )
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
