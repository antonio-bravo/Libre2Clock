package com.tonio.libre2clock.util

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Mass
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.InsulinDose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object HealthConnectManager {

    private const val TAG = "HealthConnectManager"

    val REQUIRED_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    fun isAvailable(context: Context): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context.applicationContext) == HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    suspend fun hasAllPermissions(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext false
        return@withContext try {
            val client = HealthConnectClient.getOrCreate(context.applicationContext)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(REQUIRED_PERMISSIONS)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncGlucoseReadings(
        context: Context,
        measurements: List<GlucoseMeasurement>,
        useCalibrated: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) {
            return@withContext Result.failure(IllegalStateException("Health Connect no está disponible en este dispositivo"))
        }
        return@withContext try {
            val client = HealthConnectClient.getOrCreate(context.applicationContext)
            val records = buildBloodGlucoseRecords(measurements, useCalibrated)
            if (records.isEmpty()) return@withContext Result.success(0)

            records.chunked(500).forEach { batch ->
                client.insertRecords(batch)
            }
            Result.success(records.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing glucose to Health Connect", e)
            Result.failure(e)
        }
    }

    suspend fun syncCarbsFromDoses(
        context: Context,
        doses: List<InsulinDose>
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) {
            return@withContext Result.failure(IllegalStateException("Health Connect no está disponible en este dispositivo"))
        }
        return@withContext try {
            val client = HealthConnectClient.getOrCreate(context.applicationContext)
            val records = buildNutritionRecords(doses)
            if (records.isEmpty()) return@withContext Result.success(0)

            records.chunked(500).forEach { batch ->
                client.insertRecords(batch)
            }
            Result.success(records.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing carbs to Health Connect", e)
            Result.failure(e)
        }
    }

    suspend fun readRecentSteps(context: Context, hours: Long = 24): Result<Long> = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) {
            return@withContext Result.failure(IllegalStateException("Health Connect no está disponible"))
        }
        return@withContext try {
            val client = HealthConnectClient.getOrCreate(context.applicationContext)
            val end = Instant.now()
            val start = end.minusSeconds(hours * 3600L)
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
            Result.success(steps)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading steps from Health Connect", e)
            Result.failure(e)
        }
    }

    internal fun buildBloodGlucoseRecords(
        measurements: List<GlucoseMeasurement>,
        useCalibrated: Boolean
    ): List<BloodGlucoseRecord> {
        val zone = ZoneId.systemDefault()
        return measurements.mapNotNull { m ->
            val instant = TimestampParser.parseMeasurementInstant(m) ?: return@mapNotNull null
            val rawVal = m.value.takeIf { it > 0 } ?: m.valueInMgPerDl
            val valueMgDl = if (useCalibrated && m.calibratedValue > 0) m.calibratedValue else rawVal
            if (valueMgDl <= 0) return@mapNotNull null
            val offset: ZoneOffset = zone.rules.getOffset(instant)
            BloodGlucoseRecord(
                time = instant,
                zoneOffset = offset,
                level = BloodGlucose.milligramsPerDeciliter(valueMgDl.toDouble()),
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                metadata = Metadata.manualEntry(clientRecordId = "libre2clock_cgm_${instant.toEpochMilli()}")
            )
        }
    }

    internal fun buildNutritionRecords(doses: List<InsulinDose>): List<NutritionRecord> {
        val zone = ZoneId.systemDefault()
        return doses.mapNotNull { dose ->
            if (dose.isDeleted) return@mapNotNull null
            val carbs = dose.carbs ?: return@mapNotNull null
            if (carbs <= 0.0) return@mapNotNull null
            val instant = TimestampParser.parseFlexibleInstant(dose.timestamp) ?: return@mapNotNull null
            val offset: ZoneOffset = zone.rules.getOffset(instant)
            NutritionRecord(
                startTime = instant,
                startZoneOffset = offset,
                endTime = instant.plusSeconds(60L),
                endZoneOffset = offset,
                totalCarbohydrate = Mass.grams(carbs),
                metadata = Metadata.manualEntry(clientRecordId = "libre2clock_carbs_${dose.id}")
            )
        }
    }
}
