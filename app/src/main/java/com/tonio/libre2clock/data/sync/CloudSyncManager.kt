package com.tonio.libre2clock.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper
import com.tonio.libre2clock.data.model.HistoryBackupPayload
import com.tonio.libre2clock.data.repository.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CloudSyncManager(
    private val context: Context,
    private val authManager: AuthManager,
    private val preferenceManager: PreferenceManager
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val dbHelper = GlucoseHistoryDatabaseHelper(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            combine(
                authManager.user,
                preferenceManager.isCloudSyncEnabled,
                preferenceManager.patientId
            ) { user, isEnabled, patientId ->
                Triple(user, isEnabled, patientId)
            }.distinctUntilChanged().collect { (user, isEnabled, patientId) ->
                if (user != null && isEnabled && patientId != null) {
                    startSync(user.uid, patientId)
                }
            }
        }
    }

    private fun startSync(googleUid: String, patientId: String) {
        scope.launch {
            syncSettingsToCloud(googleUid, patientId)
            listenToRemoteSettings(googleUid, patientId)
            syncHistory(googleUid, patientId)
        }
    }

    private suspend fun syncSettingsToCloud(googleUid: String, patientId: String) {
        try {
            val payload = preferenceManager.getCurrentBackupPayload()
            firestore.collection("users").document(googleUid)
                .collection("patients").document(patientId)
                .collection("config").document("settings")
                .set(payload, SetOptions.merge())
                .await()
            Log.d("CloudSync", "Settings pushed for patient: $patientId")
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pushing settings", e)
        }
    }

    private fun listenToRemoteSettings(googleUid: String, patientId: String) {
        firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)
            .collection("config").document("settings")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    scope.launch {
                        try {
                            val payload = snapshot.toObject(HistoryBackupPayload::class.java)
                            if (payload != null) {
                                preferenceManager.restoreFromPayload(payload, isHardReset = false)
                            }
                        } catch (e: Exception) {
                            Log.e("CloudSync", "Error importing remote settings", e)
                        }
                    }
                }
            }
    }

    private suspend fun syncHistory(googleUid: String, patientId: String) {
        val localMeasurements = dbHelper.readAllNewestFirst()
        val batch = firestore.batch()
        val historyColl = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)
            .collection("glucose_history")
        
        localMeasurements.take(500).forEach { m -> 
            val docId = "${m.epochSeconds}-${m.value}"
            batch.set(historyColl.document(docId), m, SetOptions.merge())
        }
        
        try {
            batch.commit().await()
            Log.d("CloudSync", "History pushed for patient: $patientId")
            preferenceManager.saveCloudSyncLastSuccessAt(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pushing history", e)
        }
    }
}
