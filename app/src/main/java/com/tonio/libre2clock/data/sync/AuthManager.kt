package com.tonio.libre2clock.data.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)

    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<com.google.firebase.auth.FirebaseUser?> = _user

    init {
        // Refrescar la configuración de Remote Config en el arranque
        RemoteConfigManager.getInstance().fetchAndActivate()

        auth.addAuthStateListener { firebaseAuth ->
            _user.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser != null) {
                RemoteConfigManager.getInstance().fetchAndActivate()
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    suspend fun signInWithGoogle(context: Context, webClientId: String): Result<Unit> {
        Log.d("AuthManager", "Starting Google Sign In with client ID: $webClientId")

        val activity = context.findActivity()
        if (activity == null) {
            Log.e("AuthManager", "Could not resolve Activity from Context: $context")
            return Result.failure(IllegalStateException("No se pudo obtener el contexto de Activity para mostrar el inicio de sesión de Google."))
        }

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Permite ver todas las cuentas, no solo las de la app
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.d("AuthManager", "Requesting credentials with Activity context...")
            val activityCredentialManager = CredentialManager.create(activity)
            val result = activityCredentialManager.getCredential(activity, request)
            handleSignIn(result)
            Result.success(Unit)
        } catch (e: GetCredentialCancellationException) {
            Log.d("AuthManager", "User cancelled Google Sign In", e)
            Result.failure(Exception("Inicio de sesión cancelado por el usuario."))
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e("AuthManager", "Google Credential Provider configuration error", e)
            Result.failure(Exception("Error de configuración de Google (verifique SHA-1 / Google Play Services): ${e.localizedMessage}"))
        } catch (e: NoCredentialException) {
            Log.e("AuthManager", "No Google credentials found or available", e)
            Result.failure(Exception("No se encontraron cuentas de Google en el dispositivo o Google Play Services no respondió: ${e.localizedMessage}"))
        } catch (e: Exception) {
            Log.e("AuthManager", "Error during Google Sign In", e)
            Result.failure(e)
        }
    }

    private suspend fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        
        // Extraer el ID Token de forma segura
        val googleIdToken = try {
            com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Failed to parse Google ID Token", e)
            throw Exception("Error al procesar la cuenta de Google: ${e.localizedMessage}")
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        auth.signInWithCredential(firebaseCredential).await()
    }

    suspend fun signOut() {
        auth.signOut()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    suspend fun signInAnonymously(): Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Result.success(currentUser.uid)
            } else {
                val authResult = auth.signInAnonymously().await()
                val uid = authResult.user?.uid ?: throw Exception("Error al obtener UID anónimo de Firebase")
                Result.success(uid)
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Error in anonymous sign-in", e)
            Result.failure(e)
        }
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid
}
