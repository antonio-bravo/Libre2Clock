package com.tonio.libre2clock.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores LibreLinkUp credentials encrypted at rest (Android Keystore-backed),
 * used only to silently re-authenticate when the session token expires.
 */
class SecureCredentialStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredentials(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getCredentials(): Pair<String, String>? {
        val email = prefs.getString(KEY_EMAIL, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        return if (email != null && password != null) email to password else null
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "secure_credentials"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
    }
}
