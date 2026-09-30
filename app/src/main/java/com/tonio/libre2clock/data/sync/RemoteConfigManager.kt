package com.tonio.libre2clock.data.sync

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import org.json.JSONArray

class RemoteConfigManager private constructor() {

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance()
    }

    init {
        try {
            val configSettings = remoteConfigSettings {
                // En producción busca cambios cada hora (3600s); durante desarrollo 0s para pruebas rápidas
                minimumFetchIntervalInSeconds = 3600
            }
            remoteConfig.setConfigSettingsAsync(configSettings)

            val defaults = mapOf<String, Any>(
                KEY_WHITELISTED_EMAILS to "[]"
            )
            remoteConfig.setDefaultsAsync(defaults)
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar RemoteConfigManager", e)
        }
    }

    fun fetchAndActivate(onComplete: ((Boolean) -> Unit)? = null) {
        try {
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    val success = task.isSuccessful
                    if (success) {
                        Log.d(TAG, "RemoteConfig actualizado correctamente. Whitelist = ${getWhitelistedEmailsRaw()}")
                    } else {
                        Log.w(TAG, "Error al actualizar RemoteConfig", task.exception)
                    }
                    onComplete?.invoke(success)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al hacer fetchAndActivate en RemoteConfig", e)
            onComplete?.invoke(false)
        }
    }

    fun getWhitelistedEmailsRaw(): String {
        return try {
            remoteConfig.getString(KEY_WHITELISTED_EMAILS)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun isWhitelistActive(): Boolean {
        val raw = getWhitelistedEmailsRaw().trim()
        return raw != "[]" && raw.isNotBlank()
    }

    fun isEmailWhitelisted(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val cleanEmail = email.trim().lowercase()
        val rawConfig = getWhitelistedEmailsRaw().trim()

        if (rawConfig.isBlank()) return false

        // 1. Intentar parsear como JSON Array ["email1", "email2"]
        try {
            if (rawConfig.startsWith("[")) {
                val jsonArray = JSONArray(rawConfig)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optString(i, "").trim().lowercase()
                    if (item == cleanEmail) {
                        Log.d(TAG, "Email '$cleanEmail' encontrado en whitelist (JSON)")
                        return true
                    }
                }
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo parsear whitelist como JSON array, probando texto plano", e)
        }

        // 2. Fallback: Parsear como lista separada por comas "email1, email2"
        val items = rawConfig.split(",").map { it.trim().lowercase() }
        val found = items.contains(cleanEmail)
        if (found) {
            Log.d(TAG, "Email '$cleanEmail' encontrado en whitelist (CSV)")
        }
        return found
    }

    companion object {
        private const val TAG = "RemoteConfigManager"
        const val KEY_WHITELISTED_EMAILS = "whitelisted_emails"

        @Volatile
        private var instance: RemoteConfigManager? = null

        fun getInstance(): RemoteConfigManager {
            return instance ?: synchronized(this) {
                instance ?: RemoteConfigManager().also { instance = it }
            }
        }
    }
}
