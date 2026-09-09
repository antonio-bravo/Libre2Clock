package com.tonio.libre2clock.util

import android.content.ContentValues
import android.content.Context
import com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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

class EventLogManager(context: Context) {
    private val dbHelper = GlucoseHistoryDatabaseHelper(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableStateFlow<List<LogEvent>>(emptyList())
    val events: StateFlow<List<LogEvent>> = _events.asStateFlow()

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        refresh()
    }

    fun log(level: LogLevel, tag: String, message: String, detail: String? = null) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("timestamp", System.currentTimeMillis())
                put("level", level.name)
                put("tag", tag)
                put("message", message)
                put("detail", detail)
            }
            db.insert("system_events", null, values)
            
            // Prune old events (keep last 500)
            db.execSQL("DELETE FROM system_events WHERE id NOT IN (SELECT id FROM system_events ORDER BY timestamp DESC LIMIT 500)")
            
            refresh()
        }
    }

    private fun refresh() {
        scope.launch {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "system_events",
                null, null, null, null, null,
                "timestamp DESC"
            )
            val list = mutableListOf<LogEvent>()
            cursor.use {
                while (it.moveToNext()) {
                    list.add(LogEvent(
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        level = LogLevel.valueOf(it.getString(it.getColumnIndexOrThrow("level"))),
                        tag = it.getString(it.getColumnIndexOrThrow("tag")),
                        message = it.getString(it.getColumnIndexOrThrow("message")),
                        detail = it.getString(it.getColumnIndexOrThrow("detail"))
                    ))
                }
            }
            _events.value = list
        }
    }

    fun formatTimestamp(epochMs: Long): String = formatter.format(Instant.ofEpochMilli(epochMs))

    fun clear() {
        scope.launch {
            dbHelper.writableDatabase.delete("system_events", null, null)
            _events.value = emptyList()
        }
    }
}
