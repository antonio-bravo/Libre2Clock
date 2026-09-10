package com.tonio.libre2clock.data.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
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

    // Evita adjuntar múltiples listeners si startSync se llama varias veces
    private var isListening = false

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

        scope.launch {
            preferenceManager.settingsUpdatedAt.collect { updatedAt ->
                if (updatedAt == null) return@collect
                val user = authManager.user.value
                val isEnabled = preferenceManager.isCloudSyncEnabled.first()
                val patientId = preferenceManager.patientId.first()
                if (user != null && isEnabled && patientId != null) {
                    syncSettingsToCloud(user.uid, patientId)
                }
            }
        }
    }

    private fun log(msg: String) {
        val current = _cloudSyncDebugOutput.value ?: ""
        _cloudSyncDebugOutput.value = current + msg + "\n"
    }

    fun clearDebugOutput() {
        _cloudSyncDebugOutput.value = null
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
                    withTimeout(15000) { testDoc.set(testData).await() }
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
                log("=== Starting Cloud Sync ===")
                val patientDoc = firestore.collection("users").document(googleUid)
                    .collection("patients").document(patientId)

                log("1. Fetching remote settings...")
                val remoteSettings = withTimeout(10000) {
                    patientDoc.collection("config").document("settings").get().await()
                }
                if (remoteSettings.exists()) {
                    val remotePayload = remoteSettings.toObject(HistoryBackupPayload::class.java)
                    val localTimestamp = preferenceManager.settingsUpdatedAt.first() ?: 0L
                    val remoteTimestamp = remotePayload?.settingsUpdatedAtMs ?: 0L

                    if (remoteTimestamp > localTimestamp) {
                        remotePayload?.let { payload ->
                            preferenceManager.restoreFromPayload(payload, isHardReset = false)
                            log("Settings restored from cloud (remote is newer).")
                        }
                    } else {
                        log("Skipping settings restore (local is newer or equal: local=$localTimestamp, remote=$remoteTimestamp).")
                    }
                }

                log("2. Pulling recent history from cloud...")
                pullHistory(googleUid, patientId)

                log("3. Pushing local settings to cloud...")
                syncSettingsToCloud(googleUid, patientId)

                log("4. Pushing local lists to cloud...")
                syncDataListsToCloud(googleUid, patientId)

                log("5. Pushing local history to cloud...")
                syncHistory(googleUid, patientId)

                log("6. Activating real-time listeners...")
                ensureListening(googleUid, patientId)

                preferenceManager.saveCloudSyncLastSuccessAt(System.currentTimeMillis())
                log("=== Sync Completed Successfully ===")
            } catch (e: TimeoutCancellationException) {
                Log.e("CloudSync", "Sync timed out", e)
                log("Sync timed out. Check network and try again.")
                eventLogger.log(LogLevel.ERROR, "CloudSync", "Sync timed out", e.stackTraceToString())
            } catch (e: Exception) {
                Log.e("CloudSync", "Sync error", e)
                log("Sync failed: ${e.message}")
                eventLogger.log(LogLevel.ERROR, "CloudSync", "Sync failed", e.stackTraceToString())
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
        if (insulin.isNotEmpty()) {
            val insulinColl = patientDoc.collection("insulin_doses")
            // Reducido a 100 para evitar payloads grandes que causan timeout
            insulin.chunked(100).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { dose -> batch.set(insulinColl.document(dose.id), dose, SetOptions.merge()) }
                withTimeout(15000) { batch.commit().await() }
            }
        }

        val capillary = preferenceManager.capillaryReadings.first()
        if (capillary.isNotEmpty()) {
            val capillaryColl = patientDoc.collection("capillary_readings")
            capillary.chunked(100).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { r -> batch.set(capillaryColl.document(r.id), r, SetOptions.merge()) }
                withTimeout(15000) { batch.commit().await() }
            }
        }
    }

    private suspend fun pullHistory(googleUid: String, patientId: String) {
        try {
            val historyColl = firestore.collection("users").document(googleUid)
                .collection("patients").document(patientId)
                .collection("glucose_history")

            val snapshot = withTimeout(15000) {
                historyColl.orderBy("__name__", Query.Direction.DESCENDING)
                    .limit(500)
                    .get()
                    .await()
            }

            val remote = snapshot.documents.mapNotNull { it.toObject(GlucoseMeasurement::class.java) }
            if (remote.isNotEmpty()) {
                dbHelper.upsertAll(remote)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w("CloudSync", "Pull history timed out, will retry on next sync")
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pulling history", e)
        }
    }

    private fun ensureListening(googleUid: String, patientId: String) {
        if (isListening) return
        isListening = true
        listenToRemoteChanges(googleUid, patientId)
    }

    private fun listenToRemoteChanges(googleUid: String, patientId: String) {
        val patientDoc = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)

        patientDoc.collection("insulin_doses").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("CloudSync", "Listen insulin failed", e)
                return@addSnapshotListener
            }
            if (snapshots == null) return@addSnapshotListener

            scope.launch {
                try {
                    val localDoses = preferenceManager.insulinDoses.first().associateBy { it.id }.toMutableMap()
                    // OPTIMIZACIÓN CLAVE: Solo procesamos los documentos que cambiaron, no toda la colección
                    for (change in snapshots.documentChanges) {
                        val dose = change.document.toObject(InsulinDose::class.java) ?: continue
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                if (!dose.isDeleted) {
                                    localDoses[dose.id] = dose
                                } else {
                                    localDoses.remove(dose.id)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                localDoses.remove(dose.id)
                            }
                        }
                    }
                    val merged = localDoses.values.sortedByDescending { it.timestamp }
                    preferenceManager.saveInsulinDoses(merged)
                } catch (e: Exception) {
                    Log.e("CloudSync", "Error processing insulin doses snapshot", e)
                }
            }
        }

        patientDoc.collection("capillary_readings").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("CloudSync", "Listen capillary failed", e)
                return@addSnapshotListener
            }
            if (snapshots == null) return@addSnapshotListener

            scope.launch {
                try {
                    val localReadings = preferenceManager.capillaryReadings.first().associateBy { it.id }.toMutableMap()
                    for (change in snapshots.documentChanges) {
                        val reading = change.document.toObject(CapillaryMeasurement::class.java) ?: continue
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                if (!reading.isDeleted) {
                                    localReadings[reading.id] = reading
                                } else {
                                    localReadings.remove(reading.id)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                localReadings.remove(reading.id)
                            }
                        }
                    }
                    val merged = localReadings.values.sortedByDescending { it.timestamp }
                    preferenceManager.saveCapillaryReadings(merged)
                } catch (e: Exception) {
                    Log.e("CloudSync", "Error processing capillary readings snapshot", e)
                }
            }
        }
    }

    private suspend fun syncHistory(googleUid: String, patientId: String) {
        val local = dbHelper.readAllNewestFirst().take(200)
        if (local.isEmpty()) return

        val historyColl = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)
            .collection("glucose_history")

        local.chunked(100).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { m ->
                batch.set(historyColl.document("${m.epochSeconds}-${m.value}"), m, SetOptions.merge())
            }
            withTimeout(15000) { batch.commit().await() }
        }
    }

    fun resetCloudData(googleUid: String, patientId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                log("Starting cloud data reset...")
                val patientDoc = firestore.collection("users").document(googleUid)
                    .collection("patients").document(patientId)
                val collections = listOf("insulin_doses", "capillary_readings", "glucose_history", "config")

                for (coll in collections) {
                    var lastDoc: DocumentSnapshot? = null
                    do {
                        // Paginación obligatoria: Firestore limita los batch a 500 operaciones
                        val query = if (lastDoc == null) {
                            patientDoc.collection(coll).limit(500).get().await()
                        } else {
                            patientDoc.collection(coll).orderBy(FieldPath.documentId()).startAfter(lastDoc).limit(500).get().await()
                        }

                        if (query.documents.isEmpty()) break

                        val batch = firestore.batch()
                        query.documents.forEach { batch.delete(it.reference) }
                        withTimeout(15000) { batch.commit().await() }

                        lastDoc = if (query.documents.size == 500) query.documents.last() else null
                    } while (lastDoc != null)
                }
                log("Cloud data reset successfully. Resyncing...")
                startSync(googleUid, patientId)
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                Log.e("CloudSync", "Reset failed", e)
                log("Reset failed: ${e.message}")
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}