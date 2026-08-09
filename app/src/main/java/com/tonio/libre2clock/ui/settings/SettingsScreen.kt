package com.tonio.libre2clock.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.RangeOffsetInsight
import com.tonio.libre2clock.data.model.WatchNotificationMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onTestNotification: () -> Unit
) {
    val offset by viewModel.glucoseOffset.collectAsStateWithLifecycle()
    val ranges by viewModel.glucoseOffsetRanges.collectAsStateWithLifecycle()
    val autoAdjustEnabled by viewModel.autoAdjustEnabled.collectAsStateWithLifecycle()
    val autoRangeOffsetMode by viewModel.autoRangeOffsetMode.collectAsStateWithLifecycle()
    val watchNotificationMode by viewModel.watchNotificationMode.collectAsStateWithLifecycle()
    val watchAlertIntervalMinutes by viewModel.watchAlertIntervalMinutes.collectAsStateWithLifecycle()
    val watchAlertStartMinute by viewModel.watchAlertStartMinute.collectAsStateWithLifecycle()
    val rangeInsights by viewModel.rangeOffsetInsights.collectAsStateWithLifecycle()
    val lowGlucoseAlarmEnabled by viewModel.lowGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val highGlucoseAlarmEnabled by viewModel.highGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val useCalibratedForAlarms by viewModel.useCalibratedForAlarms.collectAsStateWithLifecycle()
    val lastHistoryBackupRequestAt by viewModel.lastHistoryBackupRequestAt.collectAsStateWithLifecycle()
    val historyRetentionDays by viewModel.historyRetentionDays.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsStateWithLifecycle()
    val watchSchedules by viewModel.watchNotificationSchedules.collectAsStateWithLifecycle()
    val alarmSchedules by viewModel.glucoseAlarmSchedules.collectAsStateWithLifecycle()
    val batteryLowThreshold by viewModel.batteryLowThreshold.collectAsStateWithLifecycle()
    val batteryCriticalThreshold by viewModel.batteryCriticalThreshold.collectAsStateWithLifecycle()
    val disableFastOnSlowCharge by viewModel.disableFastRefreshOnSlowCharge.collectAsStateWithLifecycle()
    val isApiDebugLoading by viewModel.isApiDebugLoading.collectAsStateWithLifecycle()
    val apiDebugOutput by viewModel.apiDebugOutput.collectAsStateWithLifecycle()

    var showAddRangeDialog by remember { mutableStateOf(false) }
    var editingRange by remember { mutableStateOf<GlucoseOffsetRange?>(null) }
    
    var showAddWatchScheduleDialog by remember { mutableStateOf(false) }
    var editingWatchSchedule by remember { mutableStateOf<com.tonio.libre2clock.data.model.AlarmSchedule?>(null) }
    
    var showAddAlarmScheduleDialog by remember { mutableStateOf(false) }
    var editingAlarmSchedule by remember { mutableStateOf<com.tonio.libre2clock.data.model.AlarmSchedule?>(null) }

    val localBackupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::restoreLocalBackupFromUri)
    }

    LaunchedEffect(backupStatusMessage) {
        if (backupStatusMessage != null) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearBackupStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_global_manual_offset),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = stringResource(R.string.settings_global_offset_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                var offsetText by remember(offset) { mutableStateOf(offset.toString()) }
                OutlinedTextField(
                    value = offsetText,
                    onValueChange = {
                        offsetText = it
                        it.toIntOrNull()?.let { newOffset ->
                            viewModel.updateOffset(newOffset)
                        }
                    },
                    label = { Text(stringResource(R.string.settings_manual_offset_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Switch(checked = autoAdjustEnabled, onCheckedChange = viewModel::updateAutoAdjustEnabled)
                Text(
                    text = stringResource(R.string.settings_auto_adjust_capillary),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_watch_notifications),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_watch_notifications_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = watchNotificationMode == WatchNotificationMode.OFF,
                        onClick = { viewModel.updateWatchNotificationMode(WatchNotificationMode.OFF) },
                        label = { Text(stringResource(R.string.settings_watch_mode_off)) }
                    )
                    FilterChip(
                        selected = watchNotificationMode == WatchNotificationMode.PERIODIC_ONLY,
                        onClick = { viewModel.updateWatchNotificationMode(WatchNotificationMode.PERIODIC_ONLY) },
                        label = { Text(stringResource(R.string.settings_watch_mode_periodic)) }
                    )
                    FilterChip(
                        selected = watchNotificationMode == WatchNotificationMode.SCHEDULES_ONLY,
                        onClick = { viewModel.updateWatchNotificationMode(WatchNotificationMode.SCHEDULES_ONLY) },
                        label = { Text(stringResource(R.string.settings_watch_mode_schedules)) }
                    )
                    FilterChip(
                        selected = watchNotificationMode == WatchNotificationMode.PERIODIC_AND_SCHEDULES,
                        onClick = { viewModel.updateWatchNotificationMode(WatchNotificationMode.PERIODIC_AND_SCHEDULES) },
                        label = { Text(stringResource(R.string.settings_watch_mode_all)) }
                    )
                }
                Text(
                    text = when (watchNotificationMode) {
                        WatchNotificationMode.OFF -> stringResource(R.string.settings_watch_desc_off)
                        WatchNotificationMode.PERIODIC_ONLY -> stringResource(R.string.settings_watch_desc_periodic)
                        WatchNotificationMode.SCHEDULES_ONLY -> stringResource(R.string.settings_watch_desc_schedules)
                        WatchNotificationMode.PERIODIC_AND_SCHEDULES -> stringResource(R.string.settings_watch_desc_all)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                var watchIntervalText by remember(watchAlertIntervalMinutes) {
                    mutableStateOf(watchAlertIntervalMinutes.toString())
                }
                OutlinedTextField(
                    value = watchIntervalText,
                    onValueChange = {
                        watchIntervalText = it
                        it.toIntOrNull()?.let { minutes ->
                            viewModel.updateWatchAlertIntervalMinutes(minutes)
                        }
                    },
                    label = { Text(stringResource(R.string.settings_watch_interval_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = watchNotificationMode != WatchNotificationMode.OFF
                )
                var watchStartMinuteText by remember(watchAlertStartMinute) {
                    mutableStateOf(watchAlertStartMinute.toString())
                }
                OutlinedTextField(
                    value = watchStartMinuteText,
                    onValueChange = {
                        watchStartMinuteText = it
                        it.toIntOrNull()?.let { minute ->
                            viewModel.updateWatchAlertStartMinute(minute)
                        }
                    },
                    label = { Text(stringResource(R.string.settings_watch_start_minute_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = watchNotificationMode != WatchNotificationMode.OFF
                )
                Text(
                    text = stringResource(R.string.settings_watch_trigger_desc),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Watch Schedules
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.settings_watch_active_schedules), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (watchSchedules.isEmpty()) {
                    Text(stringResource(R.string.settings_schedules_global_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                watchSchedules.forEach { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        onDelete = { viewModel.removeWatchSchedule(schedule) },
                        onEdit = { editingWatchSchedule = schedule },
                        onToggle = { viewModel.updateWatchSchedule(schedule.copy(isEnabled = it)) }
                    )
                }
                OutlinedButton(
                    onClick = { showAddWatchScheduleDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.settings_add_watch_schedule), style = MaterialTheme.typography.labelMedium)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_glucose_alarms),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_glucose_alarms_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_low_glucose_alarm_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = lowGlucoseAlarmEnabled,
                        onCheckedChange = viewModel::updateLowGlucoseAlarmEnabled
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_high_glucose_alarm_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = highGlucoseAlarmEnabled,
                        onCheckedChange = viewModel::updateHighGlucoseAlarmEnabled
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_use_calibrated_alarms),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = useCalibratedForAlarms,
                        onCheckedChange = viewModel::updateUseCalibratedForAlarms
                    )
                }
                
                // Alarm Schedules
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.settings_alarm_active_schedules), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (alarmSchedules.isEmpty()) {
                    Text(stringResource(R.string.settings_schedules_global_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                alarmSchedules.forEach { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        onDelete = { viewModel.removeAlarmSchedule(schedule) },
                        onEdit = { editingAlarmSchedule = schedule },
                        onToggle = { viewModel.updateAlarmSchedule(schedule.copy(isEnabled = it)) }
                    )
                }
                OutlinedButton(
                    onClick = { showAddAlarmScheduleDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.settings_add_alarm_schedule), style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_battery_optimization),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_battery_optimization_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Low Battery Threshold
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(text = stringResource(R.string.settings_battery_low_threshold, batteryLowThreshold), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = batteryLowThreshold.toFloat(),
                        onValueChange = { viewModel.updateBatteryLowThreshold(it.toInt()) },
                        valueRange = 5f..50f,
                        steps = 8
                    )
                    Text(text = stringResource(R.string.settings_battery_low_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                // Critical Battery Threshold
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(text = stringResource(R.string.settings_battery_critical_threshold, batteryCriticalThreshold), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = batteryCriticalThreshold.toFloat(),
                        onValueChange = { viewModel.updateBatteryCriticalThreshold(it.toInt()) },
                        valueRange = 1f..15f,
                        steps = 13
                    )
                    Text(text = stringResource(R.string.settings_battery_critical_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                // Slow Charge Protection
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.settings_slow_charge_protection), style = MaterialTheme.typography.bodyMedium)
                        Text(text = stringResource(R.string.settings_slow_charge_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(checked = disableFastOnSlowCharge, onCheckedChange = viewModel::updateDisableFastRefreshOnSlowCharge)
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.demo_mode),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_demo_mode_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_enable_demo_mode),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = viewModel::updateDemoMode
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_history_backup),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                var historyRetentionDaysText by remember(historyRetentionDays) {
                    mutableStateOf(historyRetentionDays.toString())
                }
                OutlinedTextField(
                    value = historyRetentionDaysText,
                    onValueChange = {
                        historyRetentionDaysText = it
                        it.toIntOrNull()?.let { days ->
                            viewModel.updateHistoryRetentionDays(days)
                        }
                    },
                    label = { Text(stringResource(R.string.settings_retention_days_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    text = stringResource(R.string.settings_history_backup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Google Cloud Backup Section
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.settings_google_cloud_backup), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = lastHistoryBackupRequestAt?.let { stringResource(R.string.settings_last_request, formatBackupTimestamp(it)) } ?: stringResource(R.string.settings_no_request),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = viewModel::requestHistoryBackupNow,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.settings_request_backup_now))
                        }
                    }
                }

                // Local File Section
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.settings_local_json_backup), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::exportLocalBackupToDownloads, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_export))
                            }
                            OutlinedButton(onClick = { localBackupRestoreLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_restore))
                            }
                        }
                    }
                }

                if (backupStatusMessage != null) {
                    Text(
                        text = backupStatusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Advanced Actions Dropdown
                var showAdvancedDropdown by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showAdvancedDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_advanced_partial_actions))
                    }
                    DropdownMenu(
                        expanded = showAdvancedDropdown,
                        onDismissRequest = { showAdvancedDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text(stringResource(R.string.settings_backup_only_header), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.secondary)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_glucose_history)) },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.requestPartialHistoryBackup(includeHistoricalGlucose = true, includeCapillaryReadings = false, includeInsulinDoses = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_capillary)) },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.requestPartialHistoryBackup(includeHistoricalGlucose = false, includeCapillaryReadings = true, includeInsulinDoses = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_insulin_logs)) },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.requestPartialHistoryBackup(includeHistoricalGlucose = false, includeCapillaryReadings = false, includeInsulinDoses = true)
                            }
                        )
                        HorizontalDivider()
                        Text(stringResource(R.string.settings_restore_only_header), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.secondary)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_glucose_history)) },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.restorePartialHistoryFromBackup(includeHistoricalGlucose = true, includeCapillaryReadings = false, includeInsulinDoses = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_capillary)) },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.restorePartialHistoryFromBackup(includeHistoricalGlucose = false, includeCapillaryReadings = true, includeInsulinDoses = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_insulin_logs)) },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.restorePartialHistoryFromBackup(includeHistoricalGlucose = false, includeCapillaryReadings = false, includeInsulinDoses = true)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_range_based_offsets),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_range_based_offsets_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Total Tests Counter
                val totalTests = rangeInsights.sumOf { it.sampleCount }
                if (totalTests > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_range_total_tests, totalTests),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_auto_range_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = autoRangeOffsetMode != AutoRangeOffsetMode.OFF,
                        onCheckedChange = { enabled ->
                            viewModel.updateAutoRangeOffsetMode(
                                if (enabled) AutoRangeOffsetMode.BY_RANGE else AutoRangeOffsetMode.OFF
                            )
                        }
                    )
                }
                Text(
                    text = when (autoRangeOffsetMode) {
                        AutoRangeOffsetMode.OFF -> stringResource(R.string.settings_auto_range_off_desc)
                        AutoRangeOffsetMode.GLOBAL -> stringResource(R.string.settings_auto_range_global_desc)
                        AutoRangeOffsetMode.BY_RANGE -> stringResource(R.string.settings_auto_range_by_range_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (rangeInsights.isNotEmpty()) {
                    val applicable = rangeInsights.count { it.sampleCount >= 2 }
                    OutlinedButton(
                        onClick = { viewModel.applySuggestedRangeOffsets() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.settings_apply_intelligent_suggestions, applicable))
                    }
                }
            }
            if (ranges.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_no_ranges_defined),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(ranges) { range ->
                    val insight = rangeInsights.firstOrNull { it.min == range.min && it.max == range.max }
                    RangeItem(
                        range = range,
                        insight = insight,
                        onDelete = { viewModel.removeRange(range) },
                        onEdit = { editingRange = range }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.addDefaultRange() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_add_range))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_api_diagnostic_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_api_diagnostic_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Button(
                    onClick = viewModel::runDirectApiDiagnostic,
                    enabled = !isApiDebugLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isApiDebugLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_api_diagnostic_run))
                }

                if (apiDebugOutput != null) {
                    OutlinedButton(
                        onClick = viewModel::clearApiDebugOutput,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.settings_api_diagnostic_clear))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        SelectionContainer {
                            Text(
                                text = apiDebugOutput!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_watch_sync_test),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_watch_sync_test_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Button(
                    onClick = onTestNotification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 80.dp) // Space for FAB
                ) {
                    Text(stringResource(R.string.settings_test_notification))
                }
            }
        }
    }

    if (showAddRangeDialog) {
        RangeDialog(
            onDismiss = { showAddRangeDialog = false },
            onConfirm = { 
                viewModel.addRange(it)
                showAddRangeDialog = false
            }
        )
    }

    editingRange?.let { range ->
        RangeDialog(
            initialRange = range,
            onDismiss = { editingRange = null },
            onConfirm = {
                viewModel.updateRange(range, it)
                editingRange = null
            }
        )
    }

    if (showAddWatchScheduleDialog) {
        ScheduleDialog(
            isWatchSchedule = true,
            onDismiss = { showAddWatchScheduleDialog = false },
            onConfirm = { 
                viewModel.addWatchSchedule(it)
                showAddWatchScheduleDialog = false
            }
        )
    }

    editingWatchSchedule?.let { schedule ->
        ScheduleDialog(
            isWatchSchedule = true,
            initialSchedule = schedule,
            onDismiss = { editingWatchSchedule = null },
            onConfirm = {
                viewModel.updateWatchSchedule(it)
                editingWatchSchedule = null
            }
        )
    }

    if (showAddAlarmScheduleDialog) {
        ScheduleDialog(
            isWatchSchedule = false,
            onDismiss = { showAddAlarmScheduleDialog = false },
            onConfirm = { 
                viewModel.addAlarmSchedule(it)
                showAddAlarmScheduleDialog = false
            }
        )
    }

    editingAlarmSchedule?.let { schedule ->
        ScheduleDialog(
            isWatchSchedule = false,
            initialSchedule = schedule,
            onDismiss = { editingAlarmSchedule = null },
            onConfirm = {
                viewModel.updateAlarmSchedule(it)
                editingAlarmSchedule = null
            }
        )
    }
}

