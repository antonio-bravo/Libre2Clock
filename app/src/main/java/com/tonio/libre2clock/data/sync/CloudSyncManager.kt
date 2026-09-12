package com.tonio.libre2clock.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.HistoryBackupPayload
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.di.AppContainer
import com.tonio.libre2clock.util.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import java.io.IOException

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

    private var isListening = false
    private val syncMutex = Mutex()

    // Para evitar fugas de memoria con los listeners
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    private suspend fun <T> retryWithBackoff(
        stepName: String,
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 5000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                // Reducido de 30s a 15s. En móviles, si un lote pequeño tarda más de 15s,
                // es mejor fallar rápido y reintentar en el próximo ciclo que bloquear la app.
                return withTimeout(15000) { block() }
            } catch (e: TimeoutCancellationException) {
                log("[$stepName] Timeout en intento ${attempt + 1}. Reintentando en ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            } catch (e: IOException) {
                log("[$stepName] Error de red (IO) en intento ${attempt + 1}. Reintentando...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                    e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                    e.code == FirebaseFirestoreException.Code.ABORTED) { // Añadido ABORTED por contención
                    log("[$stepName] Error de red (Firestore) en intento ${attempt + 1}. Reintentando...")
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                } else {
                    throw e // Error de permisos, índices o datos, no reintentar
                }
            }
        }
        return withTimeout(15000) { block() }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    init {
        scope.launch {
            combine(authManager.user, preferenceManager.isCloudSyncEnabled) { user, isEnabled ->
                user to isEnabled
            }.collect { (user, isEnabled) ->
                if (user != null && isEnabled) {
                    val patientId = preferenceManager.patientId.first()
                    if (patientId != null) startSync(user.uid, patientId)
                }
            }
        }

        scope.launch {
            preferenceManager.settingsUpdatedAt.collect { updatedAt ->
                if (updatedAt == null) return@collect
                if (syncMutex.isLocked) return@collect

                val user = authManager.user.value
                val isEnabled = preferenceManager.isCloudSyncEnabled.first()
                val patientId = preferenceManager.patientId.first()

                if (user != null && isEnabled && patientId != null && isNetworkAvailable()) {
                    startSync(user.uid, patientId)
                }
            }
        }
    }

    private fun log(msg: String) {
        val current = _cloudSyncDebugOutput.value ?: ""
        val lines = current.split("\n")
        val limitedLog = if (lines.size > 150) lines.takeLast(150).joinToString("\n") else current
        _cloudSyncDebugOutput.value = limitedLog + msg + "\n"
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
                    retryWithBackoff("Diagnostic Write") { testDoc.set(testData).await() }
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
            if (user != null && patientId != null) startSync(user.uid, patientId)
        }
    }

    private fun startSync(googleUid: String, patientId: String) {
        scope.launch {
            if (!syncMutex.tryLock()) {
                Log.d("CloudSync", "Sync already in progress, skipping.")
                return@launch
            }

            try {
                if (!isNetworkAvailable()) {
                    log("Sync skipped: No network connection.")
                    return@launch
                }

                _cloudSyncDebugOutput.value = ""

                var currentStep = "Initializing"
                try {
                    log("=== Starting Cloud Sync ===")
                    val patientDoc = firestore.collection("users").document(googleUid)
                        .collection("patients").document(patientId)

                    currentStep = "Fetching remote settings"
                    log("1. $currentStep...")
                    val remoteSettings = retryWithBackoff(currentStep) {
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
                            log("Skipping settings restore (local is newer or equal).")
                        }
                    }

                    currentStep = "Pulling recent history"
                    log("2. $currentStep...")
                    pullHistory(googleUid, patientId)

                    // ---------------------------------------------------------------
                    // PASO 3 DESGLOSADO: cada sub-paso tiene su propio currentStep,
                    // así el log/exception dice exactamente cuál se atascó, en vez
                    // de agrupar todo bajo "Pushing local data".
                    // ---------------------------------------------------------------
                    currentStep = "Pushing settings"
                    log("3a. $currentStep...")
                    syncSettingsToCloud(googleUid, patientId)

                    currentStep = "Pushing data lists"
                    log("3b. $currentStep...")
                    syncDataListsToCloud(googleUid, patientId)

                    currentStep = "Pushing history"
                    log("3c. $currentStep...")
                    syncHistory(googleUid, patientId)

                    currentStep = "Activating real-time listeners"
                    log("4. $currentStep...")
                    ensureListening(googleUid, patientId)

                    preferenceManager.saveCloudSyncLastSuccessAt(System.currentTimeMillis())
                    log("=== Sync Completed Successfully ===")
                } catch (e: TimeoutCancellationException) {
                    val errorMsg = "Sync timed out during: $currentStep"
                    Log.e("CloudSync", errorMsg, e)
                    log("❌ ERROR: $errorMsg")
                    eventLogger.log(LogLevel.ERROR, "CloudSync", errorMsg, e.stackTraceToString())
                } catch (e: Exception) {
                    val errorMsg = "Sync failed during: $currentStep - ${e.message}"
                    Log.e("CloudSync", "Sync error", e)
                    log("❌ ERROR: $errorMsg")
                    eventLogger.log(LogLevel.ERROR, "CloudSync", errorMsg, e.stackTraceToString())
                }
            } finally {
                syncMutex.unlock()
            }
        }
    }

    private suspend fun syncSettingsToCloud(googleUid: String, patientId: String) {
        val payload = preferenceManager.getSettingsOnlyPayload()
        retryWithBackoff("Sync Settings") {
            firestore.collection("users").document(googleUid)
                .collection("patients").document(patientId)
                .collection("config").document("settings")
                .set(payload, SetOptions.merge()).await()
        }
    }

    private suspend fun syncDataListsToCloud(googleUid: String, patientId: String) {
        val patientDoc = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)

        // ---------------------------------------------------------------
        // Logging granular por colección: te dice cuántos registros hay
        // y en qué lote se queda atascado si vuelve a fallar. Si estos
        // números crecen sin parar en cada sync, la causa raíz es que
        // se reenvía la lista COMPLETA siempre en vez de solo lo nuevo
        // (ver recomendación de sync incremental).
        // ---------------------------------------------------------------

        val insulin = preferenceManager.insulinDoses.first()
        log("  -> Insulin: ${insulin.size} registros")
        if (insulin.isNotEmpty()) {
            val insulinColl = patientDoc.collection("insulin_doses")
            val chunks = insulin.chunked(20)
            chunks.forEachIndexed { index, chunk ->
                retryWithBackoff("Insulin Batch ${index + 1}/${chunks.size}") {
                    val batch = firestore.batch()
                    chunk.forEach { dose -> batch.set(insulinColl.document(dose.id), dose, SetOptions.merge()) }
                    batch.commit().await()
                }
            }
        }

        val capillary = preferenceManager.capillaryReadings.first()
        log("  -> Capillary: ${capillary.size} registros")
        if (capillary.isNotEmpty()) {
            val capillaryColl = patientDoc.collection("capillary_readings")
            val chunks = capillary.chunked(20)
            chunks.forEachIndexed { index, chunk ->
                retryWithBackoff("Capillary Batch ${index + 1}/${chunks.size}") {
                    val batch = firestore.batch()
                    chunk.forEach { r -> batch.set(capillaryColl.document(r.id), r, SetOptions.merge()) }
                    batch.commit().await()
                }
            }
        }

        val sensorLogs = preferenceManager.sensorLogs.first()
        log("  -> SensorLogs: ${sensorLogs.size} registros")
        if (sensorLogs.isNotEmpty()) {
            val sensorLogsColl = patientDoc.collection("sensor_logs")
            val chunks = sensorLogs.chunked(20)
            chunks.forEachIndexed { index, chunk ->
                retryWithBackoff("SensorLogs Batch ${index + 1}/${chunks.size}") {
                    val batch = firestore.batch()
                    chunk.forEach { log -> batch.set(sensorLogsColl.document(log.serialNumber), log, SetOptions.merge()) }
                    batch.commit().await()
                }
            }
        }
    }

    private suspend fun pullHistory(googleUid: String, patientId: String) {
        try {
            val historyColl = firestore.collection("users").document(googleUid)
                .collection("patients").document(patientId)
                .collection("glucose_history")

            val snapshot = retryWithBackoff("Pull History") {
                // CAMBIO CRÍTICO: Ordenar por el campo de tiempo numérico.
                // Ordenar por FieldPath.documentId() cuando es un String ("1620000-120") causa ordenamiento alfabético
                // y dispara errores de índice.
                // ⚠️ Asegúrate de que "sort_epoch_ms" sea el nombre EXACTO del campo en tu modelo GlucoseMeasurement.
                historyColl.orderBy("sort_epoch_ms", Query.Direction.DESCENDING)
                    .limit(150) // Reducido de 300 a 150 para aligerar la descarga
                    .get()
                    .await()
            }

            val remote = snapshot.documents.mapNotNull { it.toObject(GlucoseMeasurement::class.java) }
            if (remote.isNotEmpty()) {
                dbHelper.upsertAll(remote)
                log("Successfully pulled ${remote.size} history records.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.w("CloudSync", "Pull history timed out")
            log("⚠️ Pull history timed out. Will retry next time.")
        } catch (e: Exception) {
            Log.e("CloudSync", "Error pulling history", e)
            log("❌ ERROR pulling history: ${e.message}")
            eventLogger.log(LogLevel.ERROR, "CloudSync", "Pull history failed", e.stackTraceToString())
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

        // Guardamos los listeners para poder eliminarlos después y evitar fugas de memoria
        val reg1 = patientDoc.collection("insulin_doses").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null) return@addSnapshotListener
            scope.launch {
                try {
                    val localDoses = preferenceManager.insulinDoses.first().associateBy { it.id }.toMutableMap()
                    for (change in snapshots.documentChanges) {
                        val dose = change.document.toObject(InsulinDose::class.java) ?: continue
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                if (!dose.isDeleted) localDoses[dose.id] = dose else localDoses.remove(dose.id)
                            }
                            DocumentChange.Type.REMOVED -> localDoses.remove(dose.id)
                        }
                    }
                    preferenceManager.saveInsulinDoses(localDoses.values.sortedByDescending { it.timestamp })
                } catch (e: Exception) {
                    Log.e("CloudSync", "Error processing insulin snapshot", e)
                }
            }
        }
        listenerRegistrations.add(reg1)

        val reg2 = patientDoc.collection("capillary_readings").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null) return@addSnapshotListener
            scope.launch {
                try {
                    val localReadings = preferenceManager.capillaryReadings.first().associateBy { it.id }.toMutableMap()
                    for (change in snapshots.documentChanges) {
                        val reading = change.document.toObject(CapillaryMeasurement::class.java) ?: continue
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                if (!reading.isDeleted) localReadings[reading.id] = reading else localReadings.remove(reading.id)
                            }
                            DocumentChange.Type.REMOVED -> localReadings.remove(reading.id)
                        }
                    }
                    preferenceManager.saveCapillaryReadings(localReadings.values.sortedByDescending { it.timestamp })
                } catch (e: Exception) {
                    Log.e("CloudSync", "Error processing capillary snapshot", e)
                }
            }
        }
        listenerRegistrations.add(reg2)

        val reg3 = patientDoc.collection("sensor_logs").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null) return@addSnapshotListener
            scope.launch {
                try {
                    val localLogs = preferenceManager.sensorLogs.first().associateBy { it.serialNumber }.toMutableMap()
                    for (change in snapshots.documentChanges) {
                        val log = change.document.toObject(SensorLog::class.java) ?: continue
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> localLogs[log.serialNumber] = log
                            DocumentChange.Type.REMOVED -> localLogs.remove(log.serialNumber)
                        }
                    }
                    preferenceManager.saveSensorLogs(localLogs.values.sortedByDescending { it.startDate })
                } catch (e: Exception) {
                    Log.e("CloudSync", "Error processing sensor logs snapshot", e)
                }
            }
        }
        listenerRegistrations.add(reg3)
    }

    // Llama a este método cuando el usuario cierre sesión o la app se destruya
    fun stopListening() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
        isListening = false
    }

    private suspend fun syncHistory(googleUid: String, patientId: String) {
        val local = dbHelper.readRecentHistory(200)
        if (local.isEmpty()) return

        val historyColl = firestore.collection("users").document(googleUid)
            .collection("patients").document(patientId)
            .collection("glucose_history")

        // Reducido de 50 a 20
        val chunks = local.chunked(20)
        chunks.forEachIndexed { index, chunk ->
            retryWithBackoff("History Batch ${index + 1}/${chunks.size}") {
                val batch = firestore.batch()
                chunk.forEach { m ->
                    batch.set(historyColl.document("${m.epochSeconds}-${m.value}"), m, SetOptions.merge())
                }
                batch.commit().await()
            }
        }
    }

    fun resetCloudData(googleUid: String, patientId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            if (!syncMutex.tryLock()) {
                onComplete(false)
                return@launch
            }
            try {
                log("Starting cloud data reset...")
                val patientDoc = firestore.collection("users").document(googleUid)
                    .collection("patients").document(patientId)
                val collections = listOf("insulin_doses", "capillary_readings", "glucose_history", "sensor_logs", "config")

                for (coll in collections) {
                    var lastDoc: DocumentSnapshot? = null
                    do {
                        val query = retryWithBackoff("Reset Query $coll") {
                            if (lastDoc == null) {
                                // Reducido de 500 a 300 para queries más rápidas
                                patientDoc.collection(coll).limit(300).get().await()
                            } else {
                                patientDoc.collection(coll).orderBy(FieldPath.documentId()).startAfter(lastDoc).limit(300).get().await()
                            }
                        }

                        if (query.documents.isEmpty()) break

                        retryWithBackoff("Reset Delete $coll") {
                            val batch = firestore.batch()
                            query.documents.forEach { batch.delete(it.reference) }
                            batch.commit().await()
                        }

                        lastDoc = if (query.documents.size == 300) query.documents.last() else null
                    } while (lastDoc != null)
                }
                log("Cloud data reset successfully. Resyncing...")
                startSync(googleUid, patientId)
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                Log.e("CloudSync", "Reset failed", e)
                log("Reset failed: ${e.message}")
                withContext(Dispatchers.Main) { onComplete(false) }
            } finally {
                syncMutex.unlock()
            }
        }
    }

    fun pullSettingsOnly(googleUid: String, patientId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            if (!syncMutex.tryLock()) {
                log("Force pull skipped: another sync is already in progress.")
                onComplete(false)
                return@launch
            }
            try {
                log("Force pulling settings from cloud...")
                val patientDoc = firestore.collection("users").document(googleUid)
                    .collection("patients").document(patientId)

                val remoteSettings = retryWithBackoff("Force Pull Settings") {
                    patientDoc.collection("config").document("settings").get().await()
                }

                if (remoteSettings.exists()) {
                    val remotePayload = remoteSettings.toObject(HistoryBackupPayload::class.java)
                    remotePayload?.let { payload ->
                        preferenceManager.restoreFromPayload(payload, isHardReset = false)
                        log("Settings force-restored from cloud.")
                        withContext(Dispatchers.Main) { onComplete(true) }
                        return@launch
                    }
                }
                log("No settings found in cloud.")
                withContext(Dispatchers.Main) { onComplete(false) }
            } catch (e: Exception) {
                // Log detallado para poder distinguir permission-denied, deserialización, timeout, etc.
                Log.e("CloudSync", "Force pull failed", e)
                log("Force pull failed: ${e.javaClass.simpleName} - ${e.message}")
                eventLogger.log(LogLevel.ERROR, "CloudSync", "Force pull settings failed", e.stackTraceToString())
                withContext(Dispatchers.Main) { onComplete(false) }
            } finally {
                syncMutex.unlock()
            }
        }
    }
}