package com.tonio.libre2clock.data.repository

import android.content.Context
import com.tonio.libre2clock.data.api.LibreService
import com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper
import com.tonio.libre2clock.data.model.ActiveSensorInfo
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.LoginRequest
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class GlucoseRepositoryImpl(
    context: Context,
    private val preferenceManager: PreferenceManager
) : GlucoseRepository {

    private val historyDb = GlucoseHistoryDatabaseHelper(context.applicationContext)
    private val credentialStore = SecureCredentialStore(context.applicationContext)
    private val historicalState = MutableStateFlow<List<GlucoseMeasurement>>(emptyList())
    private val _dataVersion = MutableStateFlow(0L)
    override val dataVersion: Flow<Long> = _dataVersion.asStateFlow()

    private val historicalWindowCache = object : LinkedHashMap<String, List<GlucoseMeasurement>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<GlucoseMeasurement>>): Boolean {
            return size > MAX_WINDOW_CACHE_ENTRIES
        }
    }

    override val currentGlucose: Flow<GlucoseMeasurement?> = historicalState
        .map { it.firstOrNull() }

    override val historicalGlucose: StateFlow<List<GlucoseMeasurement>> = historicalState.asStateFlow()

    override val activeSensorInfo: Flow<ActiveSensorInfo?> = combine(
        preferenceManager.activeSensorSerialNumber,
        preferenceManager.activeSensorStartTime
    ) { sn, start ->
        if (sn != null && start != null) ActiveSensorInfo(sn, start) else null
    }

    override val isDemoMode: Flow<Boolean> = preferenceManager.isDemoMode

    private var patientId: String? = null
    private var lastSnapshotMirrorEpochMs: Long = 0L

    override suspend fun enableDemoMode() {
        preferenceManager.saveDemoMode(true)
    }

    override suspend fun disableDemoMode() {
        preferenceManager.saveDemoMode(false)
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
                preferenceManager.saveDemoMode(false)
                preferenceManager.clearActiveSensorInfo()
                credentialStore.saveCredentials(email, password)
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

    override suspend fun logout() {
        patientId = null
        LibreService.clearAuth()
        preferenceManager.clearAuth()
        credentialStore.clearCredentials()
    }

    override suspend fun refreshHistoricalGlucoseWindow(): Result<GlucoseMeasurement> {
        return fetchLatestGlucoseInternal(persistArchive = true)
    }

    override suspend fun getHistoricalGlucoseWindow(startEpochMs: Long, endEpochMs: Long, maxItems: Int): List<GlucoseMeasurement> {
        val key = "$startEpochMs:$endEpochMs:$maxItems"
        historicalWindowCache[key]?.let { return it.toList() }

        val window = withContext(Dispatchers.IO) {
            historyDb.readWindowNewestFirst(startEpochMs, endEpochMs, maxItems)
        }

        historicalWindowCache[key] = window.toList()
        return window
    }

    override suspend fun syncLocalArchiveFromPreferences() {
        val snapshot = preferenceManager.historicalGlucoseArchive.first()
        if (snapshot.isEmpty()) {
            historicalState.value = withContext(Dispatchers.IO) { 
                historyDb.removeDuplicates()
                historyDb.readAllNewestFirst() 
            }
            return
        }

        withContext(Dispatchers.IO) {
            val merged = mergeAndPruneHistory(
                existing = historyDb.readAllNewestFirst(),
                incoming = snapshot
            )
            historyDb.replaceAll(merged)
            historyDb.removeDuplicates()
            historicalWindowCache.clear()
            historicalState.value = merged
            _dataVersion.value++
        }
    }

    private suspend fun fetchLatestGlucoseInternal(persistArchive: Boolean, allowReauth: Boolean = true): Result<GlucoseMeasurement> {
        val demoEnabled = preferenceManager.isDemoMode.first()
        if (demoEnabled) {
            val now = Instant.now()
            val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
            val currentHistory = withContext(Dispatchers.IO) { historyDb.readAllNewestFirst() }
            
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
                withContext(Dispatchers.IO) {
                    historyDb.replaceAll(mergedHistory)
                }
                historicalWindowCache.clear()
                historicalState.value = mergedHistory
                _dataVersion.value++
                mirrorSnapshotIfNeeded(mergedHistory)
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
            val connectionSensor = response.data?.connection?.sensor
            
            // Try picking the newest sensor from activeSensors, fallback to connection sensor
            val sensor = activeSensors?.map { it.sensor }?.maxByOrNull { it.activationTimestamp }
                ?: connectionSensor
            
            if (sensor != null) {
                val activationTime = if (sensor.activationTimestamp > 10_000_000_000L) 
                    sensor.activationTimestamp / 1000 
                else 
                    sensor.activationTimestamp
                    
                preferenceManager.saveActiveSensorInfo(sensor.serialNumber, activationTime)
                autoLogSensor(sensor.serialNumber, activationTime)
            }

            val historicalMeasurements = response.data?.graphData ?: emptyList()
            val incomingList = if (measurement != null) {
                historicalMeasurements + measurement
            } else {
                historicalMeasurements
            }

            val currentHistory = withContext(Dispatchers.IO) { historyDb.readAllNewestFirst() }
            val mergedHistory = mergeAndPruneHistory(
                existing = currentHistory,
                incoming = incomingList
            )
            
            if (persistArchive) {
                withContext(Dispatchers.IO) {
                    historyDb.replaceAll(mergedHistory)
                }
                historicalWindowCache.clear()
                historicalState.value = mergedHistory
                _dataVersion.value++
                mirrorSnapshotIfNeeded(mergedHistory)
            }

            val resultMeasurement = measurement ?: mergedHistory.firstOrNull()
            if (resultMeasurement != null) {
                Result.success(resultMeasurement)
            } else {
                Result.failure(Exception("No glucose data found in response"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is retrofit2.HttpException && e.code() == 401) {
                // Session expired: try a silent re-login with the stored credentials before giving up.
                val credentials = if (allowReauth) credentialStore.getCredentials() else null
                if (credentials != null && login(credentials.first, credentials.second).isSuccess) {
                    return fetchLatestGlucoseInternal(persistArchive, allowReauth = false)
                }
                logout()
            }
            Result.failure(e)
        }
    }

    suspend fun initialize() {
        initializeLocalHistoryIfNeeded()

        val token = preferenceManager.authToken.first()
        val userId = preferenceManager.userId.first()

        preferenceManager.requestHistoryCloudBackupIfDue()

        if (token != null && userId != null) {
            LibreService.setAuth(token, userId)
        }

        // Ensure current active sensor from preferences is in the logs
        val sn = preferenceManager.activeSensorSerialNumber.first()
        val start = preferenceManager.activeSensorStartTime.first()
        if (sn != null && start != null) {
            autoLogSensor(sn, start)
        }
    }

    private suspend fun initializeLocalHistoryIfNeeded() {
        val dbHistory = withContext(Dispatchers.IO) { 
            historyDb.removeDuplicates()
            historyDb.readAllNewestFirst() 
        }
        if (dbHistory.isNotEmpty()) {
            historicalState.value = dbHistory
            return
        }

        val snapshot = preferenceManager.historicalGlucoseArchive.first()
        if (snapshot.isNotEmpty()) {
            val merged = mergeAndPruneHistory(emptyList(), snapshot)
            withContext(Dispatchers.IO) { 
                historyDb.replaceAll(merged) 
                historyDb.removeDuplicates()
            }
            historicalState.value = merged
            return
        }

        historicalState.value = emptyList()
    }

    private suspend fun autoLogSensor(sn: String, activationEpochSeconds: Long) {
        val currentLogs = preferenceManager.sensorLogs.first().toMutableList()
        if (currentLogs.any { it.serialNumber == sn }) return

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
            .withZone(ZoneId.systemDefault())
        val start = formatter.format(Instant.ofEpochSecond(activationEpochSeconds))
        val sensorDuration = preferenceManager.sensorDurationDays.first()
        val expiry = formatter.format(Instant.ofEpochSecond(activationEpochSeconds + (sensorDuration.toLong() * 24 * 60 * 60)))

        val newLog = SensorLog(
            serialNumber = sn,
            startDate = start,
            expiryDate = expiry
        )
        currentLogs.add(newLog)
        currentLogs.sortByDescending { it.startDate }
        preferenceManager.saveSensorLogs(currentLogs)
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
                parseMeasurementInstant(m)?.let { instant -> 
                    // Populate epochSeconds to avoid redundant parsing in UI/Statistics
                    instant to m.copy(epochSeconds = instant.epochSecond)
                }
            }
            .filter { (instant, _) -> !instant.isBefore(cutoff) }
            .sortedByDescending { it.first } // Newest first
            .map { it.second }
    }

    private suspend fun mirrorSnapshotIfNeeded(history: List<GlucoseMeasurement>) {
        val now = System.currentTimeMillis()
        val shouldMirror =
            now - lastSnapshotMirrorEpochMs >= SNAPSHOT_MIRROR_INTERVAL_MS || history.size <= 24
        if (!shouldMirror) return

        preferenceManager.saveHistoricalGlucoseArchiveSnapshot(history)
        lastSnapshotMirrorEpochMs = now
    }

    private fun parseMeasurementInstant(measurement: GlucoseMeasurement): Instant? {
        // LibreLinkUp "Timestamp" is already adjusted to the account's timezone.
        // We let the parser use the system default to avoid double offsets.
        return parseFlexibleInstant(measurement.timestamp)
            ?: parseFlexibleInstant(measurement.factoryTimestamp)
    }

    private fun parseFlexibleInstant(timestamp: String, zoneId: ZoneId = ZoneId.systemDefault()): Instant? {
        return TimestampParser.parseFlexibleInstant(timestamp, zoneId)
    }

    companion object {
        private const val SNAPSHOT_MIRROR_INTERVAL_MS = 15L * 60L * 1000L
        private const val MAX_WINDOW_CACHE_ENTRIES = 8
    }
}