@Composable
fun ScheduleItem(
    schedule: com.tonio.libre2clock.data.model.AlarmSchedule,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = schedule.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${schedule.startTime} - ${schedule.endTime}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (schedule.intervalMinutes != null || schedule.startMinute != null) {
                    val intv = schedule.intervalMinutes ?: "Global"
                    val start = schedule.startMinute ?: "Global"
                    Text(text = "Interval: ${intv}m | Start: :${start}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                val days = schedule.daysOfWeek.sorted().joinToString(", ") { day ->
                    when (day) {
                        1 -> "Mon"
                        2 -> "Tue"
                        3 -> "Wed"
                        4 -> "Thu"
                        5 -> "Fri"
                        6 -> "Sat"
                        7 -> "Sun"
                        else -> ""
                    }
                }
                Text(text = days, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = schedule.isEnabled, onCheckedChange = onToggle, modifier = Modifier.scale(0.7f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
fun ScheduleDialog(
    isWatchSchedule: Boolean,
    initialSchedule: com.tonio.libre2clock.data.model.AlarmSchedule? = null,
    onDismiss: () -> Unit,
    onConfirm: (com.tonio.libre2clock.data.model.AlarmSchedule) -> Unit
) {
    var name by remember { mutableStateOf(initialSchedule?.name ?: "Normal Schedule") }
    var startTime by remember { mutableStateOf(initialSchedule?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(initialSchedule?.endTime ?: "22:00") }
    var selectedDays by remember { mutableStateOf(initialSchedule?.daysOfWeek?.toSet() ?: (1..7).toSet()) }
    
    var intervalText by remember { mutableStateOf(initialSchedule?.intervalMinutes?.toString() ?: "") }
    var startMinuteText by remember { mutableStateOf(initialSchedule?.startMinute?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialSchedule == null) R.string.settings_add_active_schedule else R.string.settings_edit_schedule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.settings_schedule_name)) }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text(stringResource(R.string.settings_schedule_start)) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text(stringResource(R.string.settings_schedule_end)) }, modifier = Modifier.weight(1f))
                }
                
                if (isWatchSchedule) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.settings_watch_overrides), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text(stringResource(R.string.settings_override_interval)) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.settings_global_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = startMinuteText,
                            onValueChange = { startMinuteText = it },
                            label = { Text(stringResource(R.string.settings_override_start_minute)) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.settings_global_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Text(stringResource(R.string.settings_active_days), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { day ->
                        val label = when(day) { 1 -> "M"; 2 -> "T"; 3 -> "W"; 4 -> "T"; 5 -> "F"; 6 -> "S"; 7 -> "S"; else -> "" }
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                if (day in selectedDays) selectedDays = selectedDays - day
                                else selectedDays = selectedDays + day
                            },
                            label = { Text(label) },
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    com.tonio.libre2clock.data.model.AlarmSchedule(
                        id = initialSchedule?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name,
                        startTime = startTime,
                        endTime = endTime,
                        daysOfWeek = selectedDays.toList(),
                        isEnabled = initialSchedule?.isEnabled ?: true,
                        intervalMinutes = intervalText.toIntOrNull(),
                        startMinute = startMinuteText.toIntOrNull()
                    )
                )
            }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

