package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.AlarmSchedule
import com.tonio.libre2clock.data.model.WatchNotificationMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAlertsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onTestNotification: () -> Unit
) {
    val watchNotificationMode by viewModel.watchNotificationMode.collectAsStateWithLifecycle()
    val watchAlertIntervalMinutes by viewModel.watchAlertIntervalMinutes.collectAsStateWithLifecycle()
    val watchAlertStartMinute by viewModel.watchAlertStartMinute.collectAsStateWithLifecycle()
    val watchSchedules by viewModel.watchNotificationSchedules.collectAsStateWithLifecycle()
    val alarmSchedules by viewModel.glucoseAlarmSchedules.collectAsStateWithLifecycle()
    val lowGlucoseAlarmEnabled by viewModel.lowGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val highGlucoseAlarmEnabled by viewModel.highGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val useCalibratedForAlarms by viewModel.useCalibratedForAlarms.collectAsStateWithLifecycle()

    var showAddWatchScheduleDialog by remember { mutableStateOf(false) }
    var editingWatchSchedule by remember { mutableStateOf<AlarmSchedule?>(null) }
    var showAddAlarmScheduleDialog by remember { mutableStateOf(false) }
    var editingAlarmSchedule by remember { mutableStateOf<AlarmSchedule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_watch_notifications)) },
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
                SettingsSection(title = stringResource(R.string.settings_watch_notifications)) {
                    Text(
                        text = stringResource(R.string.settings_watch_notifications_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            WatchNotificationMode.OFF to R.string.settings_watch_mode_off,
                            WatchNotificationMode.PERIODIC_ONLY to R.string.settings_watch_mode_periodic,
                            WatchNotificationMode.SCHEDULES_ONLY to R.string.settings_watch_mode_schedules,
                            WatchNotificationMode.PERIODIC_AND_SCHEDULES to R.string.settings_watch_mode_all
                        )
                        modes.forEach { (mode, labelRes) ->
                            FilterChip(
                                selected = watchNotificationMode == mode,
                                onClick = { viewModel.updateWatchNotificationMode(mode) },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                    
                    if (watchNotificationMode != WatchNotificationMode.OFF) {
                        var watchIntervalText by remember(watchAlertIntervalMinutes) {
                            mutableStateOf(watchAlertIntervalMinutes.toString())
                        }
                        OutlinedTextField(
                            value = watchIntervalText,
                            onValueChange = {
                                watchIntervalText = it
                                it.toIntOrNull()?.let { viewModel.updateWatchAlertIntervalMinutes(it) }
                            },
                            label = { Text(stringResource(R.string.settings_watch_interval_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        var watchStartMinuteText by remember(watchAlertStartMinute) {
                            mutableStateOf(watchAlertStartMinute.toString())
                        }
                        OutlinedTextField(
                            value = watchStartMinuteText,
                            onValueChange = {
                                watchStartMinuteText = it
                                it.toIntOrNull()?.let { viewModel.updateWatchAlertStartMinute(it) }
                            },
                            label = { Text(stringResource(R.string.settings_watch_start_minute_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onTestNotification,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_test_notification))
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_watch_active_schedules)) {
                    if (watchSchedules.isEmpty()) {
                        Text(stringResource(R.string.settings_schedules_global_desc), style = MaterialTheme.typography.bodySmall)
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(stringResource(R.string.settings_add_watch_schedule))
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_glucose_alarms)) {
                    Text(stringResource(R.string.settings_glucose_alarms_desc), style = MaterialTheme.typography.bodyMedium)
                    
                    ToggleItem(R.string.settings_low_glucose_alarm_label, lowGlucoseAlarmEnabled, viewModel::updateLowGlucoseAlarmEnabled)
                    ToggleItem(R.string.settings_high_glucose_alarm_label, highGlucoseAlarmEnabled, viewModel::updateHighGlucoseAlarmEnabled)
                    ToggleItem(R.string.settings_use_calibrated_alarms, useCalibratedForAlarms, viewModel::updateUseCalibratedForAlarms)
                }

                SettingsSection(title = stringResource(R.string.settings_alarm_active_schedules)) {
                    if (alarmSchedules.isEmpty()) {
                        Text(stringResource(R.string.settings_schedules_global_desc), style = MaterialTheme.typography.bodySmall)
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(stringResource(R.string.settings_add_alarm_schedule))
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAddWatchScheduleDialog) {
        ScheduleDialog(isWatchSchedule = true, onDismiss = { showAddWatchScheduleDialog = false }, onConfirm = { viewModel.addWatchSchedule(it); showAddWatchScheduleDialog = false })
    }
    editingWatchSchedule?.let { s ->
        ScheduleDialog(isWatchSchedule = true, initialSchedule = s, onDismiss = { editingWatchSchedule = null }, onConfirm = { viewModel.updateWatchSchedule(it); editingWatchSchedule = null })
    }
    if (showAddAlarmScheduleDialog) {
        ScheduleDialog(isWatchSchedule = false, onDismiss = { showAddAlarmScheduleDialog = false }, onConfirm = { viewModel.addAlarmSchedule(it); showAddAlarmScheduleDialog = false })
    }
    editingAlarmSchedule?.let { s ->
        ScheduleDialog(isWatchSchedule = false, initialSchedule = s, onDismiss = { editingAlarmSchedule = null }, onConfirm = { viewModel.updateAlarmSchedule(it); editingAlarmSchedule = null })
    }
}

@Composable
private fun ToggleItem(labelRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
