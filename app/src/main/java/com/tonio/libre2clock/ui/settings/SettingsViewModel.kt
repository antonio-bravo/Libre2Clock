package com.tonio.libre2clock.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonio.libre2clock.data.api.LibreService
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.RangeOffsetInsight
import com.tonio.libre2clock.data.model.WatchNotificationMode
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.R
import com.tonio.libre2clock.di.AppContainer
import com.tonio.libre2clock.util.LogEvent
import com.tonio.libre2clock.util.SectionPerfTelemetry
import com.tonio.libre2clock.data.sync.CloudSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs

class SettingsViewModel(
    application: Application,
    private val preferenceManager: PreferenceManager,
    private val repository: GlucoseRepository,
    private val authManager: com.tonio.libre2clock.data.sync.AuthManager
) : AndroidViewModel(application) {

    private val appContext: Context = getApplication()
    
    // OPTIMIZACIÓN 1: Constante para reducir boilerplate y tamaño de bytecode
    private val sharingStrategy = SharingStarted.WhileSubscribed(5000)
    
    // OPTIMIZACIÓN 2: Extensión privada para limpiar la declaración de StateFlows
    private fun <T> Flow<T>.stateInDefault(defaultValue: T): StateFlow<T> = 
        stateIn(viewModelScope, sharingStrategy, defaultValue)

    private val eventLogManager = AppContainer.provideEventLogManager(appContext)
    private val cloudSyncManager = AppContainer.provideCloudSyncManager(appContext)
    private val settingsCache = SettingsSectionCacheRepository(appContext)

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage = _backupStatusMessage.asStateFlow()

    private val _apiDebugOutput = MutableStateFlow<String?>(null)
    val apiDebugOutput: StateFlow<String?> = _apiDebugOutput.asStateFlow()

    private val _isApiDebugLoading = MutableStateFlow(false)
    val isApiDebugLoading: StateFlow<Boolean> = _isApiDebugLoading.asStateFlow()

    val cloudSyncDebugOutput: StateFlow<String?> = cloudSyncManager.cloudSyncDebugOutput

    private val _isCloudSyncDebugLoading = MutableStateFlow(false)
    val isCloudSyncDebugLoading: StateFlow<Boolean> = _isCloudSyncDebugLoading.asStateFlow()

    val eventLogs: StateFlow<List<LogEvent>> = eventLogManager.events
    private val _sectionPerfStats = MutableStateFlow<List<SectionPerfTelemetry.Snapshot>>(emptyList())
    val sectionPerfStats: StateFlow<List<SectionPerfTelemetry.Snapshot>> = _sectionPerfStats.asStateFlow()

    // --- StateFlows optimizados con la extensión ---
    val firebaseUser = authManager.user.stateInDefault(null)
    val patientId = preferenceManager.patientId.stateInDefault(null)
    val libreLinkUpEmail = preferenceManager.libreLinkUpEmail.stateInDefault(null)
    val isCloudSyncEnabled = preferenceManager.isCloudSyncEnabled.stateInDefault(false)
    val cloudSyncLastSuccessAt = preferenceManager.cloudSyncLastSuccessAt.stateInDefault(null)
    val settingsUpdatedAt = preferenceManager.settingsUpdatedAt.stateInDefault(null)
    val glucoseOffset = preferenceManager.glucoseOffset.stateInDefault(0)
    val glucoseOffsetRanges = preferenceManager.glucoseOffsetRanges.stateInDefault(emptyList())
    val autoAdjustEnabled = preferenceManager.autoAdjustEnabled.stateInDefault(false)
    val autoRangeOffsetMode = preferenceManager.autoRangeOffsetMode.stateInDefault(AutoRangeOffsetMode.OFF)
    val capillaryReadings = preferenceManager.capillaryReadings.stateInDefault(emptyList())
    val watchAlertsEnabled = preferenceManager.watchAlertsEnabled.stateInDefault(false)
    val watchNotificationMode = preferenceManager.watchNotificationMode.stateInDefault(WatchNotificationMode.OFF)
    val watchAlertIntervalMinutes = preferenceManager.watchAlertIntervalMinutes.stateInDefault(60)
    val watchAlertStartMinute = preferenceManager.watchAlertStartMinute.stateInDefault(0)
    val lowGlucoseAlarmEnabled = preferenceManager.lowGlucoseAlarmEnabled.stateInDefault(false)
    val highGlucoseAlarmEnabled = preferenceManager.highGlucoseAlarmEnabled.stateInDefault(false)
    val useCalibratedForAlarms = preferenceManager.useCalibratedForAlarms.stateInDefault(true)
    val lastHistoryBackupRequestAt = preferenceManager.lastHistoryBackupRequestAt.stateInDefault(null)
    val historyRetentionDays = preferenceManager.historyRetentionDays.stateInDefault(90)
    val isDemoMode = preferenceManager.isDemoMode.stateInDefault(false)
    val rapidDurationMins = preferenceManager.rapidDurationMins.stateInDefault(240)
    val slowDurationMins = preferenceManager.slowDurationMins.stateInDefault(1440)
    val icRuleConstant = preferenceManager.icRuleConstant.stateInDefault(450)
    val isfRuleConstant = preferenceManager.isfRuleConstant.stateInDefault(1800)
    val manualTdi = preferenceManager.manualTdi.stateInDefault(null)
    val manualIsf = preferenceManager.manualIsf.stateInDefault(null)
    val targetGlucose = preferenceManager.targetGlucose.stateInDefault(100)
    val insulinDoses = preferenceManager.insulinDoses.stateInDefault(emptyList())
    val sensorLogs = preferenceManager.sensorLogs.stateInDefault(emptyList())
    val activeSensorSerialNumber = preferenceManager.activeSensorSerialNumber.stateInDefault(null)
    val watchNotificationSchedules = preferenceManager.watchNotificationSchedules.stateInDefault(emptyList())
    val glucoseAlarmSchedules = preferenceManager.glucoseAlarmSchedules.stateInDefault(emptyList())
    val batteryLowThreshold = preferenceManager.batteryLowThreshold.stateInDefault(15)
    val batteryCriticalThreshold = preferenceManager.batteryCriticalThreshold.stateInDefault(5)
    val disableFastRefreshOnSlowCharge = preferenceManager.disableFastRefreshOnSlowCharge.stateInDefault(true)
    val sensorDurationDays = preferenceManager.sensorDurationDays.stateInDefault(15)

    val currentGlucose: StateFlow<GlucoseMeasurement?> = combine(
        combine(
            repository.currentGlucose,
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode
        ) { current, manualOffset, ranges, autoAdjust, autoRangeMode ->
            CurrentGlucoseInputs(current, manualOffset, ranges, autoAdjust, autoRangeMode)
        },
        preferenceManager.capillaryReadings
    ) { inputs, capillaries ->
        inputs.current?.let {
            GlucoseProcessor.process(
                measurement = it,
                manualOffset = inputs.manualOffset,
                userRanges = inputs.ranges,
                autoAdjustEnabled = inputs.autoAdjust,
                autoRangeOffsetMode = inputs.autoRangeMode,
                capillaryReadings = capillaries
            )
        }
    }.stateIn(viewModelScope, sharingStrategy, null)

    // OPTIMIZACIÓN 3: Cálculo de métricas en UNA SOLA PASADA (Zero-Allocation)
    val rangeOffsetInsights: StateFlow<List<RangeOffsetInsight>> = combine(
        preferenceManager.glucoseOffsetRanges,
        preferenceManager.capillaryReadings,
        preferenceManager.historyRetentionDays
    ) { ranges, capillaries, retentionDays ->
        Triple(ranges, capillaries, retentionDays)
    }.map { (ranges, capillaries, retentionDays) ->
        val signature = SettingsSectionCacheRepository.buildRangeInsightsSignature(ranges, capillaries)
        settingsCache.getOrComputeRangeInsights(
            signature = signature,
            retentionDays = retentionDays
        ) {
            ranges.mapNotNull { range ->
                val estimate = GlucoseProcessor.estimateOffsetsForRange(range, capillaries) ?: return@mapNotNull null
                
                val points = capillaries.mapNotNull { reading ->
                    val sensor = reading.sensorValue ?: return@mapNotNull null
                    if (sensor == 0) return@mapNotNull null
                    if (sensor < range.min) return@mapNotNull null
                    if (range.max != null && sensor >= range.max) return@mapNotNull null
                    sensor to reading.value
                }
                
                if (points.isEmpty()) return@mapNotNull null

                // Single-pass accumulation: O(N) en lugar de O(7N) y cero listas intermedias
                var sumSensor = 0.0
                var sumCapillary = 0.0
                var sumAbsDiff = 0.0
                var sumCurrentDevPct = 0.0
                var sumSignedRawBias = 0.0
                var sumSignedCalError = 0.0
                var sumSuggestedMae = 0.0
                var sumSuggestedDev = 0.0
                
                val count = points.size.toDouble()
                val calibOffset = range.offset
                val calibPct = range.percentage / 100.0
                val estOffset = estimate.offset
                val estPct = estimate.percentage / 100.0

                for ((sensor, capillary) in points) {
                    val s = sensor.toDouble()
                    val c = capillary.toDouble()
                    
                    sumSensor += s
                    sumCapillary += c
                    val absDiff = abs(s - c)
                    sumAbsDiff += absDiff

                    if (c > 0) {
                        sumCurrentDevPct += (absDiff / c) * 100.0
                        sumSignedRawBias += ((s - c) / c) * 100.0
                        
                        val calibrated = s + calibOffset + (s * calibPct)
                        sumSignedCalError += ((calibrated - c) / c) * 100.0

                        val predicted = s + estOffset + (s * estPct)
                        val predAbsDiff = abs(predicted - c)
                        sumSuggestedMae += predAbsDiff
                        sumSuggestedDev += (predAbsDiff / c) * 100.0
                    }
                }

                RangeOffsetInsight(
                    min = range.min,
                    max = range.max,
                    sampleCount = estimate.sampleCount,
                    suggestedOffset = estimate.offset,
                    suggestedPercentage = estimate.percentage,
                    currentMae = sumAbsDiff / count,
                    suggestedMae = sumSuggestedMae / count,
                    currentDeviationPct = sumCurrentDevPct / count,
                    suggestedDeviationPct = sumSuggestedDev / count,
                    avgSensorValue = sumSensor / count,
                    avgCapillaryValue = sumCapillary / count,
                    signedCalibratedDeviationPct = sumSignedCalError / count,
                    signedRawDeviationPct = sumSignedRawBias / count
                )
            }.sortedBy { it.min }
        }
    }.distinctUntilChanged().stateIn(viewModelScope, sharingStrategy, emptyList())

    // --- Acciones de Guardado (Simplificadas) ---
    fun updateOffset(offset: Int) = launchSave { preferenceManager.saveGlucoseOffset(offset) }
    fun updateAutoAdjustEnabled(enabled: Boolean) = launchSave { preferenceManager.saveAutoAdjustEnabled(enabled) }
    fun updateAutoRangeOffsetMode(mode: AutoRangeOffsetMode) = launchSave { preferenceManager.saveAutoRangeOffsetMode(mode) }
    fun updateWatchAlertIntervalMinutes(minutes: Int) = launchSave { preferenceManager.saveWatchAlertIntervalMinutes(minutes) }
    fun updateWatchAlertStartMinute(minute: Int) = launchSave { preferenceManager.saveWatchAlertStartMinute(minute) }
    fun updateLowGlucoseAlarmEnabled(enabled: Boolean) = launchSave { preferenceManager.saveLowGlucoseAlarmEnabled(enabled) }
    fun updateHighGlucoseAlarmEnabled(enabled: Boolean) = launchSave { preferenceManager.saveHighGlucoseAlarmEnabled(enabled) }
    fun updateUseCalibratedForAlarms(enabled: Boolean) = launchSave { preferenceManager.saveUseCalibratedForAlarms(enabled) }
    fun updateRapidDuration(minutes: Int) = launchSave { preferenceManager.saveRapidDurationMins(minutes) }
    fun updateSlowDuration(minutes: Int) = launchSave { preferenceManager.saveSlowDurationMins(minutes) }
    fun updateIcRuleConstant(constant: Int) = launchSave { preferenceManager.saveIcRuleConstant(constant) }
    fun updateIsfRuleConstant(constant: Int) = launchSave { preferenceManager.saveIsfRuleConstant(constant) }
    fun updateManualTdi(tdi: Double?) = launchSave { preferenceManager.saveManualTdi(tdi) }
    fun updateManualIsf(isf: Double?) = launchSave { preferenceManager.saveManualIsf(isf) }
    fun updateTargetGlucose(target: Int) = launchSave { preferenceManager.saveTargetGlucose(target) }
    fun updateBatteryLowThreshold(threshold: Int) = launchSave { preferenceManager.saveBatteryLowThreshold(threshold) }
    fun updateBatteryCriticalThreshold(threshold: Int) = launchSave { preferenceManager.saveBatteryCriticalThreshold(threshold) }
    fun updateDisableFastRefreshOnSlowCharge(disabled: Boolean) = launchSave { preferenceManager.saveDisableFastRefreshOnSlowCharge(disabled) }
    fun updateSensorDurationDays(days: Int) = launchSave { preferenceManager.saveSensorDurationDays(days) }

    // Helper para reducir boilerplate de viewModelScope.launch
    private fun launchSave(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun updateWatchAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) preferenceManager.initializeWatchAlertStartMinuteIfMissing()
            preferenceManager.saveWatchAlertsEnabled(enabled)
        }
    }

    fun updateWatchNotificationMode(mode: WatchNotificationMode) {
        viewModelScope.launch {
            if (mode != WatchNotificationMode.OFF) preferenceManager.initializeWatchAlertStartMinuteIfMissing()
            preferenceManager.saveWatchNotificationMode(mode)
        }
    }

    fun updateDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) repository.enableDemoMode() else repository.disableDemoMode()
        }
    }

    fun addCapillaryReading(reading: CapillaryMeasurement) {
        viewModelScope.launch {
            val currentReadings = capillaryReadings.value.toMutableList()
            // OPTIMIZACIÓN 4: .value es síncrono e instantáneo para StateFlow, evita suspensión innecesaria de .first()
            val activeSerial = activeSensorSerialNumber.value
            
            val withSensor = reading.copy(sensorSerialNumber = reading.sensorSerialNumber ?: activeSerial)
            currentReadings.add(withSensor)
            currentReadings.sortByDescending { it.timestamp }
            preferenceManager.saveCapillaryReadings(currentReadings)
        }
    }

    fun removeCapillaryReading(reading: CapillaryMeasurement) {
        viewModelScope.launch {
            val currentReadings = capillaryReadings.value.toMutableList().apply { remove(reading) }
            preferenceManager.saveCapillaryReadings(currentReadings)
        }
    }

    fun requestHistoryBackupNow() {
        viewModelScope.launch {
            val requested = preferenceManager.requestHistoryCloudBackupIfDue(force = true)
            _backupStatusMessage.value = if (requested) "Google backup requested." else "No Google backup request was sent."
        }
    }

    fun updateHistoryRetentionDays(days: Int) {
        viewModelScope.launch {
            preferenceManager.saveHistoryRetentionDays(days)
            refreshSectionPerfStats()
        }
    }

    fun refreshSectionPerfStats() {
        _sectionPerfStats.value = SectionPerfTelemetry.snapshot()
    }

    fun recomputeAllCache() {
        viewModelScope.launch {
            settingsCache.clearAllCache()
            repository.clearCache()
            refreshSectionPerfStats()
            _backupStatusMessage.value = "Cache cleared. Metrics will be recomputed."
        }
    }

    fun resetSectionPerfStats() {
        SectionPerfTelemetry.reset()
        _sectionPerfStats.value = emptyList()
    }

    fun requestPartialHistoryBackup(
        includeHistoricalGlucose: Boolean,
        includeCapillaryReadings: Boolean,
        includeInsulinDoses: Boolean = true,
        includeSensorLogs: Boolean = true
    ) {
        viewModelScope.launch {
            val requested = preferenceManager.requestPartialHistoryCloudBackup(
                includeHistoricalGlucose, includeCapillaryReadings, includeInsulinDoses, includeSensorLogs
            )
            _backupStatusMessage.value = if (requested) "Partial Google backup requested." else "Partial Google backup request failed."
        }
    }

    fun restorePartialHistoryFromBackup(
        includeHistoricalGlucose: Boolean,
        includeCapillaryReadings: Boolean,
        includeInsulinDoses: Boolean = true,
        includeSensorLogs: Boolean = true
    ) {
        viewModelScope.launch {
            val restored = preferenceManager.restorePartialHistoryFromBackup(
                includeHistoricalGlucose, includeCapillaryReadings, includeInsulinDoses, includeSensorLogs
            )
            if (restored) {
                settingsCache.clearAllCache()
                repository.syncLocalArchiveFromPreferences()
            }
            _backupStatusMessage.value = if (restored) "Partial restore completed." else "Partial restore failed or no backup data was found."
        }
    }

    fun exportLocalBackupToDownloads() {
        viewModelScope.launch {
            val result = preferenceManager.exportHistoryBackupToDownloads()
            _backupStatusMessage.value = result.fold(
                onSuccess = { path -> "Local backup exported to $path" },
                onFailure = { error -> error.message ?: "Local backup export failed." }
            )
        }
    }

    fun restoreLocalBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = preferenceManager.restoreHistoryBackupFromUri(uri)
            if (result.isSuccess) {
                settingsCache.clearAllCache()
                repository.syncLocalArchiveFromPreferences()
            }
            _backupStatusMessage.value = result.fold(
                onSuccess = { appContext.getString(R.string.restore_success_merge) },
                onFailure = { error -> error.message ?: appContext.getString(R.string.restore_failed) }
            )
        }
    }

    fun clearBackupStatusMessage() {
        _backupStatusMessage.value = null
    }

    fun updateCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.saveCloudSyncEnabled(enabled)
            if (enabled) cloudSyncManager.triggerManualSync()
        }
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            cloudSyncManager.triggerManualSync()
            _backupStatusMessage.value = "Manual sync triggered."
        }
    }

    fun pullCloudSettings(onComplete: (Boolean) -> Unit) {
        val user = authManager.user.value
        viewModelScope.launch {
            // OPTIMIZACIÓN 4: .value en lugar de .first()
            val patientId = patientId.value 
            if (user != null && patientId != null) {
                cloudSyncManager.pullSettingsOnly(user.uid, patientId) { success ->
                    if (success) viewModelScope.launch { settingsCache.clearAllCache() }
                    onComplete(success)
                }
            } else {
                onComplete(false)
            }
        }
    }

    fun runCloudSyncDiagnostic() {
        viewModelScope.launch {
            _isCloudSyncDebugLoading.value = true
            cloudSyncManager.runDiagnostic()
            _isCloudSyncDebugLoading.value = false
        }
    }

    fun clearCloudSyncDebugOutput() {
        cloudSyncManager.clearDebugOutput()
    }

    fun formatLogTimestamp(timestamp: Long): String = eventLogManager.formatTimestamp(timestamp)

    fun clearEventLogs() {
        viewModelScope.launch { eventLogManager.clear() }
    }

    fun resetCloudData(onComplete: (Boolean) -> Unit) {
        val user = authManager.user.value
        viewModelScope.launch {
            val patientId = patientId.value
            if (user != null && patientId != null) {
                cloudSyncManager.resetCloudData(user.uid, patientId, onComplete)
            } else {
                onComplete(false)
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            try {
                val resId = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
                if (resId == 0) {
                    _backupStatusMessage.value = "Error: google-services.json not configured."
                    return@launch
                }
                val webClientId = appContext.getString(resId)
                val result = authManager.signInWithGoogle(context, webClientId)
                _backupStatusMessage.value = result.fold(
                    onSuccess = { "Signed in with Google." },
                    onFailure = { it.message ?: "Sign in failed." }
                )
            } catch (e: Exception) {
                _backupStatusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun signOutFromGoogle() {
        viewModelScope.launch {
            authManager.signOut()
            _backupStatusMessage.value = "Signed out."
        }
    }

    fun restoreLocalBackup(uri: Uri, isHardReset: Boolean) {
        viewModelScope.launch {
            val result = preferenceManager.restoreHistoryBackupFromUri(uri, isHardReset)
            if (result.isSuccess) {
                settingsCache.clearAllCache()
                repository.syncLocalArchiveFromPreferences()
            }
            _backupStatusMessage.value = result.fold(
                onSuccess = { if (isHardReset) appContext.getString(R.string.restore_success_hard) else appContext.getString(R.string.restore_success_merge) },
                onFailure = { it.message ?: appContext.getString(R.string.restore_failed) }
            )
        }
    }

    init {
        refreshSectionPerfStats()
    }

    fun clearApiDebugOutput() {
        _apiDebugOutput.value = null
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    fun runDirectApiDiagnostic() {
        viewModelScope.launch {
            _isApiDebugLoading.value = true
            _apiDebugOutput.value = null
            try {
                val startedAt = Instant.now().toString()
                val report = buildString {
                    appendLine("=== LibreLinkUp API Diagnostic ===")
                    appendLine("Started at: $startedAt")

                    val token = preferenceManager.authToken.first()
                    val userId = preferenceManager.userId.first()
                    val storedPatientId = preferenceManager.patientId.first()
                    val demoEnabled = preferenceManager.isDemoMode.first()

                    appendLine("Demo mode: $demoEnabled")
                    appendLine("Has token: ${!token.isNullOrBlank()}")
                    appendLine("Has userId: ${!userId.isNullOrBlank()}")
                    appendLine("Stored patientId: ${storedPatientId ?: "<none>"}")

                    if (token.isNullOrBlank() || userId.isNullOrBlank()) {
                        appendLine("\nResult: FAIL\nReason: Missing credentials. Please login again.")
                        return@buildString
                    }

                    LibreService.setAuth(token, userId)
                    val connectionsResponse = LibreService.api.getConnections()
                    val connections = connectionsResponse.data ?: emptyList()
                    val firstConnectionPatientId = connections.firstOrNull()?.patientId

                    appendLine("\nGET /llu/connections")
                    appendLine("status: ${connectionsResponse.status}")
                    appendLine("connectionsCount: ${connections.size}")
                    appendLine("firstConnectionPatientId: ${firstConnectionPatientId ?: "<none>"}")

                    val patientId = storedPatientId ?: firstConnectionPatientId
                    if (patientId.isNullOrBlank()) {
                        appendLine("\nResult: FAIL\nReason: No patientId available.\nRaw connections: $connectionsResponse")
                        return@buildString
                    }

                    val graphResponse = LibreService.api.getGlucoseGraph(patientId)
                    val measurement = graphResponse.data?.connection?.glucoseMeasurement
                    val graphData = graphResponse.data?.graphData ?: emptyList()
                    val latestFromGraph = graphData.lastOrNull()

                    appendLine("\nGET /llu/connections/{patientId}/graph")
                    appendLine("patientId used: $patientId")
                    appendLine("status: ${graphResponse.status}")
                    appendLine("graphDataCount: ${graphData.size}")
                    appendLine("\nconnection.glucoseMeasurement: ${measurement ?: "<null>"}")
                    appendLine("latest graphData item: ${latestFromGraph ?: "<null>"}")

                    val effective = measurement ?: latestFromGraph
                    appendLine("\neffective value used by app:")
                    if (effective != null) {
                        appendLine("Value: ${effective.value}")
                        appendLine("ValueInMgPerDl: ${effective.valueInMgPerDl}")
                        appendLine("TrendArrow: ${effective.trendArrow}")
                        appendLine("FactoryTimestamp: ${effective.factoryTimestamp}")
                        appendLine("Timestamp: ${effective.timestamp}")
                        appendLine("Result: OK")
                    } else {
                        appendLine("<null>\nResult: FAIL\nReason: API returned no glucoseMeasurement and empty graphData.")
                    }
                }
                _apiDebugOutput.value = report
            } catch (e: Exception) {
                _apiDebugOutput.value = buildString {
                    appendLine("=== LibreLinkUp API Diagnostic ===")
                    appendLine("Result: FAIL")
                    appendLine("Exception: ${e::class.java.simpleName}")
                    appendLine("Message: ${e.message ?: "<no message>"}")
                    appendLine("Stack (top 8):")
                    e.stackTrace.take(8).forEach { appendLine("  at $it") }
                }
            } finally {
                _isApiDebugLoading.value = false
            }
        }
    }

    fun addRange(range: GlucoseOffsetRange) {
        viewModelScope.launch {
            val currentRanges = glucoseOffsetRanges.value.toMutableList().apply { 
                add(range)
                sortBy { it.min } 
            }
            preferenceManager.saveGlucoseOffsetRanges(currentRanges)
        }
    }

    fun addDefaultRange() {
        viewModelScope.launch {
            val currentRanges = glucoseOffsetRanges.value.toMutableList().apply { 
                add(GlucoseOffsetRange(0, 0, 0))
                sortBy { it.min } 
            }
            preferenceManager.saveGlucoseOffsetRanges(currentRanges)
        }
    }

    fun removeRange(range: GlucoseOffsetRange) {
        viewModelScope.launch {
            val currentRanges = glucoseOffsetRanges.value.toMutableList().apply { remove(range) }
            preferenceManager.saveGlucoseOffsetRanges(currentRanges)
        }
    }

    fun updateRange(oldRange: GlucoseOffsetRange, newRange: GlucoseOffsetRange) {
        viewModelScope.launch {
            val currentRanges = glucoseOffsetRanges.value.toMutableList()
            val index = currentRanges.indexOf(oldRange)
            if (index != -1) {
                currentRanges[index] = newRange
                currentRanges.sortBy { it.min }
                preferenceManager.saveGlucoseOffsetRanges(currentRanges)
            }
        }
    }

    fun applySuggestedRangeOffsets(minSamples: Int = 2) {
        viewModelScope.launch {
            val insightsByKey = rangeOffsetInsights.value
                .filter { it.sampleCount >= minSamples }
                .associateBy { insightKey(it.min, it.max) }

            val updated = glucoseOffsetRanges.value.map { range ->
                insightsByKey[insightKey(range.min, range.max)]?.let { insight ->
                    range.copy(offset = insight.suggestedOffset, percentage = insight.suggestedPercentage)
                } ?: range
            }
            preferenceManager.saveGlucoseOffsetRanges(updated)
        }
    }

    private fun insightKey(min: Int, max: Int?): String = "$min:${max ?: "inf"}"

    private data class CurrentGlucoseInputs(
        val current: GlucoseMeasurement?,
        val manualOffset: Int,
        val ranges: List<GlucoseOffsetRange>,
        val autoAdjust: Boolean,
        val autoRangeMode: AutoRangeOffsetMode
    )

    // --- Funciones de Insulina y Alarmas (Optimizadas con .apply) ---
    fun addInsulinDose(dose: com.tonio.libre2clock.data.model.InsulinDose) {
        viewModelScope.launch {
            val current = insulinDoses.value.toMutableList().apply { 
                add(dose)
                sortByDescending { it.timestamp } 
            }
            preferenceManager.saveInsulinDoses(current)
        }
    }

    fun removeInsulinDose(dose: com.tonio.libre2clock.data.model.InsulinDose) {
        viewModelScope.launch {
            val current = insulinDoses.value.toMutableList().apply { remove(dose) }
            preferenceManager.saveInsulinDoses(current)
        }
    }

    fun updateInsulinDose(oldDose: com.tonio.libre2clock.data.model.InsulinDose, newDose: com.tonio.libre2clock.data.model.InsulinDose) {
        viewModelScope.launch {
            val current = insulinDoses.value.toMutableList()
            val index = current.indexOf(oldDose)
            if (index != -1) {
                current[index] = newDose
                current.sortByDescending { it.timestamp }
                preferenceManager.saveInsulinDoses(current)
            }
        }
    }

    fun updateSensorLog(log: com.tonio.libre2clock.data.model.SensorLog) {
        viewModelScope.launch {
            val current = sensorLogs.value.toMutableList()
            val index = current.indexOfFirst { it.serialNumber == log.serialNumber }
            if (index != -1) {
                current[index] = log
                preferenceManager.saveSensorLogs(current)
            }
        }
    }

    fun removeSensorLog(log: com.tonio.libre2clock.data.model.SensorLog) {
        viewModelScope.launch {
            val current = sensorLogs.value.toMutableList().apply { removeIf { it.serialNumber == log.serialNumber } }
            preferenceManager.saveSensorLogs(current)
        }
    }

    fun addWatchSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = watchNotificationSchedules.value.toMutableList().apply { add(schedule) }
            preferenceManager.saveWatchNotificationSchedules(current)
        }
    }

    fun updateWatchSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = watchNotificationSchedules.value.toMutableList()
            val index = current.indexOfFirst { it.id == schedule.id }
            if (index != -1) {
                current[index] = schedule
                preferenceManager.saveWatchNotificationSchedules(current)
            }
        }
    }

    fun removeWatchSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = watchNotificationSchedules.value.toMutableList().apply { removeIf { it.id == schedule.id } }
            preferenceManager.saveWatchNotificationSchedules(current)
        }
    }

    fun addAlarmSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = glucoseAlarmSchedules.value.toMutableList().apply { add(schedule) }
            preferenceManager.saveGlucoseAlarmSchedules(current)
        }
    }

    fun updateAlarmSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = glucoseAlarmSchedules.value.toMutableList()
            val index = current.indexOfFirst { it.id == schedule.id }
            if (index != -1) {
                current[index] = schedule
                preferenceManager.saveGlucoseAlarmSchedules(current)
            }
        }
    }

    fun removeAlarmSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule) {
        viewModelScope.launch {
            val current = glucoseAlarmSchedules.value.toMutableList().apply { removeIf { it.id == schedule.id } }
            preferenceManager.saveGlucoseAlarmSchedules(current)
        }
    }
}