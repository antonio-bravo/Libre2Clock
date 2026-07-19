package com.tonio.libre2clock.data.repository

import android.app.backup.BackupManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.io.IOException
import java.time.LocalTime

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    companion object {
        private const val HISTORY_BACKUP_REQUEST_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val HISTORY_BACKUP_DIR = "backup"
        private const val HISTORY_BACKUP_FILE = "history_backup.json"
        private const val LOCAL_DOWNLOADS_BACKUP_FILE = "libre2clock_history_backup.json"
        private const val LOCAL_DOWNLOADS_BACKUP_SUBDIR = "Libre2Clock"
        private const val DEFAULT_HISTORY_RETENTION_DAYS = 90
        private const val MIN_HISTORY_RETENTION_DAYS = 30
        private const val MAX_HISTORY_RETENTION_DAYS = 365
    }

    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val PATIENT_ID_KEY = stringPreferencesKey("patient_id")
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
    private val HISTORY_RETENTION_DAYS_KEY = androidx.datastore.preferences.core.intPreferencesKey("history_retention_days")
    private val LAST_HISTORY_BACKUP_REQUEST_AT_KEY = longPreferencesKey("last_history_backup_request_at")
    private val IS_DEMO_MODE_KEY = booleanPreferencesKey("is_demo_mode")

    val authToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    val patientId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PATIENT_ID_KEY]
    }

    val glucoseOffset: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GLUCOSE_OFFSET_KEY] ?: 0
    }

    val glucoseOffsetRanges: Flow<List<GlucoseOffsetRange>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[GLUCOSE_OFFSET_RANGES_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<List<GlucoseOffsetRange>>(jsonStr)
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
        val jsonStr = preferences[CAPILLARY_READINGS_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<List<CapillaryMeasurement>>(jsonStr)
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
        val jsonStr = preferences[HISTORICAL_GLUCOSE_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<List<GlucoseMeasurement>>(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val historyRetentionDays: Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_HISTORY_RETENTION_DAYS)
            .coerceIn(MIN_HISTORY_RETENTION_DAYS, MAX_HISTORY_RETENTION_DAYS)
    }

    val lastHistoryBackupRequestAt: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[LAST_HISTORY_BACKUP_REQUEST_AT_KEY]
    }

    val isDemoMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_DEMO_MODE_KEY] ?: false
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

    suspend fun savePatientId(patientId: String) {
        context.dataStore.edit { preferences ->
            preferences[PATIENT_ID_KEY] = patientId
        }
    }

    suspend fun saveGlucoseOffset(offset: Int) {
        context.dataStore.edit { preferences ->
            preferences[GLUCOSE_OFFSET_KEY] = offset
        }
    }

    suspend fun saveGlucoseOffsetRanges(ranges: List<GlucoseOffsetRange>) {
        context.dataStore.edit { preferences ->
            preferences[GLUCOSE_OFFSET_RANGES_KEY] = json.encodeToString(ranges)
        }
    }

    suspend fun saveAutoAdjustEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ADJUST_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveCapillaryReadings(readings: List<CapillaryMeasurement>) {
        context.dataStore.edit { preferences ->
            preferences[CAPILLARY_READINGS_KEY] = json.encodeToString(readings)
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
            preferences[HISTORICAL_GLUCOSE_KEY] = json.encodeToString(measurements)
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

    suspend fun saveHistoryRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[HISTORY_RETENTION_DAYS_KEY] = days.coerceIn(
                MIN_HISTORY_RETENTION_DAYS,
                MAX_HISTORY_RETENTION_DAYS
            )
        }
    }

    suspend fun saveDemoMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DEMO_MODE_KEY] = enabled
        }
    }

    fun loadHistoryBackupPayload(): HistoryBackupPayload? {
        val file = historyBackupFile()
        if (!file.exists()) return null
        return try {
            json.decodeFromString<HistoryBackupPayload>(file.readText())
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

    suspend fun exportHistoryBackupToDownloads(): Result<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(
                IllegalStateException("Local export to Downloads requires Android 10 or newer.")
            )
        }

        val payload = buildCurrentHistoryBackupPayload()
        val jsonPayload = json.encodeToString(payload)
        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + LOCAL_DOWNLOADS_BACKUP_SUBDIR

        deleteExistingDownloadsBackup(relativePath)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, LOCAL_DOWNLOADS_BACKUP_FILE)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IOException("Could not create backup file in Downloads."))

        return try {
            resolver.openOutputStream(uri, "w")?.use { outputStream ->
                outputStream.write(jsonPayload.toByteArray())
            } ?: throw IOException("Could not open backup file output stream.")

            val finalizeValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, finalizeValues, null, null)
            Result.success("Downloads/$LOCAL_DOWNLOADS_BACKUP_SUBDIR/$LOCAL_DOWNLOADS_BACKUP_FILE")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Result.failure(e)
        }
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
            preferences[HISTORICAL_GLUCOSE_KEY] = json.encodeToString(restoredHistorical)
            preferences[CAPILLARY_READINGS_KEY] = json.encodeToString(restoredCapillary)
        }
        saveHistoryBackupPayload(
            HistoryBackupPayload(
                historicalGlucoseArchive = restoredHistorical,
                capillaryReadings = restoredCapillary
            )
        )
        return true
    }

    suspend fun restoreHistoryBackupFromUri(uri: Uri): Result<HistoryBackupPayload> {
        return try {
            val payloadText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: throw IOException("Could not read selected backup file.")

            val payload = json.decodeFromString<HistoryBackupPayload>(payloadText)
            val mergedHistorical = mergeHistoricalMeasurements(
                historicalGlucoseArchive.first(),
                payload.historicalGlucoseArchive
            )
            val mergedCapillary = mergeCapillaryMeasurements(
                capillaryReadings.first(),
                payload.capillaryReadings
            )

            context.dataStore.edit { preferences ->
                preferences[HISTORICAL_GLUCOSE_KEY] = json.encodeToString(mergedHistorical)
                preferences[CAPILLARY_READINGS_KEY] = json.encodeToString(mergedCapillary)
            }
            saveHistoryBackupPayload(
                HistoryBackupPayload(
                    historicalGlucoseArchive = mergedHistorical,
                    capillaryReadings = mergedCapillary
                )
            )
            Result.success(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        historyBackupFile().writeText(json.encodeToString(payload))
    }

    private suspend fun buildCurrentHistoryBackupPayload(): HistoryBackupPayload {
        return HistoryBackupPayload(
            historicalGlucoseArchive = historicalGlucoseArchive.first(),
            capillaryReadings = capillaryReadings.first()
        )
    }

    private fun deleteExistingDownloadsBackup(relativePath: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val resolver = context.contentResolver
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(LOCAL_DOWNLOADS_BACKUP_FILE, relativePath)
        val projection = arrayOf(MediaStore.Downloads._ID)

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val existingUri = android.content.ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    id
                )
                resolver.delete(existingUri, null, null)
            }
        }
    }

    private fun mergeHistoricalMeasurements(
        local: List<GlucoseMeasurement>,
        backup: List<GlucoseMeasurement>
    ): List<GlucoseMeasurement> {
        val mergedMap = LinkedHashMap<String, GlucoseMeasurement>()
        (local + backup).forEach { m ->
            val instant = parseFlexibleInstant(m.factoryTimestamp) ?: parseFlexibleInstant(m.timestamp)
            val key = if (instant != null) {
                "${instant.toEpochMilli()}-${m.value}"
            } else {
                "${m.timestamp}-${m.value}"
            }
            mergedMap[key] = m
        }

        return mergedMap.values
            .mapNotNull { m ->
                val instant = parseFlexibleInstant(m.factoryTimestamp) ?: parseFlexibleInstant(m.timestamp)
                if (instant != null) instant to m else null
            }
            .sortedByDescending { it.first }
            .map { it.second }
    }

    private fun mergeCapillaryMeasurements(
        local: List<CapillaryMeasurement>,
        backup: List<CapillaryMeasurement>
    ): List<CapillaryMeasurement> {
        val mergedMap = LinkedHashMap<String, CapillaryMeasurement>()
        (local + backup).forEach { r ->
            val instant = parseFlexibleInstant(r.timestamp)
            val key = if (instant != null) {
                "${instant.toEpochMilli()}-${r.value}"
            } else {
                "${r.timestamp}-${r.value}"
            }
            mergedMap[key] = r
        }

        return mergedMap.values
            .mapNotNull { r ->
                val instant = parseFlexibleInstant(r.timestamp)
                if (instant != null) instant to r else null
            }
            .sortedByDescending { it.first }
            .map { it.second }
    }

    private fun parseFlexibleInstant(timestamp: String): java.time.Instant? {
        return com.tonio.libre2clock.util.TimestampParser.parseFlexibleInstant(timestamp)
    }
}
