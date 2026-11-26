package com.tonio.libre2clock

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.util.HealthConnectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectTest {

    @Test
    fun buildBloodGlucoseRecordsMapsValuesAndClientRecordIds() {
        val measurements = listOf(
            GlucoseMeasurement(factoryTimestamp = "2025-01-01T12:00:00Z", timestamp = "2025-01-01T12:00:00Z", value = 140, calibratedValue = 135),
            GlucoseMeasurement(factoryTimestamp = "2025-01-01T12:05:00Z", timestamp = "2025-01-01T12:05:00Z", value = 0, calibratedValue = 0)
        )

        val calRecords = HealthConnectManager.buildBloodGlucoseRecords(measurements, useCalibrated = true)
        assertEquals(1, calRecords.size)
        assertEquals(135.0, calRecords.first().level.inMilligramsPerDeciliter, 0.001)
        assertTrue(calRecords.first().metadata.clientRecordId!!.startsWith("libre2clock_cgm_"))

        val rawRecords = HealthConnectManager.buildBloodGlucoseRecords(measurements, useCalibrated = false)
        assertEquals(140.0, rawRecords.first().level.inMilligramsPerDeciliter, 0.001)
    }

    @Test
    fun buildNutritionRecordsMapsOnlyNonDeletedDosesWithCarbs() {
        val doses = listOf(
            InsulinDose(id = "d1", units = 4.0, timestamp = "2025-01-01T12:00:00Z", type = InsulinType.RAPID, carbs = 45.0),
            InsulinDose(id = "d2", units = 2.0, timestamp = "2025-01-01T14:00:00Z", type = InsulinType.RAPID, carbs = null),
            InsulinDose(id = "d3", units = 3.0, timestamp = "2025-01-01T15:00:00Z", type = InsulinType.RAPID, carbs = 30.0, isDeleted = true)
        )

        val records = HealthConnectManager.buildNutritionRecords(doses)
        assertEquals(1, records.size)
        assertEquals(45.0, records.first().totalCarbohydrate!!.inGrams, 0.001)
        assertEquals("libre2clock_carbs_d1", records.first().metadata.clientRecordId)
    }
}