private fun formatBackupTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestamp))
}

@Composable
fun RangeItem(
    range: GlucoseOffsetRange,
    insight: RangeOffsetInsight?,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val maxText = range.max?.toString() ?: "∞"
                Text(
                    text = stringResource(R.string.settings_range_label, range.min, maxText),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_range_fixed_offset, if (range.offset >= 0) "+" else "", range.offset),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = stringResource(R.string.settings_range_percentage_offset, if (range.percentage >= 0) "+" else "", range.percentage),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (insight != null) {
                    Text(
                        text = stringResource(
                            R.string.settings_range_sensor_audit,
                            if (insight.signedRawDeviationPct >= 0) "+" else "",
                            insight.signedRawDeviationPct,
                            insight.sampleCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (abs(insight.signedRawDeviationPct) > 15.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_range_no_data_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
fun RangeDialog(
    initialRange: GlucoseOffsetRange? = null,
    onDismiss: () -> Unit,
    onConfirm: (GlucoseOffsetRange) -> Unit
) {
    var minText by remember { mutableStateOf(initialRange?.min?.toString() ?: "") }
    var maxText by remember { mutableStateOf(initialRange?.max?.toString() ?: "") }
    var offsetText by remember { mutableStateOf(initialRange?.offset?.toString() ?: "") }
    var percentageText by remember { mutableStateOf(initialRange?.percentage?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialRange == null) R.string.settings_add_range else R.string.settings_edit_range)) },
        text = {
            Column {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text(stringResource(R.string.settings_min_glucose)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text(stringResource(R.string.settings_max_glucose_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = offsetText,
                    onValueChange = { offsetText = it },
                    label = { Text(stringResource(R.string.settings_fixed_offset_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = percentageText,
                    onValueChange = { percentageText = it },
                    label = { Text(stringResource(R.string.settings_percentage_offset_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val min = minText.toIntOrNull() ?: 0
                    val max = maxText.toIntOrNull()
                    val offset = offsetText.toIntOrNull() ?: 0
                    val percentage = percentageText.toIntOrNull() ?: 0
                    onConfirm(GlucoseOffsetRange(min, max, offset, percentage))
                }
            ) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
