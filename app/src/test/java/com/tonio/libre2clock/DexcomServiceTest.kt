package com.tonio.libre2clock

import com.tonio.libre2clock.data.api.DexcomService
import com.tonio.libre2clock.data.model.DexcomGlucoseReading
import com.tonio.libre2clock.data.model.DexcomServerRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DexcomServiceTest {

    @Test
    fun mapTrendToTrendArrowMapsAllDexcomTrendsCorrectly() {
        assertEquals(1, DexcomService.mapTrendToTrendArrow("DoubleUp"))
        assertEquals(2, DexcomService.mapTrendToTrendArrow("SingleUp"))
        assertEquals(3, DexcomService.mapTrendToTrendArrow("FortyFiveUp"))
        assertEquals(4, DexcomService.mapTrendToTrendArrow("Flat"))
        assertEquals(5, DexcomService.mapTrendToTrendArrow("FortyFiveDown"))
        assertEquals(6, DexcomService.mapTrendToTrendArrow("SingleDown"))
        assertEquals(7, DexcomService.mapTrendToTrendArrow("DoubleDown"))
        assertEquals(0, DexcomService.mapTrendToTrendArrow("None"))
        assertEquals(0, DexcomService.mapTrendToTrendArrow(null))
    }

    @Test
    fun extractEpochMsParsesDateStringPattern() {
        val reading = DexcomGlucoseReading(
            wt = "/Date(1711234567000)/",
            value = 125,
            trend = "Flat"
        )
        val epochMs = DexcomService.extractEpochMs(reading)
        assertEquals(1711234567000L, epochMs)
    }

    @Test
    fun convertReadingToMeasurementProducesValidGlucoseMeasurement() {
        val reading = DexcomGlucoseReading(
            wt = "Date(1711234567000-0000)",
            value = 135,
            trend = "SingleUp"
        )

        val measurement = DexcomService.convertReadingToMeasurement(reading)
        assertNotNull(measurement)
        assertEquals(135, measurement?.value)
        assertEquals(135, measurement?.valueInMgPerDl)
        assertEquals(2, measurement?.trendArrow)
        assertEquals(1711234567L, measurement?.epochSeconds)
    }

    @Test
    fun dexcomServerRegionsHaveCorrectBaseUrls() {
        assertEquals("https://shareous1.dexcom.com/ShareWebServices/Services/", DexcomServerRegion.OUS.baseUrl)
        assertEquals("https://share2.dexcom.com/ShareWebServices/Services/", DexcomServerRegion.US.baseUrl)
    }
}
