package com.tonio.libre2clock.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class SectionCacheDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // 1. OPTIMIZACIÓN: Reutilizar el array de proyección evita asignar memoria en cada lectura
    private val projection = arrayOf("payload_json")

    // 2. OPTIMIZACIÓN: Sentencia pre-compilada para inserciones/actualizaciones ultrarrápidas
    // Evita el overhead de crear objetos ContentValues (HashMap) en cada llamada.
    private val upsertStatement: SQLiteStatement by lazy {
        writableDatabase.compileStatement("""
            INSERT OR REPLACE INTO section_cache 
            (section_key, signature, payload_json, updated_at_epoch_ms) 
            VALUES (?, ?, ?, ?)
        """.trimIndent())
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE section_cache (
                section_key TEXT NOT NULL,
                signature TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL,
                PRIMARY KEY (section_key, signature)
            )
            """.trimIndent()
        )
        // El índice es útil para la purga, pero la PRIMARY KEY ya indexa (section_key, signature)
        db.execSQL("CREATE INDEX idx_section_cache_updated_at ON section_cache(updated_at_epoch_ms)")
        db.execSQL("PRAGMA optimize;")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Para una tabla de caché, la estrategia "nuke and pave" (borrar y recrear) 
        // es la más segura y rápida ante cambios de esquema.
        if (oldVersion < 5) {
            db.execSQL("DROP TABLE IF EXISTS section_cache")
            onCreate(db)
        }
    }

    fun getCachedPayload(sectionKey: String, signature: String): String? {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                "section_cache",
                projection, // Usamos el array pre-allocado
                "section_key = ? AND signature = ?",
                arrayOf(sectionKey, signature),
                null,
                null,
                null
            )

            cursor.use {
                if (it.moveToFirst()) {
                    val raw = it.getString(0)
                    return decompressIfNeeded(raw)
                }
            }
            null
        } catch (_: Exception) {
            // Manejar SQLiteBlobTooBigException u otros errores de lectura en SQLite Cursor
            // Purga el registro sobredimensionado o corrupto para prevenir cierres inesperados
            try {
                writableDatabase.delete(
                    "section_cache",
                    "section_key = ? AND signature = ?",
                    arrayOf(sectionKey, signature)
                )
            } catch (_: Exception) {}
            null
        }
    }

    fun getLatestCachedPayload(sectionKey: String): String? {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                "section_cache",
                projection,
                "section_key = ?",
                arrayOf(sectionKey),
                null,
                null,
                "updated_at_epoch_ms DESC",
                "1"
            )

            cursor.use {
                if (it.moveToFirst()) {
                    val raw = it.getString(0)
                    return decompressIfNeeded(raw)
                }
            }
            null
        } catch (_: Exception) {
            try {
                writableDatabase.delete(
                    "section_cache",
                    "section_key = ?",
                    arrayOf(sectionKey)
                )
            } catch (_: Exception) {}
            null
        }
    }

    fun upsertPayload(sectionKey: String, signature: String, payloadJson: String) {
        val payloadToStore = compressIfNeeded(payloadJson)
        if (payloadToStore.length > MAX_SAFE_PAYLOAD_CHAR_COUNT) {
            // Si incluso comprimido supera los ~1.8MB, omitimos la persistencia en DB para evitar CursorWindow limits
            return
        }
        try {
            upsertStatement.bindString(1, sectionKey)
            upsertStatement.bindString(2, signature)
            upsertStatement.bindString(3, payloadToStore)
            upsertStatement.bindLong(4, System.currentTimeMillis())
            
            upsertStatement.execute()
        } catch (e: Exception) {
            // Ignorar fallos de escritura en DB para no interrumpir la experiencia de usuario
        }
    }

    fun purgeOlderThan(cutoffEpochMs: Long): Int {
        return writableDatabase.delete(
            "section_cache",
            "updated_at_epoch_ms < ?",
            arrayOf(cutoffEpochMs.toString())
        )
    }

    fun clearAll(): Int {
        writableDatabase.execSQL("DELETE FROM section_cache")
        return 0
    }

    private fun compressIfNeeded(text: String): String {
        if (text.length < COMPRESSION_THRESHOLD_CHARS) return text
        return try {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { gzip ->
                gzip.write(text.toByteArray(Charsets.UTF_8))
            }
            val compressedBytes = bos.toByteArray()
            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            "$GZIP_PREFIX$base64"
        } catch (e: Exception) {
            text
        }
    }

    private fun decompressIfNeeded(raw: String): String {
        if (!raw.startsWith(GZIP_PREFIX)) return raw
        return try {
            val base64Part = raw.substring(GZIP_PREFIX.length)
            val compressedBytes = Base64.decode(base64Part, Base64.NO_WRAP)
            val bis = ByteArrayInputStream(compressedBytes)
            GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        } catch (e: Exception) {
            raw
        }
    }

    companion object {
        private const val DATABASE_NAME = "section_cache.db"
        private const val DATABASE_VERSION = 5
        private const val GZIP_PREFIX = "GZIP_BASE64:"
        private const val COMPRESSION_THRESHOLD_CHARS = 10_000 // Comprimir payloads >= 10 KB
        private const val MAX_SAFE_PAYLOAD_CHAR_COUNT = 1_800_000 // 1.8 MB para prevenir SQLiteBlobTooBigException en CursorWindow
    }
}