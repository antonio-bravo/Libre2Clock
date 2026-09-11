package com.tonio.libre2clock.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.util.TimestampParser

class GlucoseHistoryDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // Sentencia pre-compilada para inserciones masivas ultrarrápidas (evita ContentValues/HashMap)
    private val upsertStatementSql = """
        INSERT OR REPLACE INTO glucose_history (
            measurement_id, sort_epoch_ms, factory_timestamp, timestamp,
            measurement_type, value_mg_dl, trend_arrow, measurement_color,
            raw_value, calibrated_value, updated_at_epoch_ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE glucose_history (
                measurement_id TEXT PRIMARY KEY,
                sort_epoch_ms INTEGER NOT NULL,
                factory_timestamp TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                measurement_type INTEGER NOT NULL,
                value_mg_dl INTEGER NOT NULL,
                trend_arrow INTEGER,
                measurement_color INTEGER,
                raw_value INTEGER NOT NULL,
                calibrated_value INTEGER NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE system_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                level TEXT NOT NULL,
                tag TEXT NOT NULL,
                message TEXT NOT NULL,
                detail TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_glucose_history_sort_epoch ON glucose_history(sort_epoch_ms DESC)")
        db.execSQL("CREATE INDEX idx_glucose_history_window ON glucose_history(sort_epoch_ms DESC, raw_value)")
        db.execSQL("CREATE INDEX idx_system_events_timestamp ON system_events(timestamp DESC)")
        db.execSQL("PRAGMA optimize;")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            onCreate(db)
            return
        }
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_glucose_history_window ON glucose_history(sort_epoch_ms DESC, raw_value)")
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS system_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    level TEXT NOT NULL,
                    tag TEXT NOT NULL,
                    message TEXT NOT NULL,
                    detail TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_system_events_timestamp ON system_events(timestamp DESC)")
        }
        db.execSQL("PRAGMA optimize;")
    }

    // --- OPTIMIZACIÓN CRÍTICA: Lee solo los N registros más recientes directamente desde SQLite ---
    // Esto evita cargar 50,000 registros en RAM solo para hacer un .take(200) después.
    fun readRecentHistory(limit: Int = 200): List<GlucoseMeasurement> {
        val db = readableDatabase
        val cursor = db.query(
            "glucose_history",
            arrayOf(
                "factory_timestamp", "timestamp", "measurement_type", "value_mg_dl",
                "trend_arrow", "measurement_color", "raw_value", "calibrated_value", "sort_epoch_ms"
            ),
            null, null, null, null,
            "sort_epoch_ms DESC",
            limit.toString() // El límite se aplica en la base de datos, no en la memoria
        )

        cursor.use {
            // Usamos una capacidad inicial razonable en lugar de it.count (que es una operación O(N) lenta)
            val items = ArrayList<GlucoseMeasurement>(limit)
            while (it.moveToNext()) {
                items.add(mapCursorToMeasurement(it))
            }
            return items
        }
    }

    fun readAllNewestFirst(): List<GlucoseMeasurement> {
        val db = readableDatabase
        val cursor = db.query(
            "glucose_history",
            arrayOf(
                "factory_timestamp", "timestamp", "measurement_type", "value_mg_dl",
                "trend_arrow", "measurement_color", "raw_value", "calibrated_value", "sort_epoch_ms"
            ),
            null, null, null, null,
            "sort_epoch_ms DESC"
        )

        cursor.use {
            val items = ArrayList<GlucoseMeasurement>(1000) // Capacidad inicial razonable
            while (it.moveToNext()) {
                items.add(mapCursorToMeasurement(it))
            }
            return items
        }
    }

    fun readLatest(): GlucoseMeasurement? {
        val db = readableDatabase
        val cursor = db.query(
            "glucose_history",
            arrayOf(
                "factory_timestamp", "timestamp", "measurement_type", "value_mg_dl",
                "trend_arrow", "measurement_color", "raw_value", "calibrated_value", "sort_epoch_ms"
            ),
            null, null, null, null,
            "sort_epoch_ms DESC",
            "1"
        )

        cursor.use {
            if (!it.moveToFirst()) return null
            return mapCursorToMeasurement(it)
        }
    }

    fun readWindowNewestFirst(
        startEpochMs: Long,
        endEpochMs: Long,
        maxItems: Int = 5000
    ): List<GlucoseMeasurement> {
        val db = readableDatabase
        val cursor = db.query(
            "glucose_history",
            arrayOf(
                "factory_timestamp", "timestamp", "measurement_type", "value_mg_dl",
                "trend_arrow", "measurement_color", "raw_value", "calibrated_value", "sort_epoch_ms"
            ),
            "sort_epoch_ms >= ? AND sort_epoch_ms <= ?",
            arrayOf(startEpochMs.toString(), endEpochMs.toString()),
            null, null,
            "sort_epoch_ms DESC",
            maxItems.coerceAtLeast(1).toString()
        )

        cursor.use {
            val items = ArrayList<GlucoseMeasurement>(maxItems)
            while (it.moveToNext()) {
                items.add(mapCursorToMeasurement(it))
            }
            return items
        }
    }

    fun replaceAll(measurements: List<GlucoseMeasurement>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("glucose_history", null, null)
            val statement = db.compileStatement(upsertStatementSql)
            measurements.forEach { bindAndExecute(statement, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertAll(measurements: List<GlucoseMeasurement>) {
        if (measurements.isEmpty()) return
        
        val db = writableDatabase
        db.beginTransaction()
        try {
            val statement = db.compileStatement(upsertStatementSql)
            measurements.forEach { bindAndExecute(statement, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun pruneOlderThan(cutoffEpochMs: Long) {
        writableDatabase.delete("glucose_history", "sort_epoch_ms < ?", arrayOf(cutoffEpochMs.toString()))
    }

    fun removeDuplicates() {
        val db = writableDatabase
        db.execSQL(
            """
            DELETE FROM glucose_history 
            WHERE rowid NOT IN (
                SELECT MIN(rowid) 
                FROM glucose_history 
                GROUP BY sort_epoch_ms, raw_value
            )
            """.trimIndent()
        )
    }

    // OPTIMIZACIÓN: O(1) en lugar de O(N) con COUNT(1)
    fun isEmpty(): Boolean {
        val cursor = readableDatabase.rawQuery("SELECT 1 FROM glucose_history LIMIT 1", null)
        cursor.use {
            return !it.moveToFirst()
        }
    }

    // --- Métodos Privados de Optimización ---

    private fun mapCursorToMeasurement(cursor: android.database.Cursor): GlucoseMeasurement {
        return GlucoseMeasurement(
            factoryTimestamp = cursor.getString(0),
            timestamp = cursor.getString(1),
            type = cursor.getInt(2),
            valueInMgPerDl = cursor.getInt(3),
            trendArrow = if (cursor.isNull(4)) null else cursor.getInt(4),
            measurementColor = if (cursor.isNull(5)) null else cursor.getInt(5),
            value = cursor.getInt(6),
            calibratedValue = cursor.getInt(7),
            epochSeconds = cursor.getLong(8) / 1000L
        )
    }

    private fun bindAndExecute(statement: SQLiteStatement, measurement: GlucoseMeasurement) {
        val instant = TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp)
            ?: TimestampParser.parseFlexibleInstant(measurement.timestamp)
        val sortEpoch = instant?.toEpochMilli() ?: 0L

        val measurementId = if (instant != null) {
            "${instant.toEpochMilli()}-${measurement.value}"
        } else {
            "${measurement.factoryTimestamp}-${measurement.timestamp}-${measurement.value}"
        }

        statement.bindString(1, measurementId)
        statement.bindLong(2, sortEpoch)
        statement.bindString(3, measurement.factoryTimestamp)
        statement.bindString(4, measurement.timestamp)
        statement.bindLong(5, measurement.type.toLong())
        statement.bindLong(6, measurement.valueInMgPerDl.toLong())
        
        if (measurement.trendArrow == null) statement.bindNull(7) else statement.bindLong(7, measurement.trendArrow.toLong())
        if (measurement.measurementColor == null) statement.bindNull(8) else statement.bindLong(8, measurement.measurementColor.toLong())
        
        statement.bindLong(9, measurement.value.toLong())
        statement.bindLong(10, measurement.calibratedValue.toLong())
        statement.bindLong(11, System.currentTimeMillis())

        statement.execute()
    }

    companion object {
        private const val DATABASE_NAME = "glucose_history.db"
        private const val DATABASE_VERSION = 3
    }
}