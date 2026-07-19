package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.api.LibreService
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.LoginRequest
import com.tonio.libre2clock.data.model.SensorStatus
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class GlucoseRepositoryImpl(
    private val preferenceManager: PreferenceManager
) : GlucoseRepository {

    override val currentGlucose: Flow<GlucoseMeasurement?> = preferenceManager.historicalGlucoseArchive
        .map { it.firstOrNull() }

    override val historicalGlucose: Flow<List<GlucoseMeasurement>> = preferenceManager.historicalGlucoseArchive

    private val _sensorStatus = MutableStateFlow<SensorStatus?>(null)
    override val sensorStatus: Flow<SensorStatus?> = _sensorStatus.asStateFlow()

    private var patientId: String? = null
    private var isDemoMode = false

    override fun enableDemoMode() {
        isDemoMode = true
        _sensorStatus.value = SensorStatus(
            daysRemaining = "12d 0h remaining",
            startDate = "Started: Mon, Nov 03, 2025 10:30",
            expiryDate = "Expires: Mon, Nov 17, 2025 10:30",
            serialNumber = "DEMO-12345"
        )
    }

    override fun disableDemoMode() {
        isDemoMode = false
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            var response = LibreService.api.login(LoginRequest(email, password))
            var data = response.data

            // Handle regional redirect
            if (data?.redirect == true && data.region != null) {
                LibreService.updateRegion(data.region)
                response = LibreService.api.login(LoginRequest(email, password))
                data = response.data
            }

            if (response.status == 0 && data != null && data.authTicket != null && data.user != null) {
                val token = data.authTicket.token
                val userId = data.user.id
                LibreService.setAuth(token, userId)
                preferenceManager.saveAuth(token, userId)
                isDemoMode = false
                Result.success(Unit)
            } else {
                Result.failure(Exception("Login failed with status ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchLatestGlucose(): Result<GlucoseMeasurement> {
        return fetchLatestGlucoseInternal(persistArchive = true)
    }

    override suspend fun refreshHistoricalGlucoseWindow(): Result<GlucoseMeasurement> {
        return fetchLatestGlucoseInternal(persistArchive = true)
    }

    private suspend fun fetchLatestGlucoseInternal(persistArchive: Boolean): Result<GlucoseMeasurement> {
        if (isDemoMode) {
            val now = Instant.now()
            val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
            
            // For Demo mode, we still need to merge into PreferenceManager
            val currentHistory = preferenceManager.historicalGlucoseArchive.first()
            
            val demoValue = (100..180).random()
            val measurement = GlucoseMeasurement(
                factoryTimestamp = now.toString(),
                timestamp = formatter.format(now.atZone(ZoneId.systemDefault())),
                type = 1,
                valueInMgPerDl = demoValue,
                trendArrow = (1..5).random(),
                measurementColor = 1,
                value = demoValue
            )
            
            val mergedHistory = mergeAndPruneHistory(
                existing = currentHistory,
                incoming = listOf(measurement)
            )
            
            if (persistArchive) {
                preferenceManager.saveHistoricalGlucoseArchive(mergedHistory)
            }
            
            return Result.success(measurement)
        }

        return try {
            if (patientId == null) {
                patientId = preferenceManager.patientId.first()
            }
            
            if (patientId == null) {
                val connectionsResponse = LibreService.api.getConnections()
                val id = connectionsResponse.data?.firstOrNull()?.patientId
                if (id != null) {
                    patientId = id
                    preferenceManager.savePatientId(id)
                }
            }

            val id = patientId ?: return Result.failure(Exception("No patient found"))
            val response = LibreService.api.getGlucoseGraph(id)
            
            val measurement = response.data?.connection?.glucoseMeasurement
            val activeSensors = response.data?.activeSensors
            
            if (activeSensors != null && activeSensors.isNotEmpty()) {
                val sensor = activeSensors[0].sensor
                val activationTime = if (sensor.activationTimestamp > 10_000_000_000L) 
                    sensor.activationTimestamp / 1000 
                else 
                    sensor.activationTimestamp
                    
                val expiryTime = activationTime + (14 * 24 * 60 * 60)
                val now = Instant.now().epochSecond
                val remainingSeconds = expiryTime - now
                
                val days = (remainingSeconds / (24 * 60 * 60)).toInt()
                val hours = ((remainingSeconds % (24 * 60 * 60)) / 3600).toInt()

                val remainingStr = when {
                    remainingSeconds <= 0 -> "Expired"
                    days > 0 -> "${days}d ${hours}h remaining"
                    else -> "${hours}h remaining"
                }
                
                val formatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy HH:mm", Locale.US)
                    .withZone(ZoneId.systemDefault())
                val startDateStr = formatter.format(Instant.ofEpochSecond(activationTime))
                val expiryDateStr = formatter.format(Instant.ofEpochSecond(expiryTime))
                
                _sensorStatus.value = SensorStatus(
                    daysRemaining = remainingStr,
                    startDate = "Started: $startDateStr",
                    expiryDate = "Expires: $expiryDateStr",
                    serialNumber = sensor.serialNumber
                )
            }

            val historicalMeasurements = response.data?.graphData ?: emptyList()
            val incomingList = if (measurement != null) {
                historicalMeasurements + measurement
            } else {
                historicalMeasurements
            }

            val currentHistory = preferenceManager.historicalGlucoseArchive.first()
            val mergedHistory = mergeAndPruneHistory(
                existing = currentHistory,
                incoming = incomingList
            )
            
            if (persistArchive) {
                preferenceManager.saveHistoricalGlucoseArchive(mergedHistory)
            }

            val resultMeasurement = measurement ?: historicalMeasurements.lastOrNull()
            if (resultMeasurement != null) {
                Result.success(resultMeasurement)
            } else {
                Result.failure(Exception("No glucose data found in response"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun initialize() {
        val token = preferenceManager.authToken.first()
        val userId = preferenceManager.userId.first()
        
        // Initial sync of backup if needed (logic already in PreferenceManager)
        preferenceManager.requestHistoryCloudBackupIfDue()

        if (token != null && userId != null) {
            LibreService.setAuth(token, userId)
        }
    }

    private suspend fun mergeAndPruneHistory(
        existing: List<GlucoseMeasurement>,
        incoming: List<GlucoseMeasurement>
    ): List<GlucoseMeasurement> {
        val mergedMap = LinkedHashMap<String, GlucoseMeasurement>()
        (existing + incoming).forEach { m ->
            val instant = parseMeasurementInstant(m)
            val key = if (instant != null) {
                "${instant.toEpochMilli()}-${m.value}"
            } else {
                "${m.timestamp}-${m.value}"
            }
            mergedMap[key] = m
        }

        val retentionDays = preferenceManager.historyRetentionDays.first().toLong()
        val cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L)
        
        return mergedMap.values
            .mapNotNull { m ->
                parseMeasurementInstant(m)?.let { instant -> instant to m }
            }
            .filter { (instant, _) -> !instant.isBefore(cutoff) }
            .sortedByDescending { it.first } // Newest first
            .map { it.second }
    }

    private fun parseMeasurementInstant(measurement: GlucoseMeasurement): Instant? {
        return parseFlexibleInstant(measurement.factoryTimestamp)
            ?: parseFlexibleInstant(measurement.timestamp)
    }

    private fun parseFlexibleInstant(timestamp: String): Instant? {
        return TimestampParser.parseFlexibleInstant(timestamp)
    }
}
