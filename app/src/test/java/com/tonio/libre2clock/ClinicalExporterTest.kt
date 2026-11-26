package com.tonio.libre2clock

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.util.ClinicalExporter
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalExporterTest {

    @Test
    fun generateCsvIncludesHeaderAndSortedCgmInsulinCapillaryRows() {
        val glucose = listOf(
            GlucoseMeasurement(factoryTimestamp = "2025-01-01T12:00:00Z", timestamp = "2025-01-01T12:00:00Z", value = 145, calibratedValue = 140, trendArrow = 3)
        )
        val doses = listOf(
            InsulinDose(units = 4.5, timestamp = "2025-01-01T12:05:00Z", type = InsulinType.RAPID, carbs = 45.0),
            InsulinDose(units = 10.0, timestamp = "2025-01-01T12:10:00Z", type = InsulinType.RAPID, isDeleted = true)
        )
        val caps = listOf(
            CapillaryMeasurement(value = 138, timestamp = "2025-01-01T11:55:00Z", sensorValue = 142, sensorSerialNumber = "SN01")
        )

        val csv = ClinicalExporter.generateCsv(glucose, doses, caps)
        val lines = csv.trim().lines()

        assertTrue(lines.first().startsWith("timestamp_iso,record_type,"))
        assertTrue(lines.size == 4) // Header + 1 cap + 1 CGM + 1 insulin (deleted excluded)
        assertTrue(lines[1].contains(",CAPILLARY,138,142,,,,,SN:SN01"))
        assertTrue(lines[2].contains(",CGM,145,140,Flat,,,,"))
        assertTrue(lines[3].contains(",INSULIN,,,,4.50,RAPID,45.0,"))
    }

    @Test
    fun generateClinicalJsonIncludesCountsAndValidStructure() {
        val glucose = listOf(
            GlucoseMeasurement(factoryTimestamp = "2025-01-01T12:00:00Z", timestamp = "2025-01-01T12:00:00Z", value = 120, calibratedValue = 120)
        )
        val json = ClinicalExporter.generateClinicalJson(glucose, emptyList(), emptyList(), emptyList(), null)
        assertTrue(json.contains("\"glucoseCount\": 1"))
        assertTrue(json.contains("\"glucoseReadings\""))
    }
}
