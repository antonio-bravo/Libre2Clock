package com.tonio.libre2clock.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.HistoryBackupPayload
import com.tonio.libre2clock.data.model.InsulinDose
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

    fun triggerManualSync() {
        Log.d("CloudSync", "Manual sync triggered")
        scope.launch {
            val user = authManager.user.value
            val patientId = preferenceManager.patientId.first()
            if (user != null && patientId != null) {
                startSync(user.uid, patientId)
            } else {
                Log.w("CloudSync", "Cannot sync: user=$user, patientId=$patientId")
            }
        }
    }

    suspend fun runDiagnostic(): String = buildString {
        appendLine("=== Cloud Sync Diagnostic ===")
        val user = authManager.user.value
        appendLine("Google User: ${user?.email ?: "NOT LOGGED IN"}")
        
        val patientId = preferenceManager.patientId.first()
        appendLine("Patient ID: ${patientId ?: "MISSING (Login to LLU first)"}")

        if (user != null && patientId != null) {
            try {
                appendLine("Testing Firestore write...")
                val testDoc = firestore.collection("users").document(user.uid)
                    .collection("patients").document(patientId)
                    .collection("config").document("diagnostic")
                
                testDoc.set(mapOf("last_test" to System.currentTimeMillis())).await()
                appendLine("Result: SUCCESS (Write test passed)")
                
                appendLine("Starting full sync test...")
                syncSettingsToCloud(user.uid, patientId)
                appendLine("Settings sync: OK")
                
            } catch (e: Exception) {
                appendLine("Result: FAIL")
                appendLine("Error: ${e.message}")
                if (e.message?.contains("permission-denied") == true) {
                    appendLine("TIP: Check Firestore Rules in Firebase Console.")
                }
            }
        } else {
            appendLine("Result: SKIPPED (Requirements not met)")
        }
    }

    private fun startSync(googleUid: String, patientId: String) {
        scope.launch {
            try {
                Log.d("CloudSync", "Starting sync session for patient: $patientId")
                syncSettingsToCloud(googleUid, patientId)
                syncDataListsToCloud(googleUid, patientId)
                listenToRemoteSettings(googleUid, patientId)
                syncHistory(googleUid, patientId)
                Log.d("CloudSync", "Sync session initialized")
            } catch (e: Exception) {
                Log.e("CloudSync", "Fatal error during sync initialization", e)
            }
        }
    }

    private suspend fun syncSettingsToCloud(googleUid: String, patientId: String) {
        try {
            val payload = preferenceManager.getSettingsOnlyPayload()
            firestore.collection("users").document(googleUid)
                .collection("patients").document(patientId)
                .collection("config").document("settings")
                .set(payload, SetOptions.merge())
                .await()
            Log.d("CloudSync", "Settings pushed for patient: $patientId")
            preferenceManager.saveCloudSyncLastSuccessAt(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pushing settings", e)
        }
    }

    private suspend fun syncDataListsToCloud(googleUid: String, patientId: String) {
        try {
            // Sync smaller lists (Capillary, Insulin, SensorLogs) as separate docs
            // to avoid hitting document size limits.
            val patientDoc = firestore.collection("users").document(googleUid)
                .collection("patients").document(patientId)
            
            val capillary = preferenceManager.capillaryReadings.first()
            patientDoc.collection("data").document("capillary").set(mapOf("items" to capillary)).await()

            val insulin = preferenceManager.insulinDoses.first()
            patientDoc.collection("data").document("insulin").set(mapOf("items" to insulin)).await()

            val sensorLogs = preferenceManager.sensorLogs.first()
            patientDoc.collection("data").document("sensor_logs").set(mapOf("items" to sensorLogs)).await()
            
            Log.d("CloudSync", "Data lists pushed for patient: $patientId")
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pushing data lists", e)
        }
    }

    private fun listenToRemoteSettings(googleUid: String, patientId: String) {
        val patientDoc = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)

        // Listen for settings
        patientDoc.collection("config").document("settings")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                snapshot?.toObject(HistoryBackupPayload::class.java)?.let { payload ->
                    scope.launch { preferenceManager.restoreFromPayload(payload, isHardReset = false) }
                }
            }

        // Listen for data lists
        patientDoc.collection("data").document("capillary").addSnapshotListener { snapshot, _ ->
            snapshot?.toObject(ListWrapper::class.java)?.let { wrapper ->
                @Suppress("UNCHECKED_CAST")
                val items = wrapper.items as? List<CapillaryMeasurement> ?: return@let
                scope.launch { preferenceManager.saveCapillaryReadings(items) }
            }
        }
        
        patientDoc.collection("data").document("insulin").addSnapshotListener { snapshot, _ ->
            snapshot?.toObject(ListWrapper::class.java)?.let { wrapper ->
                @Suppress("UNCHECKED_CAST")
                val items = wrapper.items as? List<InsulinDose> ?: return@let
                scope.launch { preferenceManager.saveInsulinDoses(items) }
            }
        }
    }

    private data class ListWrapper(val items: List<Any> = emptyList())

    private suspend fun syncHistory(googleUid: String, patientId: String) {
        try {
            val localMeasurements = dbHelper.readAllNewestFirst()
            val historyColl = firestore.collection("users").document(googleUid)
                .collection("patients").document(patientId)
                .collection("glucose_history")
            
            // Sync in smaller chunks to be extra safe
            val itemsToSync = localMeasurements.take(300) 
            itemsToSync.chunked(50).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { m ->
                    val docId = "${m.epochSeconds}-${m.value}"
                    batch.set(historyColl.document(docId), m, SetOptions.merge())
                }
                batch.commit().await()
            }
            
            Log.d("CloudSync", "History chunks pushed for patient: $patientId")
            preferenceManager.saveCloudSyncLastSuccessAt(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pushing history", e)
        }
    }
}
