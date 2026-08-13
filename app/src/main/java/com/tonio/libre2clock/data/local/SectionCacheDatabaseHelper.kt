package com.tonio.libre2clock.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SectionCacheDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE section_cache (
                section_key TEXT PRIMARY KEY,
                signature TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_section_cache_updated_at ON section_cache(updated_at_epoch_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS section_cache")
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_section_cache_updated_at ON section_cache(updated_at_epoch_ms)")
        }
    }

    fun getCachedPayload(sectionKey: String, signature: String): String? {
        val db = readableDatabase
        val cursor = db.query(
            "section_cache",
            arrayOf("payload_json"),
            "section_key = ? AND signature = ?",
            arrayOf(sectionKey, signature),
            null,
            null,
            null
        )

        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getString(0)
        }
    }

    fun upsertPayload(sectionKey: String, signature: String, payloadJson: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("section_key", sectionKey)
            put("signature", signature)
            put("payload_json", payloadJson)
            put("updated_at_epoch_ms", System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            "section_cache",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun purgeOlderThan(cutoffEpochMs: Long): Int {
        return writableDatabase.delete(
            "section_cache",
            "updated_at_epoch_ms < ?",
            arrayOf(cutoffEpochMs.toString())
        )
    }

    companion object {
        private const val DATABASE_NAME = "section_cache.db"
        private const val DATABASE_VERSION = 3
    }
}
