package com.tonio.libre2clock.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tonio.libre2clock.data.model.AlarmSchedule
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.HistoryBackupPayload
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.data.model.WatchNotificationMode
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant
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

    // --- Keys ---
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val PATIENT_ID_KEY = stringPreferencesKey("patient_id")
    private val LIBRE_LINK_UP_EMAIL_KEY = stringPreferencesKey("libre_link_up_email")
    private val GLUCOSE_OFFSET_KEY = intPreferencesKey("glucose_offset")
    private val GLUCOSE_OFFSET_RANGES_KEY = stringPreferencesKey("glucose_offset_ranges")
    private val AUTO_ADJUST_ENABLED_KEY = booleanPreferencesKey("auto_adjust_enabled")
    private val AUTO_RANGE_OFFSETS_ENABLED_KEY = booleanPreferencesKey("auto_range_offsets_enabled")
    private val AUTO_RANGE_OFFSET_MODE_KEY = stringPreferencesKey("auto_range_offset_mode")
    private val CAPILLARY_READINGS_KEY = stringPreferencesKey("capillary_readings")
    private val WATCH_ALERTS_ENABLED_KEY = booleanPreferencesKey("watch_alerts_enabled")
    private val WATCH_NOTIFICATION_MODE_KEY = stringPreferencesKey("watch_notification_mode")
    private val WATCH_ALERT_INTERVAL_MINUTES_KEY = intPreferencesKey("watch_alert_interval_minutes")
    private val WATCH_ALERT_START_MINUTE_KEY = intPreferencesKey("watch_alert_start_minute")
    private val LOW_GLUCOSE_ALARM_ENABLED_KEY = booleanPreferencesKey("low_glucose_alarm_enabled")
    private val HIGH_GLUCOSE_ALARM_ENABLED_KEY = booleanPreferencesKey("high_glucose_alarm_enabled")
    private val USE_CALIBRATED_FOR_ALARMS_KEY = booleanPreferencesKey("use_calibrated_for_alarms")
    private val HISTORICAL_GLUCOSE_KEY = stringPreferencesKey("historical_glucose_archive")
    private val HISTORY_RETENTION_DAYS_KEY = intPreferencesKey("history_retention_days")
    private val LAST_HISTORY_BACKUP_REQUEST_AT_KEY = longPreferencesKey("last_history_backup_request_at")
    private val IS_DEMO_MODE_KEY = booleanPreferencesKey("is_demo_mode")
    private val ACTIVE_SENSOR_SN_KEY = stringPreferencesKey("active_sensor_sn")
    private val ACTIVE_SENSOR_START_TIME_KEY = longPreferencesKey("active_sensor_start_time")
    private val RAPID_DURATION_MINS_KEY = intPreferencesKey("rapid_duration_mins")
    private val SLOW_DURATION_MINS_KEY = intPreferencesKey("slow_duration_mins")
    private val IC_RULE_CONSTANT_KEY = intPreferencesKey("ic_rule_constant")
    private val ISF_RULE_CONSTANT_KEY = intPreferencesKey("isf_rule_constant")
    private val MANUAL_TDI_KEY = doublePreferencesKey("manual_tdi")
    private val MANUAL_ISF_KEY = doublePreferencesKey("manual_isf")
    private val TARGET_GLUCOSE_KEY = intPreferencesKey("target_glucose")
    private val INSULIN_DOSES_KEY = stringPreferencesKey("insulin_doses")
    private val SENSOR_LOGS_KEY = stringPreferencesKey("sensor_logs")
    private val WATCH_NOTIFICATION_SCHEDULES_KEY = stringPreferencesKey("watch_notification_schedules")
    private val GLUCOSE_ALARM_SCHEDULES_KEY = stringPreferencesKey("glucose_alarm_schedules")
    private val BATTERY_LOW_THRESHOLD_KEY = intPreferencesKey("battery_low_threshold")
    private val BATTERY_CRITICAL_THRESHOLD_KEY = intPreferencesKey("battery_critical_threshold")
    private val DISABLE_FAST_REFRESH_ON_SLOW_CHARGE_KEY = booleanPreferencesKey("disable_fast_refresh_on_slow_charge")
    private val SENSOR_DURATION_DAYS_KEY = intPreferencesKey("sensor_duration_days")
    private val IS_CLOUD_SYNC_ENABLED_KEY = booleanPreferencesKey("is_cloud_sync_enabled")
    private val CLOUD_SYNC_LAST_SUCCESS_AT_KEY = longPreferencesKey("cloud_sync_last_success_at")
    private val SETTINGS_UPDATED_AT_KEY = longPreferencesKey("settings_updated_at")

    // --- Flows ---
    val authToken: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val patientId: Flow<String?> = context.dataStore.data.map { it[PATIENT_ID_KEY] }
    val libreLinkUpEmail: Flow<String?> = context.dataStore.data.map { it[LIBRE_LINK_UP_EMAIL_KEY] }
    val glucoseOffset: Flow<Int> = context.dataStore.data.map { it[GLUCOSE_OFFSET_KEY] ?: 0 }
    
    val glucoseOffsetRanges: Flow<List<GlucoseOffsetRange>> = context.dataStore.data.map { prefs ->
        try {
            prefs[GLUCOSE_OFFSET_RANGES_KEY]?.let { json.decodeFromString<List<GlucoseOffsetRange>>(it) } ?: getDefaultRanges()
        } catch (e: Exception) { getDefaultRanges() }
    }

    val autoAdjustEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_ADJUST_ENABLED_KEY] ?: false }

    val autoRangeOffsetMode: Flow<AutoRangeOffsetMode> = context.dataStore.data.map { prefs ->
        val persisted = prefs[AUTO_RANGE_OFFSET_MODE_KEY]
        if (!persisted.isNullOrBlank()) {
            val mode = AutoRangeOffsetMode.entries.firstOrNull { it.name == persisted } ?: AutoRangeOffsetMode.OFF
            if (mode == AutoRangeOffsetMode.GLOBAL) AutoRangeOffsetMode.BY_RANGE else mode
        } else {
            if (prefs[AUTO_RANGE_OFFSETS_ENABLED_KEY] == true) AutoRangeOffsetMode.BY_RANGE else AutoRangeOffsetMode.OFF
        }
    }

    val autoRangeOffsetsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val persisted = prefs[AUTO_RANGE_OFFSET_MODE_KEY]
        if (!persisted.isNullOrBlank()) {
            val mode = AutoRangeOffsetMode.entries.firstOrNull { it.name == persisted } ?: AutoRangeOffsetMode.OFF
            mode != AutoRangeOffsetMode.OFF
        } else {
            prefs[AUTO_RANGE_OFFSETS_ENABLED_KEY] ?: false
        }
    }

    val capillaryReadings: Flow<List<CapillaryMeasurement>> = context.dataStore.data.map { prefs ->
        try { prefs[CAPILLARY_READINGS_KEY]?.let { json.decodeFromString<List<CapillaryMeasurement>>(it) } ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    val watchAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { it[WATCH_ALERTS_ENABLED_KEY] ?: false }

    val watchNotificationMode: Flow<WatchNotificationMode> = context.dataStore.data.map { prefs ->
        val persisted = prefs[WATCH_NOTIFICATION_MODE_KEY]
        if (!persisted.isNullOrBlank()) {
            WatchNotificationMode.entries.firstOrNull { it.name == persisted } ?: WatchNotificationMode.OFF
        } else {
            if (prefs[WATCH_ALERTS_ENABLED_KEY] == true) WatchNotificationMode.PERIODIC_AND_SCHEDULES else WatchNotificationMode.OFF
        }
    }

    val watchAlertIntervalMinutes: Flow<Int> = context.dataStore.data.map { (it[WATCH_ALERT_INTERVAL_MINUTES_KEY] ?: 60).coerceIn(5, 180) }
    val watchAlertStartMinute: Flow<Int> = context.dataStore.data.map { (it[WATCH_ALERT_START_MINUTE_KEY] ?: 0).coerceIn(0, 59) }
    val lowGlucoseAlarmEnabled: Flow<Boolean> = context.dataStore.data.map { it[LOW_GLUCOSE_ALARM_ENABLED_KEY] ?: false }
    val highGlucoseAlarmEnabled: Flow<Boolean> = context.dataStore.data.map { it[HIGH_GLUCOSE_ALARM_ENABLED_KEY] ?: false }
    val useCalibratedForAlarms: Flow<Boolean> = context.dataStore.data.map { it[USE_CALIBRATED_FOR_ALARMS_KEY] ?: true }

    val historicalGlucoseArchive: Flow<List<GlucoseMeasurement>> = context.dataStore.data.map { prefs ->
        try { prefs[HISTORICAL_GLUCOSE_KEY]?.let { json.decodeFromString<List<GlucoseMeasurement>>(it) } ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    val historyRetentionDays: Flow<Int> = context.dataStore.data.map { (it[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_HISTORY_RETENTION_DAYS).coerceIn(MIN_HISTORY_RETENTION_DAYS, MAX_HISTORY_RETENTION_DAYS) }
    val lastHistoryBackupRequestAt: Flow<Long?> = context.dataStore.data.map { it[LAST_HISTORY_BACKUP_REQUEST_AT_KEY] }
    val isDemoMode: Flow<Boolean> = context.dataStore.data.map { it[IS_DEMO_MODE_KEY] ?: false }
    val activeSensorSerialNumber: Flow<String?> = context.dataStore.data.map { it[ACTIVE_SENSOR_SN_KEY] }
    val activeSensorStartTime: Flow<Long?> = context.dataStore.data.map { it[ACTIVE_SENSOR_START_TIME_KEY] }
    val rapidDurationMins: Flow<Int> = context.dataStore.data.map { it[RAPID_DURATION_MINS_KEY] ?: 240 }
    val slowDurationMins: Flow<Int> = context.dataStore.data.map { it[SLOW_DURATION_MINS_KEY] ?: 1440 }
    val icRuleConstant: Flow<Int> = context.dataStore.data.map { it[IC_RULE_CONSTANT_KEY] ?: 450 }
    val isfRuleConstant: Flow<Int> = context.dataStore.data.map { it[ISF_RULE_CONSTANT_KEY] ?: 1800 }
    val manualTdi: Flow<Double?> = context.dataStore.data.map { it[MANUAL_TDI_KEY] }
    val manualIsf: Flow<Double?> = context.dataStore.data.map { it[MANUAL_ISF_KEY] }
    val targetGlucose: Flow<Int> = context.dataStore.data.map { it[TARGET_GLUCOSE_KEY] ?: 80 }

    val insulinDoses: Flow<List<InsulinDose>> = context.dataStore.data.map { prefs ->
        try { prefs[INSULIN_DOSES_KEY]?.let { json.decodeFromString<List<InsulinDose>>(it) } ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    val sensorLogs: Flow<List<SensorLog>> = context.dataStore.data.map { prefs ->
        try { prefs[SENSOR_LOGS_KEY]?.let { json.decodeFromString<List<SensorLog>>(it) } ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    val watchNotificationSchedules: Flow<List<AlarmSchedule>> = context.dataStore.data.map { prefs ->
        try { prefs[WATCH_NOTIFICATION_SCHEDULES_KEY]?.let { json.decodeFromString<List<AlarmSchedule>>(it) } ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    val glucoseAlarmSchedules: Flow<List<AlarmSchedule>> = context.dataStore.data.map { prefs ->
        try { prefs[GLUCOSE_ALARM_SCHEDULES_KEY]?.let { json.decodeFromString<List<AlarmSchedule>>(it) } ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    val batteryLowThreshold: Flow<Int> = context.dataStore.data.map { it[BATTERY_LOW_THRESHOLD_KEY] ?: 15 }
    val batteryCriticalThreshold: Flow<Int> = context.dataStore.data.map { it[BATTERY_CRITICAL_THRESHOLD_KEY] ?: 5 }
    val disableFastRefreshOnSlowCharge: Flow<Boolean> = context.dataStore.data.map { it[DISABLE_FAST_REFRESH_ON_SLOW_CHARGE_KEY] ?: true }
    val sensorDurationDays: Flow<Int> = context.dataStore.data.map { it[SENSOR_DURATION_DAYS_KEY] ?: 15 }
    val isCloudSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_CLOUD_SYNC_ENABLED_KEY] ?: false }
    val cloudSyncLastSuccessAt: Flow<Long?> = context.dataStore.data.map { it[CLOUD_SYNC_LAST_SUCCESS_AT_KEY] }
    val settingsUpdatedAt: Flow<Long?> = context.dataStore.data.map { it[SETTINGS_UPDATED_AT_KEY] }

    private fun getDefaultRanges() = listOf(
        GlucoseOffsetRange(0, 70, 20),
        GlucoseOffsetRange(70, 100, 40),
        GlucoseOffsetRange(100, 140, 60),
        GlucoseOffsetRange(140, 200, 80),
        GlucoseOffsetRange(200, null, 80)
    )

    private inline fun <reified T> decodeList(prefs: Preferences, key: Preferences.Key<String>): List<T> {
        val jsonStr = prefs[key] ?: return emptyList()
        return try { json.decodeFromString(jsonStr) } catch (e: Exception) { emptyList() }
    }

    // --- Guardado (Save) ---
    suspend fun saveAuth(token: String, userId: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
            preferences[USER_ID_KEY] = userId
        }
    }

    suspend fun saveLibreLinkUpEmail(email: String) {
        context.dataStore.edit { it[LIBRE_LINK_UP_EMAIL_KEY] = email }
    }

    suspend fun savePatientId(patientId: String) {
        context.dataStore.edit { it[PATIENT_ID_KEY] = patientId }
    }

    suspend fun saveGlucoseOffset(offset: Int) {
        context.dataStore.edit { it[GLUCOSE_OFFSET_KEY] = offset }
        updateBackupPayload()
    }

    suspend fun saveGlucoseOffsetRanges(ranges: List<GlucoseOffsetRange>) {
        context.dataStore.edit { it[GLUCOSE_OFFSET_RANGES_KEY] = json.encodeToString(ranges) }
        updateBackupPayload()
    }

    suspend fun saveAutoAdjustEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_ADJUST_ENABLED_KEY] = enabled }
        updateBackupPayload()
    }

    suspend fun saveAutoRangeOffsetsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_RANGE_OFFSETS_ENABLED_KEY] = enabled
            preferences[AUTO_RANGE_OFFSET_MODE_KEY] = if (enabled) AutoRangeOffsetMode.BY_RANGE.name else AutoRangeOffsetMode.OFF.name
        }
        updateBackupPayload()
    }

    suspend fun saveAutoRangeOffsetMode(mode: AutoRangeOffsetMode) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_RANGE_OFFSET_MODE_KEY] = mode.name
            preferences[AUTO_RANGE_OFFSETS_ENABLED_KEY] = mode != AutoRangeOffsetMode.OFF
        }
        updateBackupPayload()
    }

    suspend fun saveCapillaryReadings(readings: List<CapillaryMeasurement>) {
        context.dataStore.edit { it[CAPILLARY_READINGS_KEY] = json.encodeToString(readings) }
        updateBackupPayload()
    }

    suspend fun saveWatchAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WATCH_ALERTS_ENABLED_KEY] = enabled
            preferences[WATCH_NOTIFICATION_MODE_KEY] = if (enabled) WatchNotificationMode.PERIODIC_AND_SCHEDULES.name else WatchNotificationMode.OFF.name
        }
        updateBackupPayload()
    }

    suspend fun saveWatchNotificationMode(mode: WatchNotificationMode) {
        context.dataStore.edit { preferences ->
            preferences[WATCH_NOTIFICATION_MODE_KEY] = mode.name
            preferences[WATCH_ALERTS_ENABLED_KEY] = mode != WatchNotificationMode.OFF
        }
        updateBackupPayload()
    }

    suspend fun initializeWatchAlertStartMinuteIfMissing() {
        val currentMinute = LocalTime.now().minute
        context.dataStore.edit { preferences ->
            if (preferences[WATCH_ALERT_START_MINUTE_KEY] == null) {
                preferences[WATCH_ALERT_START_MINUTE_KEY] = currentMinute.coerceIn(0, 59)
            }
        }
        updateBackupPayload()
    }

    suspend fun saveWatchAlertIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[WATCH_ALERT_INTERVAL_MINUTES_KEY] = minutes.coerceIn(5, 180) }
        updateBackupPayload()
    }

    suspend fun saveWatchAlertStartMinute(minute: Int) {
        context.dataStore.edit { it[WATCH_ALERT_START_MINUTE_KEY] = minute.coerceIn(0, 59) }
        updateBackupPayload()
    }

    suspend fun saveLowGlucoseAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[LOW_GLUCOSE_ALARM_ENABLED_KEY] = enabled }
        updateBackupPayload()
    }

    suspend fun saveHighGlucoseAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HIGH_GLUCOSE_ALARM_ENABLED_KEY] = enabled }
        updateBackupPayload()
    }

    suspend fun saveUseCalibratedForAlarms(enabled: Boolean) {
        context.dataStore.edit { it[USE_CALIBRATED_FOR_ALARMS_KEY] = enabled }
        updateBackupPayload()
    }

    suspend fun saveHistoricalGlucoseArchive(measurements: List<GlucoseMeasurement>) {
        context.dataStore.edit { it[HISTORICAL_GLUCOSE_KEY] = json.encodeToString(measurements) }
        updateBackupPayload()
    }

    suspend fun saveHistoricalGlucoseArchiveSnapshot(measurements: List<GlucoseMeasurement>) {
        context.dataStore.edit { it[HISTORICAL_GLUCOSE_KEY] = json.encodeToString(measurements) }
    }

    suspend fun saveHistoryRetentionDays(days: Int) {
        context.dataStore.edit { it[HISTORY_RETENTION_DAYS_KEY] = days.coerceIn(MIN_HISTORY_RETENTION_DAYS, MAX_HISTORY_RETENTION_DAYS) }
        updateBackupPayload()
    }

    suspend fun saveDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[IS_DEMO_MODE_KEY] = enabled }
    }

    suspend fun saveActiveSensorInfo(sn: String, startTime: Long) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_SENSOR_SN_KEY] = sn
            preferences[ACTIVE_SENSOR_START_TIME_KEY] = startTime
        }
    }

    suspend fun clearActiveSensorInfo() {
        context.dataStore.edit { preferences ->
            preferences.remove(ACTIVE_SENSOR_SN_KEY)
            preferences.remove(ACTIVE_SENSOR_START_TIME_KEY)
        }
    }

    suspend fun saveRapidDurationMins(minutes: Int) {
        context.dataStore.edit { it[RAPID_DURATION_MINS_KEY] = minutes }
        updateBackupPayload()
    }

    suspend fun saveSlowDurationMins(minutes: Int) {
        context.dataStore.edit { it[SLOW_DURATION_MINS_KEY] = minutes }
        updateBackupPayload()
    }

    suspend fun saveIcRuleConstant(constant: Int) {
        context.dataStore.edit { it[IC_RULE_CONSTANT_KEY] = constant }
        updateBackupPayload()
    }

    suspend fun saveIsfRuleConstant(constant: Int) {
        context.dataStore.edit { it[ISF_RULE_CONSTANT_KEY] = constant }
        updateBackupPayload()
    }

    suspend fun saveManualTdi(tdi: Double?) {
        context.dataStore.edit { preferences ->
            if (tdi == null) preferences.remove(MANUAL_TDI_KEY) else preferences[MANUAL_TDI_KEY] = tdi
        }
        updateBackupPayload()
    }

    suspend fun saveManualIsf(isf: Double?) {
        context.dataStore.edit { preferences ->
            if (isf == null) preferences.remove(MANUAL_ISF_KEY) else preferences[MANUAL_ISF_KEY] = isf
        }
        updateBackupPayload()
    }

    suspend fun saveTargetGlucose(target: Int) {
        context.dataStore.edit { it[TARGET_GLUCOSE_KEY] = target }
        updateBackupPayload()
    }

    suspend fun saveInsulinDoses(doses: List<InsulinDose>) {
        context.dataStore.edit { it[INSULIN_DOSES_KEY] = json.encodeToString(doses) }
        updateBackupPayload()
    }

    suspend fun saveSensorLogs(logs: List<SensorLog>) {
        context.dataStore.edit { it[SENSOR_LOGS_KEY] = json.encodeToString(logs) }
        updateBackupPayload()
    }

    suspend fun saveWatchNotificationSchedules(schedules: List<AlarmSchedule>) {
        context.dataStore.edit { it[WATCH_NOTIFICATION_SCHEDULES_KEY] = json.encodeToString(schedules) }
        updateBackupPayload()
    }

    suspend fun saveGlucoseAlarmSchedules(schedules: List<AlarmSchedule>) {
        context.dataStore.edit { it[GLUCOSE_ALARM_SCHEDULES_KEY] = json.encodeToString(schedules) }
        updateBackupPayload()
    }

    suspend fun saveBatteryLowThreshold(threshold: Int) {
        context.dataStore.edit { it[BATTERY_LOW_THRESHOLD_KEY] = threshold.coerceIn(5, 50) }
        updateBackupPayload()
    }

    suspend fun saveBatteryCriticalThreshold(threshold: Int) {
        context.dataStore.edit { it[BATTERY_CRITICAL_THRESHOLD_KEY] = threshold.coerceIn(1, 15) }
        updateBackupPayload()
    }

    suspend fun saveDisableFastRefreshOnSlowCharge(disabled: Boolean) {
        context.dataStore.edit { it[DISABLE_FAST_REFRESH_ON_SLOW_CHARGE_KEY] = disabled }
        updateBackupPayload()
    }

    suspend fun saveSensorDurationDays(days: Int) {
        context.dataStore.edit { it[SENSOR_DURATION_DAYS_KEY] = days.coerceIn(1, 30) }
        updateBackupPayload()
    }

    suspend fun saveCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_CLOUD_SYNC_ENABLED_KEY] = enabled }
    }

    suspend fun saveCloudSyncLastSuccessAt(timestamp: Long) {
        context.dataStore.edit { it[CLOUD_SYNC_LAST_SUCCESS_AT_KEY] = timestamp }
    }

    // --- Backup y Restauración ---
    private suspend fun updateBackupPayload(timestamp: Long? = null) {
        context.dataStore.edit { it[SETTINGS_UPDATED_AT_KEY] = timestamp ?: System.currentTimeMillis() }
        val payload = buildCurrentHistoryBackupPayload()
        saveHistoryBackupPayload(payload)
        requestHistoryCloudBackupIfDue()
    }

    fun loadHistoryBackupPayload(): HistoryBackupPayload? {
        val file = historyBackupFile()
        if (!file.exists()) return null
        return try { json.decodeFromString<HistoryBackupPayload>(file.readText()) } catch (e: Exception) { null }
    }

    suspend fun requestHistoryCloudBackupIfDue(force: Boolean = false): Boolean {
        if (!historyBackupFile().exists()) return false
        val now = System.currentTimeMillis()
        val lastRequestedAt = lastHistoryBackupRequestAt.first() ?: 0L
        if (!force && now - lastRequestedAt < HISTORY_BACKUP_REQUEST_INTERVAL_MS) return false

        context.dataStore.edit { it[LAST_HISTORY_BACKUP_REQUEST_AT_KEY] = now }
        return true
    }

    suspend fun requestPartialHistoryCloudBackup(
        includeHistoricalGlucose: Boolean,
        includeCapillaryReadings: Boolean,
        includeInsulinDoses: Boolean = true,
        includeSensorLogs: Boolean = true
    ): Boolean {
        val fullPayload = buildCurrentHistoryBackupPayload()
        val payload = fullPayload.copy(
            historicalGlucoseArchive = if (includeHistoricalGlucose) fullPayload.historicalGlucoseArchive else emptyList(),
            capillaryReadings = if (includeCapillaryReadings) fullPayload.capillaryReadings else emptyList(),
            insulinDoses = if (includeInsulinDoses) fullPayload.insulinDoses else emptyList(),
            sensorLogs = if (includeSensorLogs) fullPayload.sensorLogs else emptyList()
        )
        saveHistoryBackupPayload(payload)
        return requestHistoryCloudBackupIfDue(force = true)
    }

    suspend fun exportHistoryBackupToDownloads(): Result<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(IllegalStateException("Local export to Downloads requires Android 10 or newer."))
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
            resolver.openOutputStream(uri, "w")?.use { it.write(jsonPayload.toByteArray()) }
                ?: throw IOException("Could not open backup file output stream.")

            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            Result.success("Downloads/$LOCAL_DOWNLOADS_BACKUP_SUBDIR/$LOCAL_DOWNLOADS_BACKUP_FILE")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Result.failure(e)
        }
    }

    suspend fun restorePartialHistoryFromBackup(
        includeHistoricalGlucose: Boolean,
        includeCapillaryReadings: Boolean,
        includeInsulinDoses: Boolean = true,
        includeSensorLogs: Boolean = true
    ): Boolean {
        val payload = loadHistoryBackupPayload() ?: return false
        val currentPrefs = context.dataStore.data.first()
        
        val restoredHistorical = if (includeHistoricalGlucose) {
            mergeHistoricalMeasurements(decodeList(currentPrefs, HISTORICAL_GLUCOSE_KEY), payload.historicalGlucoseArchive)
        } else {
            decodeList(currentPrefs, HISTORICAL_GLUCOSE_KEY)
        }
        val restoredCapillary = if (includeCapillaryReadings) {
            mergeCapillaryMeasurements(decodeList(currentPrefs, CAPILLARY_READINGS_KEY), payload.capillaryReadings)
        } else {
            decodeList(currentPrefs, CAPILLARY_READINGS_KEY)
        }
        val restoredInsulin = if (includeInsulinDoses) {
            mergeInsulinDoses(decodeList(currentPrefs, INSULIN_DOSES_KEY), payload.insulinDoses)
        } else {
            decodeList(currentPrefs, INSULIN_DOSES_KEY)
        }
        val restoredSensorLogs = if (includeSensorLogs) {
            mergeSensorLogs(decodeList(currentPrefs, SENSOR_LOGS_KEY), payload.sensorLogs)
        } else {
            decodeList(currentPrefs, SENSOR_LOGS_KEY)
        }

        context.dataStore.edit { preferences ->
            preferences[HISTORICAL_GLUCOSE_KEY] = json.encodeToString(restoredHistorical)
            preferences[CAPILLARY_READINGS_KEY] = json.encodeToString(restoredCapillary)
            preferences[INSULIN_DOSES_KEY] = json.encodeToString(restoredInsulin)
            preferences[SENSOR_LOGS_KEY] = json.encodeToString(restoredSensorLogs)
            preferences[WATCH_NOTIFICATION_SCHEDULES_KEY] = json.encodeToString(payload.watchNotificationSchedules)
            preferences[GLUCOSE_ALARM_SCHEDULES_KEY] = json.encodeToString(payload.glucoseAlarmSchedules)
            
            applyPayloadToPreferences(preferences, payload)
        }
        updateBackupPayload(payload.settingsUpdatedAtMs)
        return true
    }

    suspend fun restoreHistoryBackupFromUri(uri: Uri, isHardReset: Boolean = false): Result<HistoryBackupPayload> {
        return try {
            val payloadText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IOException("Could not read selected backup file.")

            val payload = json.decodeFromString<HistoryBackupPayload>(payloadText)
            val currentPrefs = if (!isHardReset) context.dataStore.data.first() else null

            val historicalToSave = if (isHardReset) payload.historicalGlucoseArchive else {
                mergeHistoricalMeasurements(decodeList(currentPrefs!!, HISTORICAL_GLUCOSE_KEY), payload.historicalGlucoseArchive)
            }
            val capillaryToSave = if (isHardReset) payload.capillaryReadings else {
                mergeCapillaryMeasurements(decodeList(currentPrefs!!, CAPILLARY_READINGS_KEY), payload.capillaryReadings)
            }
            val insulinToSave = if (isHardReset) payload.insulinDoses else {
                mergeInsulinDoses(decodeList(currentPrefs!!, INSULIN_DOSES_KEY), payload.insulinDoses)
            }
            val sensorLogsToSave = if (isHardReset) payload.sensorLogs else {
                mergeSensorLogs(decodeList(currentPrefs!!, SENSOR_LOGS_KEY), payload.sensorLogs)
            }

            context.dataStore.edit { preferences ->
                if (isHardReset) preferences.clear()
                applyPayloadToPreferences(preferences, payload)
                
                preferences[HISTORICAL_GLUCOSE_KEY] = json.encodeToString(historicalToSave)
                preferences[CAPILLARY_READINGS_KEY] = json.encodeToString(capillaryToSave)
                preferences[INSULIN_DOSES_KEY] = json.encodeToString(insulinToSave)
                preferences[SENSOR_LOGS_KEY] = json.encodeToString(sensorLogsToSave)
                preferences[WATCH_NOTIFICATION_SCHEDULES_KEY] = json.encodeToString(payload.watchNotificationSchedules)
                preferences[GLUCOSE_ALARM_SCHEDULES_KEY] = json.encodeToString(payload.glucoseAlarmSchedules)
            }
            updateBackupPayload(payload.settingsUpdatedAtMs)
            Result.success(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(PATIENT_ID_KEY)
        }
    }

    private fun historyBackupFile(): File {
        val dir = File(context.filesDir, HISTORY_BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, HISTORY_BACKUP_FILE)
    }

    private fun saveHistoryBackupPayload(payload: HistoryBackupPayload) {
        historyBackupFile().writeText(json.encodeToString(payload))
    }

    suspend fun getCurrentBackupPayload(): HistoryBackupPayload = buildCurrentHistoryBackupPayload()

    // ========================================================================
    // 🚀 OPTIMIZACIÓN CRÍTICA: Construye el payload de settings SIN deserializar 
    // las listas grandes. Esto evita el timeout de 15s causado por parsear 
    // miles de registros de glucosa en memoria.
    // ========================================================================
    suspend fun getSettingsOnlyPayload(): HistoryBackupPayload {
        val prefs = context.dataStore.data.first()
        
        val glucoseOffsetRanges = try {
            prefs[GLUCOSE_OFFSET_RANGES_KEY]?.let { json.decodeFromString<List<GlucoseOffsetRange>>(it) } ?: getDefaultRanges()
        } catch (e: Exception) { getDefaultRanges() }

        val autoRangeOffsetModeStr = prefs[AUTO_RANGE_OFFSET_MODE_KEY]
        val autoRangeOffsetMode = if (!autoRangeOffsetModeStr.isNullOrBlank()) {
            AutoRangeOffsetMode.entries.firstOrNull { it.name == autoRangeOffsetModeStr } ?: AutoRangeOffsetMode.OFF
        } else {
            if (prefs[AUTO_RANGE_OFFSETS_ENABLED_KEY] == true) AutoRangeOffsetMode.BY_RANGE else AutoRangeOffsetMode.OFF
        }

        val watchNotificationModeStr = prefs[WATCH_NOTIFICATION_MODE_KEY]
        val watchNotificationMode = if (!watchNotificationModeStr.isNullOrBlank()) {
            WatchNotificationMode.entries.firstOrNull { it.name == watchNotificationModeStr } ?: WatchNotificationMode.OFF
        } else {
            if (prefs[WATCH_ALERTS_ENABLED_KEY] == true) WatchNotificationMode.PERIODIC_AND_SCHEDULES else WatchNotificationMode.OFF
        }

        return HistoryBackupPayload(
            historicalGlucoseArchive = emptyList(),
            capillaryReadings = emptyList(),
            insulinDoses = emptyList(),
            sensorLogs = emptyList(),
            watchNotificationSchedules = emptyList(),
            glucoseAlarmSchedules = emptyList(),
            
            settingsUpdatedAtMs = prefs[SETTINGS_UPDATED_AT_KEY],
            glucoseOffset = prefs[GLUCOSE_OFFSET_KEY] ?: 0,
            glucoseOffsetRanges = glucoseOffsetRanges,
            autoAdjustEnabled = prefs[AUTO_ADJUST_ENABLED_KEY] ?: false,
            autoRangeOffsetsEnabled = autoRangeOffsetMode != AutoRangeOffsetMode.OFF,
            autoRangeOffsetMode = autoRangeOffsetMode,
            rapidDurationMins = prefs[RAPID_DURATION_MINS_KEY] ?: 240,
            slowDurationMins = prefs[SLOW_DURATION_MINS_KEY] ?: 1440,
            icRuleConstant = prefs[IC_RULE_CONSTANT_KEY] ?: 450,
            isfRuleConstant = prefs[ISF_RULE_CONSTANT_KEY] ?: 1800,
            manualTdi = prefs[MANUAL_TDI_KEY],
            manualIsf = prefs[MANUAL_ISF_KEY],
            targetGlucose = prefs[TARGET_GLUCOSE_KEY] ?: 80,
            watchAlertsEnabled = prefs[WATCH_ALERTS_ENABLED_KEY] ?: false,
            watchNotificationMode = watchNotificationMode,
            watchAlertIntervalMinutes = (prefs[WATCH_ALERT_INTERVAL_MINUTES_KEY] ?: 60).coerceIn(5, 180),
            watchAlertStartMinute = (prefs[WATCH_ALERT_START_MINUTE_KEY] ?: 0).coerceIn(0, 59),
            lowGlucoseAlarmEnabled = prefs[LOW_GLUCOSE_ALARM_ENABLED_KEY] ?: false,
            highGlucoseAlarmEnabled = prefs[HIGH_GLUCOSE_ALARM_ENABLED_KEY] ?: false,
            useCalibratedForAlarms = prefs[USE_CALIBRATED_FOR_ALARMS_KEY] ?: true,
            historyRetentionDays = (prefs[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_HISTORY_RETENTION_DAYS).coerceIn(MIN_HISTORY_RETENTION_DAYS, MAX_HISTORY_RETENTION_DAYS),
            batteryLowThreshold = prefs[BATTERY_LOW_THRESHOLD_KEY] ?: 15,
            batteryCriticalThreshold = prefs[BATTERY_CRITICAL_THRESHOLD_KEY] ?: 5,
            disableFastRefreshOnSlowCharge = prefs[DISABLE_FAST_REFRESH_ON_SLOW_CHARGE_KEY] ?: true,
            sensorDurationDays = prefs[SENSOR_DURATION_DAYS_KEY] ?: 15
        )
    }

    suspend fun getCloudDataPayload(): HistoryBackupPayload {
        val full = buildCurrentHistoryBackupPayload()
        return full.copy(historicalGlucoseArchive = emptyList())
    }

    suspend fun restoreFromPayload(payload: HistoryBackupPayload, isHardReset: Boolean = false): Boolean {
        return try {
            val currentPrefs = if (!isHardReset) context.dataStore.data.first() else null

            val historicalToSave = if (isHardReset) payload.historicalGlucoseArchive else {
                mergeHistoricalMeasurements(decodeList(currentPrefs!!, HISTORICAL_GLUCOSE_KEY), payload.historicalGlucoseArchive)
            }
            val capillaryToSave = if (isHardReset) payload.capillaryReadings else {
                mergeCapillaryMeasurements(decodeList(currentPrefs!!, CAPILLARY_READINGS_KEY), payload.capillaryReadings)
            }
            val insulinToSave = if (isHardReset) payload.insulinDoses else {
                mergeInsulinDoses(decodeList(currentPrefs!!, INSULIN_DOSES_KEY), payload.insulinDoses)
            }
            val sensorLogsToSave = if (isHardReset) payload.sensorLogs else {
                mergeSensorLogs(decodeList(currentPrefs!!, SENSOR_LOGS_KEY), payload.sensorLogs)
            }

            context.dataStore.edit { preferences ->
                if (isHardReset) preferences.clear()
                applyPayloadToPreferences(preferences, payload)
                
                preferences[HISTORICAL_GLUCOSE_KEY] = json.encodeToString(historicalToSave)
                preferences[CAPILLARY_READINGS_KEY] = json.encodeToString(capillaryToSave)
                preferences[INSULIN_DOSES_KEY] = json.encodeToString(insulinToSave)
                preferences[SENSOR_LOGS_KEY] = json.encodeToString(sensorLogsToSave)
                preferences[WATCH_NOTIFICATION_SCHEDULES_KEY] = json.encodeToString(payload.watchNotificationSchedules)
                preferences[GLUCOSE_ALARM_SCHEDULES_KEY] = json.encodeToString(payload.glucoseAlarmSchedules)
            }
            updateBackupPayload(payload.settingsUpdatedAtMs)
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun buildCurrentHistoryBackupPayload(): HistoryBackupPayload {
        val prefs = context.dataStore.data.first()
        
        val glucoseOffsetRanges = try {
            prefs[GLUCOSE_OFFSET_RANGES_KEY]?.let { json.decodeFromString<List<GlucoseOffsetRange>>(it) } ?: getDefaultRanges()
        } catch (e: Exception) { getDefaultRanges() }

        val autoRangeOffsetModeStr = prefs[AUTO_RANGE_OFFSET_MODE_KEY]
        val autoRangeOffsetMode = if (!autoRangeOffsetModeStr.isNullOrBlank()) {
            AutoRangeOffsetMode.entries.firstOrNull { it.name == autoRangeOffsetModeStr } ?: AutoRangeOffsetMode.OFF
        } else {
            if (prefs[AUTO_RANGE_OFFSETS_ENABLED_KEY] == true) AutoRangeOffsetMode.BY_RANGE else AutoRangeOffsetMode.OFF
        }

        val watchNotificationModeStr = prefs[WATCH_NOTIFICATION_MODE_KEY]
        val watchNotificationMode = if (!watchNotificationModeStr.isNullOrBlank()) {
            WatchNotificationMode.entries.firstOrNull { it.name == watchNotificationModeStr } ?: WatchNotificationMode.OFF
        } else {
            if (prefs[WATCH_ALERTS_ENABLED_KEY] == true) WatchNotificationMode.PERIODIC_AND_SCHEDULES else WatchNotificationMode.OFF
        }

        return HistoryBackupPayload(
            historicalGlucoseArchive = decodeList(prefs, HISTORICAL_GLUCOSE_KEY),
            capillaryReadings = decodeList(prefs, CAPILLARY_READINGS_KEY),
            insulinDoses = decodeList(prefs, INSULIN_DOSES_KEY),
            sensorLogs = decodeList(prefs, SENSOR_LOGS_KEY),
            settingsUpdatedAtMs = prefs[SETTINGS_UPDATED_AT_KEY],
            glucoseOffset = prefs[GLUCOSE_OFFSET_KEY] ?: 0,
            glucoseOffsetRanges = glucoseOffsetRanges,
            autoAdjustEnabled = prefs[AUTO_ADJUST_ENABLED_KEY] ?: false,
            autoRangeOffsetsEnabled = autoRangeOffsetMode != AutoRangeOffsetMode.OFF,
            autoRangeOffsetMode = autoRangeOffsetMode,
            rapidDurationMins = prefs[RAPID_DURATION_MINS_KEY] ?: 240,
            slowDurationMins = prefs[SLOW_DURATION_MINS_KEY] ?: 1440,
            icRuleConstant = prefs[IC_RULE_CONSTANT_KEY] ?: 450,
            isfRuleConstant = prefs[ISF_RULE_CONSTANT_KEY] ?: 1800,
            manualTdi = prefs[MANUAL_TDI_KEY],
            manualIsf = prefs[MANUAL_ISF_KEY],
            targetGlucose = prefs[TARGET_GLUCOSE_KEY] ?: 80,
            watchAlertsEnabled = prefs[WATCH_ALERTS_ENABLED_KEY] ?: false,
            watchNotificationMode = watchNotificationMode,
            watchAlertIntervalMinutes = (prefs[WATCH_ALERT_INTERVAL_MINUTES_KEY] ?: 60).coerceIn(5, 180),
            watchAlertStartMinute = (prefs[WATCH_ALERT_START_MINUTE_KEY] ?: 0).coerceIn(0, 59),
            lowGlucoseAlarmEnabled = prefs[LOW_GLUCOSE_ALARM_ENABLED_KEY] ?: false,
            highGlucoseAlarmEnabled = prefs[HIGH_GLUCOSE_ALARM_ENABLED_KEY] ?: false,
            useCalibratedForAlarms = prefs[USE_CALIBRATED_FOR_ALARMS_KEY] ?: true,
            historyRetentionDays = (prefs[HISTORY_RETENTION_DAYS_KEY] ?: DEFAULT_HISTORY_RETENTION_DAYS).coerceIn(MIN_HISTORY_RETENTION_DAYS, MAX_HISTORY_RETENTION_DAYS),
            watchNotificationSchedules = decodeList(prefs, WATCH_NOTIFICATION_SCHEDULES_KEY),
            glucoseAlarmSchedules = decodeList(prefs, GLUCOSE_ALARM_SCHEDULES_KEY),
            batteryLowThreshold = prefs[BATTERY_LOW_THRESHOLD_KEY] ?: 15,
            batteryCriticalThreshold = prefs[BATTERY_CRITICAL_THRESHOLD_KEY] ?: 5,
            disableFastRefreshOnSlowCharge = prefs[DISABLE_FAST_REFRESH_ON_SLOW_CHARGE_KEY] ?: true,
            sensorDurationDays = prefs[SENSOR_DURATION_DAYS_KEY] ?: 15
        )
    }

    private fun applyPayloadToPreferences(preferences: androidx.datastore.preferences.core.MutablePreferences, payload: HistoryBackupPayload) {
        payload.glucoseOffset?.let { preferences[GLUCOSE_OFFSET_KEY] = it }
        payload.glucoseOffsetRanges?.let { preferences[GLUCOSE_OFFSET_RANGES_KEY] = json.encodeToString(it) }
        payload.autoAdjustEnabled?.let { preferences[AUTO_ADJUST_ENABLED_KEY] = it }
        payload.autoRangeOffsetsEnabled?.let { preferences[AUTO_RANGE_OFFSETS_ENABLED_KEY] = it }
        payload.autoRangeOffsetMode?.let { preferences[AUTO_RANGE_OFFSET_MODE_KEY] = it.name }
        payload.rapidDurationMins?.let { preferences[RAPID_DURATION_MINS_KEY] = it }
        payload.slowDurationMins?.let { preferences[SLOW_DURATION_MINS_KEY] = it }
        payload.icRuleConstant?.let { preferences[IC_RULE_CONSTANT_KEY] = it }
        payload.isfRuleConstant?.let { preferences[ISF_RULE_CONSTANT_KEY] = it }
        payload.manualTdi?.let { preferences[MANUAL_TDI_KEY] = it }
        payload.manualIsf?.let { preferences[MANUAL_ISF_KEY] = it }
        payload.targetGlucose?.let { preferences[TARGET_GLUCOSE_KEY] = it }
        payload.watchAlertsEnabled?.let { preferences[WATCH_ALERTS_ENABLED_KEY] = it }
        payload.watchNotificationMode?.let { preferences[WATCH_NOTIFICATION_MODE_KEY] = it.name }
        payload.watchAlertIntervalMinutes?.let { preferences[WATCH_ALERT_INTERVAL_MINUTES_KEY] = it }
        payload.watchAlertStartMinute?.let { preferences[WATCH_ALERT_START_MINUTE_KEY] = it }
        payload.lowGlucoseAlarmEnabled?.let { preferences[LOW_GLUCOSE_ALARM_ENABLED_KEY] = it }
        payload.highGlucoseAlarmEnabled?.let { preferences[HIGH_GLUCOSE_ALARM_ENABLED_KEY] = it }
        payload.useCalibratedForAlarms?.let { preferences[USE_CALIBRATED_FOR_ALARMS_KEY] = it }
        payload.historyRetentionDays?.let { preferences[HISTORY_RETENTION_DAYS_KEY] = it }
        payload.batteryLowThreshold?.let { preferences[BATTERY_LOW_THRESHOLD_KEY] = it }
        payload.batteryCriticalThreshold?.let { preferences[BATTERY_CRITICAL_THRESHOLD_KEY] = it }
        payload.disableFastRefreshOnSlowCharge?.let { preferences[DISABLE_FAST_REFRESH_ON_SLOW_CHARGE_KEY] = it }
        payload.sensorDurationDays?.let { preferences[SENSOR_DURATION_DAYS_KEY] = it }
        payload.settingsUpdatedAtMs?.let { preferences[SETTINGS_UPDATED_AT_KEY] = it }
    }

    private fun deleteExistingDownloadsBackup(relativePath: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(LOCAL_DOWNLOADS_BACKUP_FILE, relativePath)
        
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID), selection, selectionArgs, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                resolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id), null, null)
            }
        }
    }

    // --- Funciones de Fusión Optimizadas ---
    private fun mergeHistoricalMeasurements(local: List<GlucoseMeasurement>, backup: List<GlucoseMeasurement>): List<GlucoseMeasurement> {
        val mergedMap = LinkedHashMap<String, Pair<Instant?, GlucoseMeasurement>>()
        val processList = { list: List<GlucoseMeasurement> ->
            for (m in list) {
                val instant = parseFlexibleInstant(m.factoryTimestamp) ?: parseFlexibleInstant(m.timestamp)
                val key = instant?.toEpochMilli()?.toString() ?: m.timestamp
                mergedMap[key] = instant to m
            }
        }
        processList(local)
        processList(backup)

        return mergedMap.values.filter { it.first != null }.sortedByDescending { it.first!!.toEpochMilli() }.map { it.second }
    }

    private fun mergeCapillaryMeasurements(local: List<CapillaryMeasurement>, backup: List<CapillaryMeasurement>): List<CapillaryMeasurement> {
        val mergedMap = local.associateBy { it.id }.toMutableMap()
        
        for (r in backup) {
            val existing = mergedMap[r.id]
            if (existing == null || r.updatedAtMs > existing.updatedAtMs) {
                if (r.isDeleted) {
                    mergedMap.remove(r.id) // Elimina físicamente el registro si fue borrado en la nube
                } else {
                    mergedMap[r.id] = r
                }
            }
        }
        
        return mergedMap.values.sortedByDescending { it.timestamp }
    }

    private fun mergeInsulinDoses(local: List<InsulinDose>, backup: List<InsulinDose>): List<InsulinDose> {
        val mergedMap = local.associateBy { it.id }.toMutableMap()
        
        for (d in backup) {
            val existing = mergedMap[d.id]
            if (existing == null || d.updatedAtMs > existing.updatedAtMs) {
                if (d.isDeleted) {
                    mergedMap.remove(d.id) // Elimina físicamente el registro si fue borrado en la nube
                } else {
                    mergedMap[d.id] = d
                }
            }
        }
        
        return mergedMap.values.sortedByDescending { it.timestamp }
    }

    private fun mergeSensorLogs(local: List<SensorLog>, backup: List<SensorLog>): List<SensorLog> {
        val mergedMap = local.associateBy { it.serialNumber }.toMutableMap()
        
        for (log in backup) {
            val existing = mergedMap[log.serialNumber]
            if (existing == null || log.updatedAtMs > existing.updatedAtMs) {
                mergedMap[log.serialNumber] = log
            }
        }
        
        return mergedMap.values.sortedByDescending { it.startDate }
    }

    private fun parseFlexibleInstant(timestamp: String): Instant? = TimestampParser.parseFlexibleInstant(timestamp)
}