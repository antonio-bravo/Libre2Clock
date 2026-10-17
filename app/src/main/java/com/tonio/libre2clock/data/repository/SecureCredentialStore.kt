package com.tonio.libre2clock.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores LibreLinkUp credentials encrypted at rest (Android Keystore-backed),
 * used only to silently re-authenticate when the session token expires.
 */
@Suppress("DEPRECATION")
class SecureCredentialStore(private val context: Context) {

    private val prefs: SharedPreferences? by lazy {
        createEncryptedPrefs(context)
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
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
        } catch (e: Exception) {
            Log.e("SecureCredentialStore", "Failed to initialize EncryptedSharedPreferences, resetting...", e)
            try {
                // Remove corrupted prefs file and recreate cleanly
                context.deleteSharedPreferences(PREFS_FILE_NAME)
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
            } catch (e2: Exception) {
                Log.e("SecureCredentialStore", "Critical failure initializing EncryptedSharedPreferences", e2)
                null
            }
        }
    }

    fun saveCredentials(email: String, password: String) {
        try {
            prefs?.edit()
                ?.putString(KEY_EMAIL, email)
                ?.putString(KEY_PASSWORD, password)
                ?.apply()
        } catch (e: Exception) {
            Log.e("SecureCredentialStore", "Failed to save credentials", e)
        }
    }

    fun getCredentials(): Pair<String, String>? {
        return try {
            val email = prefs?.getString(KEY_EMAIL, null)
            val password = prefs?.getString(KEY_PASSWORD, null)
            if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                email to password
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SecureCredentialStore", "Failed to read credentials", e)
            null
        }
    }

    fun clearCredentials() {
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            Log.e("SecureCredentialStore", "Failed to clear credentials", e)
        }
    }

    fun saveDexcomCredentials(accountName: String, password: String) {
        try {
            prefs?.edit()
                ?.putString(KEY_DEXCOM_ACCOUNT_NAME, accountName)
                ?.putString(KEY_DEXCOM_PASSWORD, password)
                ?.apply()
        } catch (e: Exception) {
            Log.e("SecureCredentialStore", "Failed to save Dexcom credentials", e)
        }
    }

    fun getDexcomCredentials(): Pair<String, String>? {
        return try {
            val accountName = prefs?.getString(KEY_DEXCOM_ACCOUNT_NAME, null)
            val password = prefs?.getString(KEY_DEXCOM_PASSWORD, null)
            if (!accountName.isNullOrBlank() && !password.isNullOrBlank()) {
                accountName to password
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SecureCredentialStore", "Failed to read Dexcom credentials", e)
            null
        }
    }

    fun clearDexcomCredentials() {
        try {
            prefs?.edit()
                ?.remove(KEY_DEXCOM_ACCOUNT_NAME)
                ?.remove(KEY_DEXCOM_PASSWORD)
                ?.apply()
        } catch (e: Exception) {
            Log.e("SecureCredentialStore", "Failed to clear Dexcom credentials", e)
        }
    }

    private companion object {
        const val PREFS_FILE_NAME = "secure_credentials"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
        const val KEY_DEXCOM_ACCOUNT_NAME = "dexcom_account_name"
        const val KEY_DEXCOM_PASSWORD = "dexcom_password"
    }
}
