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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class GlucoseRepositoryImpl(
    private val preferenceManager: PreferenceManager
) : GlucoseRepository {

    private val _currentGlucose = MutableStateFlow<GlucoseMeasurement?>(null)
    override val currentGlucose: Flow<GlucoseMeasurement?> = _currentGlucose.asStateFlow()

    private val _historicalGlucose = MutableStateFlow<List<GlucoseMeasurement>>(emptyList())
    override val historicalGlucose: Flow<List<GlucoseMeasurement>> = _historicalGlucose.asStateFlow()

    private val _sensorStatus = MutableStateFlow<SensorStatus?>(null)
    override val sensorStatus: Flow<SensorStatus?> = _sensorStatus.asStateFlow()

    private var patientId: String? = null
    private var isDemoMode = false

    override fun enableDemoMode() {
        isDemoMode = true
        _sensorStatus.value = SensorStatus(
            daysRemaining = 12,
            startDate = "Started: Mon, Nov 03, 2025 10:30",
            expiryDate = "Expires: Sat, Nov 15, 2025 10:30",
            serialNumber = "DEMO-12345"
        )
    }

    init {
        // Initialize LibreService with stored credentials if available
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
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            
            val manualOffset = preferenceManager.glucoseOffset.first()
            val userRanges = preferenceManager.glucoseOffsetRanges.first()
            val autoAdjustEnabled = preferenceManager.autoAdjustEnabled.first()
            val capillaryReadings = preferenceManager.capillaryReadings.first()

            // Generate initial historical data if empty
            if (_historicalGlucose.value.isEmpty()) {
                val mockHistory = (0 until 48).map { i ->
                    val time = now.minusSeconds((i * 15 * 60).toLong())
                    val rawValue = (100..180).random()
                    val measurement = GlucoseMeasurement(
                        factoryTimestamp = time.toString(),
                        timestamp = formatter.format(time.atZone(ZoneId.systemDefault())),
                        type = 0,
                        valueInMgPerDl = rawValue,
                        trendArrow = 3,
                        measurementColor = 1,
                        value = rawValue
                    )
                    GlucoseProcessor.process(
                        measurement,
                        manualOffset,
                        userRanges,
                        autoAdjustEnabled,
                        capillaryReadings
                    )
                }.reversed()
                _historicalGlucose.value = mockHistory
            }

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
            val processed = GlucoseProcessor.process(
                measurement,
                manualOffset,
                userRanges,
                autoAdjustEnabled,
                capillaryReadings
            )
            _currentGlucose.value = processed
            
            // Add new reading to history
            val mergedHistory = mergeAndPruneHistory(
                existing = _historicalGlucose.value,
                incoming = listOf(processed)
            )
            _historicalGlucose.value = mergedHistory
            if (persistArchive) {
                preferenceManager.saveHistoricalGlucoseArchive(mergedHistory)
            }
            
            return Result.success(processed)
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
            
            // Log for debugging if needed (System.out for now)
            println("Libre2Clock: Graph Response status=${response.status} items=${response.data?.graphData?.size}")

            val measurement = response.data?.connection?.glucoseMeasurement
            val activeSensors = response.data?.activeSensors
            
            if (activeSensors != null && activeSensors.isNotEmpty()) {
                val sensor = activeSensors[0].sensor
                val activationTime = sensor.activationTimestamp
                val expiryTime = activationTime + (14 * 24 * 60 * 60)
                val now = Instant.now().epochSecond
                val remainingSeconds = expiryTime - now
                val daysRemaining = (remainingSeconds / (24 * 60 * 60)).toInt()
                
                val formatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy HH:mm", Locale.US)
                    .withZone(ZoneId.systemDefault())
                val startDateStr = formatter.format(Instant.ofEpochSecond(activationTime))
                val expiryDateStr = formatter.format(Instant.ofEpochSecond(expiryTime))
                
                _sensorStatus.value = SensorStatus(
                    daysRemaining = daysRemaining.coerceAtLeast(0),
                    startDate = "Started: $startDateStr",
                    expiryDate = "Expires: $expiryDateStr",
                    serialNumber = sensor.serialNumber
                )
            }

            val historicalMeasurements = response.data?.graphData ?: emptyList()

            // Merge everything as RAW data
            val incomingList = if (measurement != null) {
                _currentGlucose.value = measurement
                historicalMeasurements + measurement
            } else {
                historicalMeasurements
            }

            val mergedHistory = mergeAndPruneHistory(
                existing = _historicalGlucose.value,
                incoming = incomingList
            )
            _historicalGlucose.value = mergedHistory
            
            if (persistArchive) {
                preferenceManager.saveHistoricalGlucoseArchive(mergedHistory)
            }

            if (measurement != null) {
                Result.success(measurement)
            } else if (historicalMeasurements.isNotEmpty()) {
                Result.success(historicalMeasurements.last())
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
        val archivedHistory = preferenceManager.historicalGlucoseArchive.first()
        val backupPayload = preferenceManager.loadHistoryBackupPayload()
        if (archivedHistory.isNotEmpty()) {
            _historicalGlucose.value = mergeAndPruneHistory(emptyList(), archivedHistory)
        } else if (backupPayload?.historicalGlucoseArchive?.isNotEmpty() == true) {
            val restoredHistory = mergeAndPruneHistory(emptyList(), backupPayload.historicalGlucoseArchive)
            _historicalGlucose.value = restoredHistory
            preferenceManager.saveHistoricalGlucoseArchive(restoredHistory)
        }

        val capillaryReadings = preferenceManager.capillaryReadings.first()
        if (capillaryReadings.isEmpty() && backupPayload?.capillaryReadings?.isNotEmpty() == true) {
            preferenceManager.saveCapillaryReadings(backupPayload.capillaryReadings)
        }

        preferenceManager.requestHistoryCloudBackupIfDue()

        if (token != null && userId != null) {
            LibreService.setAuth(token, userId)
        }
    }

    private suspend fun mergeAndPruneHistory(
        existing: List<GlucoseMeasurement>,
        incoming: List<GlucoseMeasurement>
    ): List<GlucoseMeasurement> {
        if (existing.isEmpty() && incoming.isEmpty()) return emptyList()

        val mergedByKey = LinkedHashMap<String, GlucoseMeasurement>()
        (existing + incoming).forEach { measurement ->
            measurementKey(measurement)?.let { key ->
                mergedByKey[key] = measurement
            }
        }

        val retentionDays = preferenceManager.historyRetentionDays.first().toLong()
        val cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L)
        return mergedByKey.values
            .mapNotNull { measurement ->
                parseMeasurementInstant(measurement)?.let { instant -> instant to measurement }
            }
            .filter { (instant, _) -> !instant.isBefore(cutoff) }
            .sortedBy { (instant, _) -> instant }
            .map { it.second }
    }

    private fun measurementKey(measurement: GlucoseMeasurement): String? {
        val instant = parseMeasurementInstant(measurement) ?: return null
        return "${instant.toEpochMilli()}-${measurement.value}-${measurement.type}"
    }

    private fun parseMeasurementInstant(measurement: GlucoseMeasurement): Instant? {
        return parseFlexibleInstant(measurement.factoryTimestamp)
            ?: parseFlexibleInstant(measurement.timestamp)
    }

    private fun parseFlexibleInstant(timestamp: String): Instant? {
        return TimestampParser.parseFlexibleInstant(timestamp)
    }
}
