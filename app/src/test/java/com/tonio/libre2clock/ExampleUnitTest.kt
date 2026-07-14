package com.tonio.libre2clock

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.repository.GlucoseProcessor
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
}