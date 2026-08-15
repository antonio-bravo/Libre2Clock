package com.tonio.libre2clock.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.util.TimestampParser

class GlucoseHistoryDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

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
        db.execSQL("CREATE INDEX idx_glucose_history_sort_epoch ON glucose_history(sort_epoch_ms DESC)")
        db.execSQL("CREATE INDEX idx_glucose_history_window ON glucose_history(sort_epoch_ms DESC, raw_value)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            onCreate(db)
            return
        }
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_glucose_history_window ON glucose_history(sort_epoch_ms DESC, raw_value)")
        }
    }

    fun readAllNewestFirst(): List<GlucoseMeasurement> {
        val db = readableDatabase
        val cursor = db.query(
            "glucose_history",
            arrayOf(
                "factory_timestamp",
                "timestamp",
                "measurement_type",
                "value_mg_dl",
                "trend_arrow",
                "measurement_color",
                "raw_value",
                "calibrated_value",
                "sort_epoch_ms"
            ),
            null,
            null,
            null,
            null,
            "sort_epoch_ms DESC"
        )

        cursor.use {
            val items = ArrayList<GlucoseMeasurement>(it.count.coerceAtLeast(0))
            while (it.moveToNext()) {
                items.add(
                    GlucoseMeasurement(
                        factoryTimestamp = it.getString(0),
                        timestamp = it.getString(1),
                        type = it.getInt(2),
                        valueInMgPerDl = it.getInt(3),
                        trendArrow = it.takeIf { row -> !row.isNull(4) }?.getInt(4),
                        measurementColor = it.takeIf { row -> !row.isNull(5) }?.getInt(5),
                        value = it.getInt(6),
                        calibratedValue = it.getInt(7),
                        epochSeconds = it.getLong(8) / 1000L
                    )
                )
            }
            return items
        }
    }

    fun readLatest(): GlucoseMeasurement? {
        val db = readableDatabase
        val cursor = db.query(
            "glucose_history",
            arrayOf(
                "factory_timestamp",
                "timestamp",
                "measurement_type",
                "value_mg_dl",
                "trend_arrow",
                "measurement_color",
                "raw_value",
                "calibrated_value",
                "sort_epoch_ms"
            ),
            null,
            null,
            null,
            null,
            "sort_epoch_ms DESC",
            "1"
        )

        cursor.use {
            if (!it.moveToFirst()) return null
            return GlucoseMeasurement(
                factoryTimestamp = it.getString(0),
                timestamp = it.getString(1),
                type = it.getInt(2),
                valueInMgPerDl = it.getInt(3),
                trendArrow = it.takeIf { row -> !row.isNull(4) }?.getInt(4),
                measurementColor = it.takeIf { row -> !row.isNull(5) }?.getInt(5),
                value = it.getInt(6),
                calibratedValue = it.getInt(7),
                epochSeconds = it.getLong(8) / 1000L
            )
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
                "factory_timestamp",
                "timestamp",
                "measurement_type",
                "value_mg_dl",
                "trend_arrow",
                "measurement_color",
                "raw_value",
                "calibrated_value",
                "sort_epoch_ms"
            ),
            "sort_epoch_ms >= ? AND sort_epoch_ms <= ?",
            arrayOf(startEpochMs.toString(), endEpochMs.toString()),
            null,
            null,
            "sort_epoch_ms DESC",
            maxItems.coerceAtLeast(1).toString()
        )

        cursor.use {
            val items = ArrayList<GlucoseMeasurement>(it.count.coerceAtLeast(0))
            while (it.moveToNext()) {
                items.add(
                    GlucoseMeasurement(
                        factoryTimestamp = it.getString(0),
                        timestamp = it.getString(1),
                        type = it.getInt(2),
                        valueInMgPerDl = it.getInt(3),
                        trendArrow = it.takeIf { row -> !row.isNull(4) }?.getInt(4),
                        measurementColor = it.takeIf { row -> !row.isNull(5) }?.getInt(5),
                        value = it.getInt(6),
                        calibratedValue = it.getInt(7),
                        epochSeconds = it.getLong(8) / 1000L
                    )
                )
            }
            return items
        }
    }

    fun replaceAll(measurements: List<GlucoseMeasurement>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("glucose_history", null, null)
            measurements.forEach { upsertInternal(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertAll(measurements: List<GlucoseMeasurement>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            measurements.forEach { upsertInternal(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun pruneOlderThan(cutoffEpochMs: Long) {
        writableDatabase.delete(
            "glucose_history",
            "sort_epoch_ms < ?",
            arrayOf(cutoffEpochMs.toString())
        )
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

    fun isEmpty(): Boolean {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(1) FROM glucose_history", null)
        cursor.use {
            if (!it.moveToFirst()) return true
            return it.getLong(0) == 0L
        }
    }

    private fun upsertInternal(db: SQLiteDatabase, measurement: GlucoseMeasurement) {
        val instant = TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp)
            ?: TimestampParser.parseFlexibleInstant(measurement.timestamp)
        val sortEpoch = instant?.toEpochMilli() ?: 0L

        // CRITICAL: The ID must be unique to the measurement event, NOT its current calibration state.
        // Including calibratedValue in the ID causes duplicates when offsets are adjusted.
        val measurementId = if (instant != null) {
            "${instant.toEpochMilli()}-${measurement.value}"
        } else {
            "${measurement.factoryTimestamp}-${measurement.timestamp}-${measurement.value}"
        }

        val values = ContentValues().apply {
            put("measurement_id", measurementId)
            put("sort_epoch_ms", sortEpoch)
            put("factory_timestamp", measurement.factoryTimestamp)
            put("timestamp", measurement.timestamp)
            put("measurement_type", measurement.type)
            put("value_mg_dl", measurement.valueInMgPerDl)
            if (measurement.trendArrow == null) putNull("trend_arrow") else put("trend_arrow", measurement.trendArrow)
            if (measurement.measurementColor == null) putNull("measurement_color") else put("measurement_color", measurement.measurementColor)
            put("raw_value", measurement.value)
            put("calibrated_value", measurement.calibratedValue)
            put("updated_at_epoch_ms", System.currentTimeMillis())
        }

        db.insertWithOnConflict(
            "glucose_history",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    companion object {
        private const val DATABASE_NAME = "glucose_history.db"
        private const val DATABASE_VERSION = 2
    }
}
