package com.tonio.libre2clock.data.sync

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
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
        auth.addAuthStateListener { firebaseAuth ->
            _user.value = firebaseAuth.currentUser
        }
    }

    suspend fun signInWithGoogle(webClientId: String): Result<Unit> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            handleSignIn(result)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        val googleIdToken = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        auth.signInWithCredential(firebaseCredential).await()
    }

    suspend fun signOut() {
        auth.signOut()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid
}
