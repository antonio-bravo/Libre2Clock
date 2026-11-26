package com.tonio.libre2clock

import com.tonio.libre2clock.util.ProcessedGlucose
import com.tonio.libre2clock.util.ReportCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ReportCalculatorTest {

    @Test
    fun griFormulaAndZonesMatchClinicalDefinition() {
        // VLow=2%, Low=4%, VHigh=5%, High=20%
        // Hypo = 2 + 0.8*4 = 5.2; Hyper = 5 + 0.5*20 = 15.0
        // GRI = 3.0*5.2 + 1.6*15.0 = 15.6 + 24.0 = 39.6 -> Zone B
        val gri = ReportCalculator.calculateGri(
            tbrVLowPct = 2.0,
            tbrLowPct = 4.0,
            tarVHighPct = 5.0,
            tarHighPct = 20.0
        )
        assertEquals(39.6, gri, 0.001)
        assertEquals("B", ReportCalculator.getGriZone(gri))
        assertEquals("A", ReportCalculator.getGriZone(15.0))
        assertEquals("E", ReportCalculator.getGriZone(90.0))
    }

    @Test
    fun glycemicEpisodesDetectsHypoAndHyperWithMinDuration() {
        val base = Instant.parse("2025-01-01T02:00:00Z")
        val readings = listOf(
            // Hypo L1 + L2 episode from 02:00 to 02:30 (30 min, with >=15 min <54)
            ProcessedGlucose(base, 65.0, 65.0),
            ProcessedGlucose(base.plusSeconds(15 * 60L), 50.0, 50.0),
            ProcessedGlucose(base.plusSeconds(30 * 60L), 52.0, 52.0),
            // Back in range
            ProcessedGlucose(base.plusSeconds(45 * 60L), 110.0, 110.0),
            // Single transient hyper reading (should NOT count as >=15 min episode)
            ProcessedGlucose(base.plusSeconds(60 * 60L), 195.0, 195.0),
            ProcessedGlucose(base.plusSeconds(65 * 60L), 140.0, 140.0),
            // Sustained hyper >180 and >250 from 04:00 to 04:30 (30 min)
            ProcessedGlucose(base.plusSeconds(120 * 60L), 210.0, 210.0),
            ProcessedGlucose(base.plusSeconds(135 * 60L), 265.0, 265.0),
            ProcessedGlucose(base.plusSeconds(150 * 60L), 260.0, 260.0)
        )

        val summary = ReportCalculator.calculateGlycemicEpisodes(readings, useOffset = true, zone = ZoneOffset.UTC)
        assertEquals(1, summary.hypoLevel1Count)
        assertEquals(30.0, summary.hypoLevel1AvgMinutes, 0.001)
        assertEquals(1, summary.hypoLevel2Count)
        assertEquals(1, summary.hyperCount)
        assertEquals(1, summary.hyperSevereCount)
        assertEquals(2, summary.mostFrequentHypoHour)
    }
}
