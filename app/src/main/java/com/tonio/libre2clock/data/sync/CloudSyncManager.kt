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
                preferenceManager.isCloudSyncEnabled
            ) { user, isEnabled ->
                user to isEnabled
            }.distinctUntilChanged().collect { (user, isEnabled) ->
                if (user != null && isEnabled) {
                    startSync(user.uid)
                }
            }
        }
    }

    private fun startSync(userId: String) {
        // Sync Settings
        scope.launch {
            // Push local settings to cloud when they change
            // For simplicity, we just push the whole payload occasionally or on specific events
            // In a real app, we'd watch individual flows. 
            // Here we'll just push whenever the backup payload would have been updated.
            // But for now, let's just do an initial push and listen for remote changes.
            syncSettingsToCloud(userId)
            
            // Listen for remote settings changes
            listenToRemoteSettings(userId)
            
            // Sync History
            syncHistory(userId)
        }
    }

    private suspend fun syncSettingsToCloud(userId: String) {
        try {
            val payload = preferenceManager.getCurrentBackupPayload()
            firestore.collection("users").document(userId)
                .collection("config").document("settings")
                .set(payload, SetOptions.merge())
                .await()
            Log.d("CloudSync", "Settings pushed to cloud")
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pushing settings", e)
        }
    }

    private fun listenToRemoteSettings(userId: String) {
        firestore.collection("users").document(userId)
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

    private suspend fun syncHistory(userId: String) {
        // Push local history to cloud
        val localMeasurements = dbHelper.readAllNewestFirst()
        val batch = firestore.batch()
        val historyColl = firestore.collection("users").document(userId).collection("glucose_history")
        
        localMeasurements.take(500).forEach { m -> // Limit initial sync
            val docId = "${m.epochSeconds}-${m.value}"
            batch.set(historyColl.document(docId), m, SetOptions.merge())
        }
        
        try {
            batch.commit().await()
            Log.d("CloudSync", "History pushed to cloud")
            preferenceManager.saveCloudSyncLastSuccessAt(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pushing history", e)
        }
    }
}
