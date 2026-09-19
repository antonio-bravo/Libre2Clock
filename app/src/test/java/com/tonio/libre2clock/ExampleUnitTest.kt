package com.tonio.libre2clock

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.InsulinProcessor
import com.tonio.libre2clock.util.buildSensorErrorSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun autoAdjustUsesStoredCapillaryReadings() {
        val measurement = GlucoseMeasurement(
            factoryTimestamp = "",
            timestamp = "2024-01-01T13:00:00",
            type = 0,
            valueInMgPerDl = 90,
            trendArrow = 3,
            measurementColor = null,
            value = 90
        )
        val capillaryReadings = listOf(
            CapillaryMeasurement(value = 100, timestamp = "2024-01-01T12:00:00"),
            CapillaryMeasurement(value = 110, timestamp = "2024-01-01T13:00:00")
        )

        val calibrated = GlucoseProcessor.process(
            measurement = measurement,
            manualOffset = 0,
            userRanges = emptyList(),
            autoAdjustEnabled = true,
            capillaryReadings = capillaryReadings
        )

        assertEquals(110, calibrated.calibratedValue)
    }

    @Test
    fun calibratedValueAppliesFixedAndPercentageOffsets() {
        val calibrated = GlucoseProcessor.getCalibratedValue(
            rawValue = 100,
            manualOffset = 0,
            userRanges = listOf(
                GlucoseOffsetRange(min = 80, max = 130, offset = 10, percentage = 5)
            ),
            autoAdjustEnabled = false
        )

        assertEquals(115, calibrated)
    }

    @Test
    fun calibratedValueSupportsNegativeFixedAndPercentageOffsets() {
        val calibrated = GlucoseProcessor.getCalibratedValue(
            rawValue = 100,
            manualOffset = 0,
            userRanges = listOf(
                GlucoseOffsetRange(min = 80, max = 130, offset = -8, percentage = -12)
            ),
            autoAdjustEnabled = false
        )

        assertEquals(80, calibrated)
    }

    @Test
    fun sensorErrorSummaryAveragesPerSensor() {
        val capillaryReadings = listOf(
            CapillaryMeasurement(value = 120, timestamp = "2024-01-01T10:00", sensorValue = 100, sensorSerialNumber = "SN-001"),
            CapillaryMeasurement(value = 130, timestamp = "2024-01-01T11:00", sensorValue = 100, sensorSerialNumber = "SN-001"),
            CapillaryMeasurement(value = 150, timestamp = "2024-01-01T12:00", sensorValue = 120, sensorSerialNumber = "SN-002")
        )

        val summary = buildSensorErrorSummary(emptyList(), capillaryReadings)

        assertEquals(2, summary.size)
        val first = summary.first { it.serialNumber == "SN-001" }
        assertEquals(2, first.samples)
        assertEquals(25.0, first.avgAbsoluteDeviationPct, 0.1)
        assertEquals(25.0, first.avgSignedDeviationPct, 0.1)
    }

    @Test
    fun rangeEstimateForSpecificRangeIsCorrect() {
        val range = GlucoseOffsetRange(min = 80, max = 130)
        val capillaryReadings = listOf(
            CapillaryMeasurement(value = 110, timestamp = "2024-01-01T10:00", sensorValue = 100, sensorSerialNumber = "SN-001"),
            CapillaryMeasurement(value = 120, timestamp = "2024-01-01T11:00", sensorValue = 100, sensorSerialNumber = "SN-002")
        )

        val estimate = GlucoseProcessor.estimateOffsetsForRange(range, capillaryReadings)
        assertEquals(2, estimate?.sampleCount)
        assertEquals(false, estimate?.isSensorSpecific)
    }

    @Test
    fun rangeEstimatePrioritizesActiveSensorWhenAvailable() {
        val range = GlucoseOffsetRange(min = 80, max = 130)
        val capillaryReadings = listOf(
            CapillaryMeasurement(value = 110, timestamp = "2024-01-01T10:00", sensorValue = 100, sensorSerialNumber = "SN-001"), // delta +10
            CapillaryMeasurement(value = 130, timestamp = "2024-01-01T11:00", sensorValue = 100, sensorSerialNumber = "SN-002")  // delta +30
        )

        val estimateActive = GlucoseProcessor.estimateOffsetsForRange(range, capillaryReadings, activeSensorSn = "SN-002")
        assertEquals(1, estimateActive?.sampleCount)
        assertEquals(true, estimateActive?.isSensorSpecific)
        assertEquals(30, estimateActive?.offset)
    }

    @Test
    fun rangeEstimateFallsBackToRangeAverageWhenActiveSensorHasNoData() {
        val range = GlucoseOffsetRange(min = 80, max = 130)
        val capillaryReadings = listOf(
            CapillaryMeasurement(value = 110, timestamp = "2024-01-01T10:00", sensorValue = 100, sensorSerialNumber = "SN-001"),
            CapillaryMeasurement(value = 130, timestamp = "2024-01-01T11:00", sensorValue = 100, sensorSerialNumber = "SN-002")
        )

        val estimateFallback = GlucoseProcessor.estimateOffsetsForRange(range, capillaryReadings, activeSensorSn = "SN-003")
        assertEquals(2, estimateFallback?.sampleCount)
        assertEquals(false, estimateFallback?.isSensorSpecific)
        assertEquals(20, estimateFallback?.offset)
    }

    @Test
    fun predictiveHypoRiskDetectsImpendingLow() {
        val risk = InsulinProcessor.calculatePredictiveHypoRisk(
            currentGlucose = 90,
            trendArrow = 6,
            doses = emptyList(),
            isf = 45.0,
            hypoThreshold = 70
        )

        assertEquals(true, risk.isRisk)
        assertEquals(15, risk.minutesUntilHypo)
        assertEquals(60, risk.projectedValue)
    }
}
