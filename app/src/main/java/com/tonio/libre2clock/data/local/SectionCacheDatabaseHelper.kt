package com.tonio.libre2clock.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement

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
                return it.getString(0)
            }
        }
        return null
    }

    fun upsertPayload(sectionKey: String, signature: String, payloadJson: String) {
        // 3. OPTIMIZACIÓN: Usar la sentencia pre-compilada en lugar de ContentValues
        upsertStatement.bindString(1, sectionKey)
        upsertStatement.bindString(2, signature)
        upsertStatement.bindString(3, payloadJson)
        upsertStatement.bindLong(4, System.currentTimeMillis())
        
        upsertStatement.execute()
    }

    fun purgeOlderThan(cutoffEpochMs: Long): Int {
        return writableDatabase.delete(
            "section_cache",
            "updated_at_epoch_ms < ?",
            arrayOf(cutoffEpochMs.toString())
        )
    }

    fun clearAll(): Int {
        // 4. OPTIMIZACIÓN: execSQL es ligeramente más directo que delete() para borrados totales
        writableDatabase.execSQL("DELETE FROM section_cache")
        // Opcional: reiniciar el contador de auto-incremento si existiera, 
        // pero como usamos PRIMARY KEY compuesto, no es necesario.
        return 0 // O podríamos devolver el número de filas, pero execSQL no lo devuelve directamente.
    }

    companion object {
        private const val DATABASE_NAME = "section_cache.db"
        private const val DATABASE_VERSION = 5
    }
}