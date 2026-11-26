package com.tonio.libre2clock

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.util.SensorQualityTier
import com.tonio.libre2clock.util.buildSensorErrorSummary
import com.tonio.libre2clock.util.detectCompressionLows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SensorQualityTest {

    @Test
    fun buildSensorErrorSummaryCalculatesClinicalMardAndTier() {
        val logs = listOf(SensorLog(serialNumber = "SN123", startDate = "2025-01-01 10:00", expiryDate = "2025-01-15 10:00"))
        // Capillary=100, Sensor=92 -> |92-100|/100 = 8% (EXCELLENT)
        // Capillary=200, Sensor=184 -> |184-200|/200 = 8% (EXCELLENT)
        val caps = listOf(
            CapillaryMeasurement(value = 100, timestamp = "2025-01-02T10:00:00Z", sensorValue = 92, sensorSerialNumber = "SN123"),
            CapillaryMeasurement(value = 200, timestamp = "2025-01-03T10:00:00Z", sensorValue = 184, sensorSerialNumber = "SN123")
        )

        val summary = buildSensorErrorSummary(logs, caps).first()
        assertEquals(2, summary.samples)
        assertEquals(8.0, summary.mardPct, 0.001)
        assertEquals(SensorQualityTier.EXCELLENT, summary.qualityTier)
    }

    @Test
    fun detectCompressionLowsIdentifiesSharpVShapedPressureDrop() {
        val base = Instant.parse("2025-01-01T03:00:00Z")
        val readings = listOf(
            GlucoseMeasurement(factoryTimestamp = base.toString(), timestamp = base.toString(), value = 115, epochSeconds = base.epochSecond),
            GlucoseMeasurement(factoryTimestamp = base.plusSeconds(10 * 60L).toString(), timestamp = base.plusSeconds(10 * 60L).toString(), value = 58, epochSeconds = base.plusSeconds(10 * 60L).epochSecond),
            GlucoseMeasurement(factoryTimestamp = base.plusSeconds(20 * 60L).toString(), timestamp = base.plusSeconds(20 * 60L).toString(), value = 112, epochSeconds = base.plusSeconds(20 * 60L).epochSecond)
        )

        val events = detectCompressionLows(readings)
        assertEquals(1, events.size)
        assertEquals(58, events.first().nadirMgDl)
        assertTrue(events.first().dropRateMgPerMin <= -2.5)
        assertTrue(events.first().recoveryRateMgPerMin >= 2.5)
    }
}
