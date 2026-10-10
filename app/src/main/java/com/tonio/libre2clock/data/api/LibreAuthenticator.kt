package com.tonio.libre2clock.data.api

import android.util.Log
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.SecureCredentialStore
import com.tonio.libre2clock.util.EventLogManager
import com.tonio.libre2clock.util.LogLevel
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException

/**
 * Thread-safe OkHttp Authenticator for automatic transparent token refresh
 * when LibreLinkUp returns HTTP 401 Unauthorized.
 */
class LibreAuthenticator(
    private val credentialStore: SecureCredentialStore,
    private val preferenceManager: PreferenceManager,
    private val eventLogger: EventLogManager? = null
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath

        // Do not intercept failing login requests to prevent recursive retry loops
        if (path.contains("llu/auth/login")) {
            return null
        }

        // Limit retries per request to prevent infinite loops
        if (responseCount(response) >= 3) {
            Log.w("LibreAuthenticator", "Reached maximum reauth retry attempts for ${response.request.url}")
            return null
        }

        synchronized(this) {
            val currentAuthToken = LibreService.getAuthToken()
            val requestAuthHeader = response.request.header("Authorization")
            val requestToken = requestAuthHeader?.removePrefix("Bearer ")?.trim()

            // If another thread already refreshed the token while this thread was waiting, retry with the new token
            if (!currentAuthToken.isNullOrBlank() && currentAuthToken != requestToken) {
                Log.d("LibreAuthenticator", "Token was already refreshed by another thread. Retrying request.")
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAuthToken")
                    .apply {
                        LibreService.getUserId()?.let { userId ->
                            header("Account-Id", LibreService.sha256(userId))
                        }
                    }
                    .build()
            }

            Log.i("LibreAuthenticator", "HTTP 401 detected on ${response.request.url}. Attempting silent token renewal...")

            val credentials = credentialStore.getCredentials()
            if (credentials == null) {
                Log.w("LibreAuthenticator", "No credentials stored in SecureCredentialStore. Cannot renew token.")
                onSessionExpired("No hay credenciales guardadas para renovar la sesión.")
                return null
            }

            val (email, password) = credentials
            val reauthResult = LibreService.performSilentReauth(email, password)

            return if (reauthResult.isSuccess) {
                val loginData = reauthResult.getOrNull()
                val newToken = loginData?.authTicket?.token
                val newUserId = loginData?.user?.id

                if (!newToken.isNullOrBlank() && !newUserId.isNullOrBlank()) {
                    Log.i("LibreAuthenticator", "Silent token renewal SUCCESSFUL!")
                    eventLogger?.log(
                        LogLevel.INFO,
                        "AUTH",
                        "Renovación automática de token LibreLinkUp exitosa",
                        "El token fue renovado transparentemente tras caducar."
                    )

                    // Persist newly acquired session token
                    runBlocking {
                        preferenceManager.saveAuth(newToken, newUserId)
                    }

                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .header("Account-Id", LibreService.sha256(newUserId))
                        .build()
                } else {
                    Log.e("LibreAuthenticator", "Silent reauth succeeded but token or user ID was empty.")
                    null
                }
            } else {
                val error = reauthResult.exceptionOrNull()
                Log.e("LibreAuthenticator", "Silent reauth failed with exception: ${error?.localizedMessage}", error)

                if (error is HttpException && error.code() == 401) {
                    // Credentials explicitly rejected by LibreLinkUp server (e.g. password changed)
                    onSessionExpired("Las credenciales guardadas no son válidas o la contraseña cambió.")
                } else {
                    // Transient network error or server downtime -> DO NOT logout!
                    eventLogger?.log(
                        LogLevel.WARNING,
                        "AUTH",
                        "Fallo de red al renovar token de LibreLinkUp",
                        error?.localizedMessage ?: "Error de conexión temporal"
                    )
                }
                null
            }
        }
    }

    private fun onSessionExpired(reason: String) {
        eventLogger?.log(
            LogLevel.ERROR,
            "AUTH",
            "Sesión de LibreLinkUp caducada",
            "$reason Es necesario volver a iniciar sesión."
        )
        LibreService.clearAuth()
        runBlocking {
            preferenceManager.clearAuth()
            credentialStore.clearCredentials()
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
