package com.tonio.libre2clock

import com.tonio.libre2clock.data.api.DexcomService
import com.tonio.libre2clock.data.model.BolusTimeProfile
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.InsulinProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GlucoseAndInsulinProcessorTest {

    @Test
    fun dexcomTrendMapsToConsistentDisplayArrows() {
        assertEquals("↑", GlucoseProcessor.getTrendArrowSymbol(DexcomService.mapTrendToTrendArrow("DoubleUp")))
        assertEquals("↑", GlucoseProcessor.getTrendArrowSymbol(DexcomService.mapTrendToTrendArrow("SingleUp")))
        assertEquals("↗", GlucoseProcessor.getTrendArrowSymbol(DexcomService.mapTrendToTrendArrow("FortyFiveUp")))
        assertEquals("→", GlucoseProcessor.getTrendArrowSymbol(DexcomService.mapTrendToTrendArrow("Flat")))
        assertEquals("↘", GlucoseProcessor.getTrendArrowSymbol(DexcomService.mapTrendToTrendArrow("FortyFiveDown")))
        assertEquals("↓", GlucoseProcessor.getTrendArrowSymbol(DexcomService.mapTrendToTrendArrow("SingleDown")))
        assertEquals("↓", GlucoseProcessor.getTrendArrowSymbol(DexcomService.mapTrendToTrendArrow("DoubleDown")))
    }

    @Test
    fun glucoseProcessorAppliesManualAndRangeOffsets() {
        val measurement = GlucoseMeasurement(
            factoryTimestamp = "2025-01-01T12:00:00Z",
            timestamp = "2025-01-01T12:00:00Z",
            value = 150
        )
        val ranges = listOf(
            GlucoseOffsetRange(min = 0, max = 70, offset = 5, percentage = 0),
            GlucoseOffsetRange(min = 70, max = 180, offset = -10, percentage = 10),
            GlucoseOffsetRange(min = 180, max = null, offset = 0, percentage = 0)
        )

        val processed = GlucoseProcessor.process(
            measurement = measurement,
            manualOffset = 5,
            userRanges = ranges
        )

        // 150 + (-10 range offset) + (150 * 10% = 15) + 5 manual = 160
        assertEquals(160, processed.calibratedValue)
        assertEquals("150(160)", GlucoseProcessor.formatDualValue(processed.value, processed.calibratedValue))
    }

    @Test
    fun insulinProcessorRapidIobHandlesShortDurationWithoutNaN() {
        val doseTime = Instant.parse("2025-01-01T10:00:00Z")
        val dose = InsulinDose(
            units = 5.0,
            timestamp = doseTime.toString(),
            type = InsulinType.RAPID,
            durationMinutes = 185
        )

        val iobAtStart = InsulinProcessor.calculateIOB(dose, doseTime)
        val iobAt185 = InsulinProcessor.calculateIOB(dose, doseTime.plusSeconds(185 * 60L))
        val iobAt184 = InsulinProcessor.calculateIOB(
            dose.copy(durationMinutes = 184),
            doseTime.plusSeconds(184 * 60L)
        )

        assertEquals(5.0, iobAtStart, 0.001)
        assertEquals(0.0, iobAt185, 0.001)
        assertEquals(0.0, iobAt184, 0.001)
        assertFalse(iobAt184.isNaN())
        assertTrue(InsulinProcessor.calculateIOB(dose, doseTime.plusSeconds(60 * 60L)) > 0.0)
    }

    @Test
    fun insulinProcessorIgnoresDeletedDosesInTotalIob() {
        val now = Instant.parse("2025-01-01T12:00:00Z")
        val doses = listOf(
            InsulinDose(units = 4.0, timestamp = now.minusSeconds(30 * 60L).toString(), type = InsulinType.RAPID, durationMinutes = 240, isDeleted = false),
            InsulinDose(units = 10.0, timestamp = now.minusSeconds(30 * 60L).toString(), type = InsulinType.RAPID, durationMinutes = 240, isDeleted = true)
        )

        val totalIob = InsulinProcessor.calculateTotalIOB(doses, now)
        assertTrue(totalIob in 3.0..4.0)
    }

    @Test
    fun resolveBolusParamsAppliesDaytimeAndOvernightProfiles() {
        val profiles = listOf(
            BolusTimeProfile(
                name = "Mañana",
                startTime = "06:00",
                endTime = "12:00",
                isEnabled = true,
                icRatioOverride = 8.0,
                isfOverride = 35.0,
                targetGlucoseOverride = 95
            ),
            BolusTimeProfile(
                name = "Noche",
                startTime = "22:00",
                endTime = "06:00",
                isEnabled = true,
                icRatioOverride = null,
                isfOverride = 60.0,
                targetGlucoseOverride = 120
            ),
            BolusTimeProfile(
                name = "Desactivada",
                startTime = "12:00",
                endTime = "18:00",
                isEnabled = false,
                icRatioOverride = 5.0
            )
        )

        val morning = InsulinProcessor.resolveBolusParams(
            time = java.time.LocalTime.of(8, 30),
            tdi = 45.0,
            icConstant = 450,
            isfConstant = 1800,
            manualIsf = null,
            defaultTargetGlucose = 100,
            profiles = profiles
        )
        assertEquals(8.0, morning.icRatio, 0.001)
        assertEquals(35.0, morning.isf, 0.001)
        assertEquals(95, morning.targetGlucose)
        assertEquals("Mañana", morning.activeProfileName)

        // Overnight wrap-around at 01:00
        val night = InsulinProcessor.resolveBolusParams(
            time = java.time.LocalTime.of(1, 0),
            tdi = 45.0,
            icConstant = 450,
            isfConstant = 1800,
            manualIsf = null,
            defaultTargetGlucose = 100,
            profiles = profiles
        )
        assertEquals(10.0, night.icRatio, 0.001) // Falls back to 450 / 45 = 10.0
        assertEquals(60.0, night.isf, 0.001)
        assertEquals(120, night.targetGlucose)
        assertEquals("Noche", night.activeProfileName)

        // Disabled profile falls back to global rules
        val afternoon = InsulinProcessor.resolveBolusParams(
            time = java.time.LocalTime.of(14, 0),
            tdi = 45.0,
            icConstant = 450,
            isfConstant = 1800,
            manualIsf = null,
            defaultTargetGlucose = 100,
            profiles = profiles
        )
        assertEquals(10.0, afternoon.icRatio, 0.001)
        assertEquals(40.0, afternoon.isf, 0.001)
        assertEquals(100, afternoon.targetGlucose)
        assertEquals(null, afternoon.activeProfileName)
    }

    @Test
    fun suggestedBolusUsesIcRatioOverrideWhenProvided() {
        val breakdown = InsulinProcessor.getSuggestedBolusDetailed(
            carbs = 40.0,
            currentGlucose = 170,
            targetGlucose = 100,
            tdi = 45.0,
            icConstant = 450, // Would be 10.0 g/U -> 4.0 U
            isf = 35.0,
            isBasalExpiringSoon = false,
            icRatioOverride = 8.0 // 40 / 8 = 5.0 U + (170 - 100)/35 = 2.0 U => 7.0 U
        )
        assertEquals(5.0, breakdown.carbDose, 0.001)
        assertEquals(2.0, breakdown.correctionDose, 0.001)
        assertEquals(7.0, breakdown.total, 0.001)
    }
}
