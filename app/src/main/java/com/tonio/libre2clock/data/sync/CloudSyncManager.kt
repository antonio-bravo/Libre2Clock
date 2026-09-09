package com.tonio.libre2clock.data.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.HistoryBackupPayload
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.di.AppContainer
import com.tonio.libre2clock.util.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class CloudSyncManager(
    private val context: Context,
    private val authManager: AuthManager,
    private val preferenceManager: PreferenceManager
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val dbHelper = GlucoseHistoryDatabaseHelper(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventLogger = AppContainer.provideEventLogManager(context)

    private val _cloudSyncDebugOutput = MutableStateFlow<String?>(null)
    val cloudSyncDebugOutput: StateFlow<String?> = _cloudSyncDebugOutput.asStateFlow()

    init {
        scope.launch {
            combine(authManager.user, preferenceManager.isCloudSyncEnabled) { user, isEnabled ->
                user to isEnabled
            }.collect { (user, isEnabled) ->
                if (user != null && isEnabled) {
                    val patientId = preferenceManager.patientId.first()
                    if (patientId != null) {
                        startSync(user.uid, patientId)
                    }
                }
            }
        }
    }

    private fun log(msg: String) {
        val current = _cloudSyncDebugOutput.value ?: ""
        _cloudSyncDebugOutput.value = current + msg + "\n"
    }

    fun runDiagnostic() {
        _cloudSyncDebugOutput.value = ""
        scope.launch {
            log("=== Cloud Sync Diagnostic ===")
            val user = authManager.user.value
            log("Google User: ${user?.email ?: "NOT LOGGED IN"}")
            
            val patientId = preferenceManager.patientId.first()
            log("Patient ID: ${patientId ?: "MISSING (Login to LLU first)"}")

            if (user != null && patientId != null) {
                try {
                    log("Google UID: ${user.uid}")
                    log("Ensuring Firestore is online...")
                    firestore.enableNetwork().await()
                    
                    log("Testing Firestore write...")
                    val testDoc = firestore.collection("users").document(user.uid)
                        .collection("patients").document(patientId)
                        .collection("config").document("diagnostic")
                    
                    val testData = mapOf("last_test" to System.currentTimeMillis(), "device" to Build.MODEL)
                    withTimeout(10000) { testDoc.set(testData).await() }
                    log("Result: SUCCESS")
                    
                    startSync(user.uid, patientId)
                    log("Full sync triggered: OK")
                } catch (e: Exception) {
                    log("Result: FAIL - ${e.message}")
                    eventLogger.log(LogLevel.ERROR, "CloudSync", "Diagnostic failed", e.stackTraceToString())
                }
            }
        }
    }

    fun triggerManualSync() {
        scope.launch {
            val user = authManager.user.value
            val patientId = preferenceManager.patientId.first()
            if (user != null && patientId != null) {
                startSync(user.uid, patientId)
            }
        }
    }

    private fun startSync(googleUid: String, patientId: String) {
        scope.launch {
            try {
                val patientDoc = firestore.collection("users").document(googleUid)
                    .collection("patients").document(patientId)
                
                // 1. Initial Merge for settings
                val remoteSettings = patientDoc.collection("config").document("settings").get().await()
                if (remoteSettings.exists()) {
                    remoteSettings.toObject(HistoryBackupPayload::class.java)?.let { payload ->
                        preferenceManager.restoreFromPayload(payload, isHardReset = false)
                    }
                }
                
                // 2. Push local state
                syncSettingsToCloud(googleUid, patientId)
                syncDataListsToCloud(googleUid, patientId)
                syncHistory(googleUid, patientId)
                
                // 3. Start listeners
                listenToRemoteChanges(googleUid, patientId)
                
                preferenceManager.saveCloudSyncLastSuccessAt(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("CloudSync", "Sync error", e)
            }
        }
    }

    private suspend fun syncSettingsToCloud(googleUid: String, patientId: String) {
        val payload = preferenceManager.getSettingsOnlyPayload()
        firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)
            .collection("config").document("settings")
            .set(payload, SetOptions.merge()).await()
    }

    private suspend fun syncDataListsToCloud(googleUid: String, patientId: String) {
        val patientDoc = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)
        
        val insulin = preferenceManager.insulinDoses.first()
        val insulinColl = patientDoc.collection("insulin_doses")
        insulin.forEach { dose -> insulinColl.document(dose.id).set(dose, SetOptions.merge()) }

        val capillary = preferenceManager.capillaryReadings.first()
        val capillaryColl = patientDoc.collection("capillary_readings")
        capillary.forEach { r -> capillaryColl.document(r.id).set(r, SetOptions.merge()) }
    }

    private fun listenToRemoteChanges(googleUid: String, patientId: String) {
        val patientDoc = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)

        patientDoc.collection("insulin_doses").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null) return@addSnapshotListener
            scope.launch {
                val remote = snapshots.documents.mapNotNull { it.toObject(InsulinDose::class.java) }
                if (remote.isNotEmpty()) {
                    val local = preferenceManager.insulinDoses.first()
                    val merged = (local + remote).associateBy { it.id }.values
                        .filter { !it.isDeleted }.sortedByDescending { it.timestamp }
                    preferenceManager.saveInsulinDoses(merged)
                }
            }
        }

        patientDoc.collection("capillary_readings").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null) return@addSnapshotListener
            scope.launch {
                val remote = snapshots.documents.mapNotNull { it.toObject(CapillaryMeasurement::class.java) }
                if (remote.isNotEmpty()) {
                    val local = preferenceManager.capillaryReadings.first()
                    val merged = (local + remote).associateBy { it.id }.values
                        .filter { !it.isDeleted }.sortedByDescending { it.timestamp }
                    preferenceManager.saveCapillaryReadings(merged)
                }
            }
        }
    }

    private suspend fun syncHistory(googleUid: String, patientId: String) {
        val local = dbHelper.readAllNewestFirst().take(200)
        val historyColl = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)
            .collection("glucose_history")
        
        local.chunked(25).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { m -> batch.set(historyColl.document("${m.epochSeconds}-${m.value}"), m, SetOptions.merge()) }
            batch.commit().await()
        }
    }

    fun resetCloudData(googleUid: String, patientId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val patientDoc = firestore.collection("users").document(googleUid)
                    .collection("patients").document(patientId)
                val collections = listOf("insulin_doses", "capillary_readings", "glucose_history", "config")
                for (coll in collections) {
                    val snapshot = patientDoc.collection(coll).get().await()
                    val batch = firestore.batch()
                    snapshot.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                startSync(googleUid, patientId)
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}
