package com.tonio.libre2clock.data.repository

import android.app.backup.BackupManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.HistoryBackupPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalTime

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        private const val HISTORY_BACKUP_REQUEST_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val HISTORY_BACKUP_DIR = "backup"
        private const val HISTORY_BACKUP_FILE = "history_backup.json"
    }

    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val GLUCOSE_OFFSET_KEY = androidx.datastore.preferences.core.intPreferencesKey("glucose_offset")
    private val GLUCOSE_OFFSET_RANGES_KEY = stringPreferencesKey("glucose_offset_ranges")
    private val AUTO_ADJUST_ENABLED_KEY = booleanPreferencesKey("auto_adjust_enabled")
    private val CAPILLARY_READINGS_KEY = stringPreferencesKey("capillary_readings")
    private val WATCH_ALERTS_ENABLED_KEY = booleanPreferencesKey("watch_alerts_enabled")
    private val WATCH_ALERT_INTERVAL_MINUTES_KEY = androidx.datastore.preferences.core.intPreferencesKey("watch_alert_interval_minutes")
    private val WATCH_ALERT_START_MINUTE_KEY = androidx.datastore.preferences.core.intPreferencesKey("watch_alert_start_minute")
    private val LOW_GLUCOSE_ALARM_ENABLED_KEY = booleanPreferencesKey("low_glucose_alarm_enabled")
    private val HIGH_GLUCOSE_ALARM_ENABLED_KEY = booleanPreferencesKey("high_glucose_alarm_enabled")
    private val HISTORICAL_GLUCOSE_KEY = stringPreferencesKey("historical_glucose_archive")
    private val LAST_HISTORY_BACKUP_REQUEST_AT_KEY = longPreferencesKey("last_history_backup_request_at")

    val authToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    val glucoseOffset: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GLUCOSE_OFFSET_KEY] ?: 0
    }

    val glucoseOffsetRanges: Flow<List<GlucoseOffsetRange>> = context.dataStore.data.map { preferences ->
        val json = preferences[GLUCOSE_OFFSET_RANGES_KEY]
        if (json != null) {
            try {
                Json.decodeFromString<List<GlucoseOffsetRange>>(json)
            } catch (e: Exception) {
                getDefaultRanges()
            }
        } else {
            getDefaultRanges()
        }
    }

    val autoAdjustEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_ADJUST_ENABLED_KEY] ?: false
    }

    val capillaryReadings: Flow<List<CapillaryMeasurement>> = context.dataStore.data.map { preferences ->
        val json = preferences[CAPILLARY_READINGS_KEY]
        if (json != null) {
            try {
                Json.decodeFromString<List<CapillaryMeasurement>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val watchAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WATCH_ALERTS_ENABLED_KEY] ?: false
    }

    val watchAlertIntervalMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[WATCH_ALERT_INTERVAL_MINUTES_KEY] ?: 60).coerceIn(5, 180)
    }

    val watchAlertStartMinute: Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[WATCH_ALERT_START_MINUTE_KEY] ?: 0).coerceIn(0, 59)
    }

    val lowGlucoseAlarmEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOW_GLUCOSE_ALARM_ENABLED_KEY] ?: false
    }

    val highGlucoseAlarmEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HIGH_GLUCOSE_ALARM_ENABLED_KEY] ?: false
    }

    val historicalGlucoseArchive: Flow<List<GlucoseMeasurement>> = context.dataStore.data.map { preferences ->
        val json = preferences[HISTORICAL_GLUCOSE_KEY]
        if (json != null) {
            try {
                Json.decodeFromString<List<GlucoseMeasurement>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val lastHistoryBackupRequestAt: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[LAST_HISTORY_BACKUP_REQUEST_AT_KEY]
    }

    private fun getDefaultRanges() = listOf(
        GlucoseOffsetRange(0, 70, 20),
        GlucoseOffsetRange(70, 100, 40),
        GlucoseOffsetRange(100, 140, 60),
        GlucoseOffsetRange(140, 200, 80),
        GlucoseOffsetRange(200, null, 80)
    )

    suspend fun saveAuth(token: String, userId: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
            preferences[USER_ID_KEY] = userId
        }
    }

    suspend fun saveGlucoseOffset(offset: Int) {
        context.dataStore.edit { preferences ->
            preferences[GLUCOSE_OFFSET_KEY] = offset
        }
    }

    suspend fun saveGlucoseOffsetRanges(ranges: List<GlucoseOffsetRange>) {
        context.dataStore.edit { preferences ->
            preferences[GLUCOSE_OFFSET_RANGES_KEY] = Json.encodeToString(ranges)
        }
    }

    suspend fun saveAutoAdjustEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ADJUST_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveCapillaryReadings(readings: List<CapillaryMeasurement>) {
        context.dataStore.edit { preferences ->
            preferences[CAPILLARY_READINGS_KEY] = Json.encodeToString(readings)
        }
        val historicalArchive = historicalGlucoseArchive.first()
        saveHistoryBackupPayload(
            HistoryBackupPayload(
                historicalGlucoseArchive = historicalArchive,
                capillaryReadings = readings
            )
        )
        requestHistoryCloudBackupIfDue()
    }

    suspend fun saveWatchAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WATCH_ALERTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun initializeWatchAlertStartMinuteIfMissing() {
        val currentMinute = LocalTime.now().minute
        context.dataStore.edit { preferences ->
            if (preferences[WATCH_ALERT_START_MINUTE_KEY] == null) {
                preferences[WATCH_ALERT_START_MINUTE_KEY] = currentMinute.coerceIn(0, 59)
            }
        }
    }

    suspend fun saveWatchAlertIntervalMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[WATCH_ALERT_INTERVAL_MINUTES_KEY] = minutes.coerceIn(5, 180)
        }
    }

    suspend fun saveWatchAlertStartMinute(minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[WATCH_ALERT_START_MINUTE_KEY] = minute.coerceIn(0, 59)
        }
    }

    suspend fun saveLowGlucoseAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOW_GLUCOSE_ALARM_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveHighGlucoseAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIGH_GLUCOSE_ALARM_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveHistoricalGlucoseArchive(measurements: List<GlucoseMeasurement>) {
        context.dataStore.edit { preferences ->
            preferences[HISTORICAL_GLUCOSE_KEY] = Json.encodeToString(measurements)
        }
        val capillaryArchive = capillaryReadings.first()
        saveHistoryBackupPayload(
            HistoryBackupPayload(
                historicalGlucoseArchive = measurements,
                capillaryReadings = capillaryArchive
            )
        )
        requestHistoryCloudBackupIfDue()
    }

    fun loadHistoryBackupPayload(): HistoryBackupPayload? {
        val file = historyBackupFile()
        if (!file.exists()) return null
        return try {
            Json.decodeFromString<HistoryBackupPayload>(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun requestHistoryCloudBackupIfDue(force: Boolean = false): Boolean {
        if (!historyBackupFile().exists()) return false

        val now = System.currentTimeMillis()
        val lastRequestedAt = lastHistoryBackupRequestAt.first() ?: 0L
        if (!force && now - lastRequestedAt < HISTORY_BACKUP_REQUEST_INTERVAL_MS) {
            return false
        }

        BackupManager(context).dataChanged()
        context.dataStore.edit { preferences ->
            preferences[LAST_HISTORY_BACKUP_REQUEST_AT_KEY] = now
        }
        return true
    }

    suspend fun requestPartialHistoryCloudBackup(
        includeHistoricalGlucose: Boolean,
        includeCapillaryReadings: Boolean
    ): Boolean {
        val payload = HistoryBackupPayload(
            historicalGlucoseArchive = if (includeHistoricalGlucose) {
                historicalGlucoseArchive.first()
            } else {
                emptyList()
            },
            capillaryReadings = if (includeCapillaryReadings) {
                capillaryReadings.first()
            } else {
                emptyList()
            }
        )
        saveHistoryBackupPayload(payload)
        return requestHistoryCloudBackupIfDue(force = true)
    }

    suspend fun restorePartialHistoryFromBackup(
        includeHistoricalGlucose: Boolean,
        includeCapillaryReadings: Boolean
    ): Boolean {
        val payload = loadHistoryBackupPayload() ?: return false
        if (!includeHistoricalGlucose && !includeCapillaryReadings) return false

        val currentHistorical = historicalGlucoseArchive.first()
        val currentCapillary = capillaryReadings.first()

        val restoredHistorical = if (includeHistoricalGlucose) {
            mergeHistoricalMeasurements(currentHistorical, payload.historicalGlucoseArchive)
        } else {
            currentHistorical
        }
        val restoredCapillary = if (includeCapillaryReadings) {
            mergeCapillaryMeasurements(currentCapillary, payload.capillaryReadings)
        } else {
            currentCapillary
        }

        context.dataStore.edit { preferences ->
            preferences[HISTORICAL_GLUCOSE_KEY] = Json.encodeToString(restoredHistorical)
            preferences[CAPILLARY_READINGS_KEY] = Json.encodeToString(restoredCapillary)
        }
        saveHistoryBackupPayload(
            HistoryBackupPayload(
                historicalGlucoseArchive = restoredHistorical,
                capillaryReadings = restoredCapillary
            )
        )
        return true
    }

    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
        }
    }

    private fun historyBackupFile(): File {
        val dir = File(context.filesDir, HISTORY_BACKUP_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, HISTORY_BACKUP_FILE)
    }

    private fun saveHistoryBackupPayload(payload: HistoryBackupPayload) {
        historyBackupFile().writeText(Json.encodeToString(payload))
    }

    private fun mergeHistoricalMeasurements(
        local: List<GlucoseMeasurement>,
        backup: List<GlucoseMeasurement>
    ): List<GlucoseMeasurement> {
        return (local + backup)
            .distinctBy { measurement ->
                "${measurement.timestamp}|${measurement.value}|${measurement.calibratedValue}|${measurement.trendArrow}"
            }
            .sortedByDescending { it.timestamp }
    }

    private fun mergeCapillaryMeasurements(
        local: List<CapillaryMeasurement>,
        backup: List<CapillaryMeasurement>
    ): List<CapillaryMeasurement> {
        return (local + backup)
            .distinctBy { reading ->
                "${reading.timestamp}|${reading.value}|${reading.sensorValue}|${reading.delta}"
            }
            .sortedByDescending { it.timestamp }
    }
}
