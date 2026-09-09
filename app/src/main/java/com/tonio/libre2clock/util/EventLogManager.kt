package com.tonio.libre2clock.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tonio.libre2clock.data.repository.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class LogEvent(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val detail: String? = null
)

enum class LogLevel { INFO, WARNING, ERROR }

class EventLogManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val EVENTS_KEY = stringPreferencesKey("system_events_log")
    private val MAX_EVENTS = 200

    private val json = Json { ignoreUnknownKeys = true }
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    val events: Flow<List<LogEvent>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[EVENTS_KEY] ?: "[]"
        try {
            json.decodeFromString<List<LogEvent>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun log(level: LogLevel, tag: String, message: String, detail: String? = null) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val currentJson = prefs[EVENTS_KEY] ?: "[]"
                val currentList = try {
                    json.decodeFromString<MutableList<LogEvent>>(currentJson)
                } catch (e: Exception) {
                    mutableListOf()
                }

                currentList.add(0, LogEvent(System.currentTimeMillis(), level, tag, message, detail))
                
                // Keep only the last N events
                val trimmed = currentList.take(MAX_EVENTS)
                prefs[EVENTS_KEY] = json.encodeToString(trimmed)
            }
        }
    }

    fun formatTimestamp(epochMs: Long): String = formatter.format(Instant.ofEpochMilli(epochMs))

    suspend fun clear() {
        context.dataStore.edit { it.remove(EVENTS_KEY) }
    }
}
