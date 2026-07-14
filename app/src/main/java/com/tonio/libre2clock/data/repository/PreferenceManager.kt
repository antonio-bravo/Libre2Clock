package com.tonio.libre2clock.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val GLUCOSE_OFFSET_KEY = androidx.datastore.preferences.core.intPreferencesKey("glucose_offset")
    private val GLUCOSE_OFFSET_RANGES_KEY = stringPreferencesKey("glucose_offset_ranges")
    private val AUTO_ADJUST_ENABLED_KEY = booleanPreferencesKey("auto_adjust_enabled")
    private val CAPILLARY_READINGS_KEY = stringPreferencesKey("capillary_readings")
    private val WATCH_ALERTS_ENABLED_KEY = booleanPreferencesKey("watch_alerts_enabled")
    private val WATCH_ALERT_INTERVAL_MINUTES_KEY = androidx.datastore.preferences.core.intPreferencesKey("watch_alert_interval_minutes")
    private val LOW_GLUCOSE_ALARM_ENABLED_KEY = booleanPreferencesKey("low_glucose_alarm_enabled")
    private val HIGH_GLUCOSE_ALARM_ENABLED_KEY = booleanPreferencesKey("high_glucose_alarm_enabled")

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

    val lowGlucoseAlarmEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOW_GLUCOSE_ALARM_ENABLED_KEY] ?: false
    }

    val highGlucoseAlarmEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HIGH_GLUCOSE_ALARM_ENABLED_KEY] ?: false
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
    }

    suspend fun saveWatchAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WATCH_ALERTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveWatchAlertIntervalMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[WATCH_ALERT_INTERVAL_MINUTES_KEY] = minutes.coerceIn(5, 180)
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

    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
        }
    }
}
