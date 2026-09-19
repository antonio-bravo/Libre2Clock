package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.AlarmSchedule
import com.tonio.libre2clock.data.model.WatchNotificationMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAlertsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onTestNotification: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val testNotificationSentMessage = stringResource(R.string.settings_watch_test_sent)

    // Estados recopilados de forma limpia
    val watchNotificationMode by viewModel.watchNotificationMode.collectAsStateWithLifecycle()
    val watchAlertIntervalMinutes by viewModel.watchAlertIntervalMinutes.collectAsStateWithLifecycle()
    val watchAlertStartMinute by viewModel.watchAlertStartMinute.collectAsStateWithLifecycle()
    val watchSchedules by viewModel.watchNotificationSchedules.collectAsStateWithLifecycle()
    val alarmSchedules by viewModel.glucoseAlarmSchedules.collectAsStateWithLifecycle()
    val lowGlucoseAlarmEnabled by viewModel.lowGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val highGlucoseAlarmEnabled by viewModel.highGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val useCalibratedForAlarms by viewModel.useCalibratedForAlarms.collectAsStateWithLifecycle()
    val predictiveAlarmsEnabled by viewModel.predictiveAlarmsEnabled.collectAsStateWithLifecycle()

    var editingWatchSchedule by remember { mutableStateOf<AlarmSchedule?>(null) }
    var editingAlarmSchedule by remember { mutableStateOf<AlarmSchedule?>(null) }
    var showAddWatchSchedule by remember { mutableStateOf(false) }
    var showAddAlarmSchedule by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_watch_notifications)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                SecondaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.settings_tab_watch), fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Watch, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.settings_tab_alarms), fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedTab == 0) {
                // --- PESTAÑA 1: NOTIFICACIONES AL RELOJ ---
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Watch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.settings_watch_banner_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    item {
                        WatchNotificationModeCard(
                            mode = watchNotificationMode,
                            intervalMinutes = watchAlertIntervalMinutes,
                            startMinute = watchAlertStartMinute,
                            onModeChange = viewModel::updateWatchNotificationMode,
                            onIntervalChange = viewModel::updateWatchAlertIntervalMinutes,
                            onStartMinuteChange = viewModel::updateWatchAlertStartMinute,
                            onTestNotification = {
                                onTestNotification()
                                scope.launch {
                                    snackbarHostState.showSnackbar(testNotificationSentMessage)
                                }
                            }
                        )
                    }

                    item {
                        SettingsSection(title = stringResource(R.string.settings_watch_active_schedules)) {
                            Text(
                                text = stringResource(R.string.settings_schedules_global_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(
                        items = watchSchedules,
                        key = { schedule -> "${schedule.id}_${schedule.startTime}_${schedule.daysOfWeek.hashCode()}" }
                    ) { schedule ->
                        ScheduleItem(
                            schedule = schedule,
                            onDelete = { viewModel.removeWatchSchedule(schedule) },
                            onEdit = { editingWatchSchedule = schedule },
                            onToggle = { viewModel.updateWatchSchedule(schedule.copy(isEnabled = it)) }
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = { showAddWatchSchedule = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_add_watch_schedule))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else {
                // --- PESTAÑA 2: ALARMAS DE GLUCOSA ---
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        GlucoseAlarmsCard(
                            lowEnabled = lowGlucoseAlarmEnabled,
                            highEnabled = highGlucoseAlarmEnabled,
                            useCalibrated = useCalibratedForAlarms,
                            predictiveEnabled = predictiveAlarmsEnabled,
                            onLowChange = viewModel::updateLowGlucoseAlarmEnabled,
                            onHighChange = viewModel::updateHighGlucoseAlarmEnabled,
                            onCalibratedChange = viewModel::updateUseCalibratedForAlarms,
                            onPredictiveChange = viewModel::updatePredictiveAlarmsEnabled
                        )
                    }

                    item {
                        SettingsSection(title = stringResource(R.string.settings_alarm_active_schedules)) {
                            Text(
                                text = stringResource(R.string.settings_schedules_global_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(
                        items = alarmSchedules,
                        key = { schedule -> "${schedule.id}_${schedule.startTime}_${schedule.daysOfWeek.hashCode()}" }
                    ) { schedule ->
                        ScheduleItem(
                            schedule = schedule,
                            onDelete = { viewModel.removeAlarmSchedule(schedule) },
                            onEdit = { editingAlarmSchedule = schedule },
                            onToggle = { viewModel.updateAlarmSchedule(schedule.copy(isEnabled = it)) }
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = { showAddAlarmSchedule = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_add_alarm_schedule))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Diálogos de adición y edición
    if (showAddWatchSchedule) {
        ScheduleDialog(
            isWatchSchedule = true,
            onDismiss = { showAddWatchSchedule = false },
            onConfirm = { viewModel.addWatchSchedule(it); showAddWatchSchedule = false }
        )
    }

    editingWatchSchedule?.let { schedule ->
        ScheduleDialog(
            isWatchSchedule = true,
            initialSchedule = schedule,
            onDismiss = { editingWatchSchedule = null },
            onConfirm = { viewModel.updateWatchSchedule(it); editingWatchSchedule = null }
        )
    }

    if (showAddAlarmSchedule) {
        ScheduleDialog(
            isWatchSchedule = false,
            onDismiss = { showAddAlarmSchedule = false },
            onConfirm = { viewModel.addAlarmSchedule(it); showAddAlarmSchedule = false }
        )
    }

    editingAlarmSchedule?.let { schedule ->
        ScheduleDialog(
            isWatchSchedule = false,
            initialSchedule = schedule,
            onDismiss = { editingAlarmSchedule = null },
            onConfirm = { viewModel.updateAlarmSchedule(it); editingAlarmSchedule = null }
        )
    }
}

@Composable
private fun WatchNotificationModeCard(
    mode: WatchNotificationMode,
    intervalMinutes: Int,
    startMinute: Int,
    onModeChange: (WatchNotificationMode) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onTestNotification: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_watch_notifications)) {
        val modes = listOf(
            WatchNotificationMode.OFF to (R.string.settings_watch_mode_off to R.string.settings_watch_desc_off),
            WatchNotificationMode.PERIODIC_ONLY to (R.string.settings_watch_mode_periodic to R.string.settings_watch_desc_periodic),
            WatchNotificationMode.SCHEDULES_ONLY to (R.string.settings_watch_mode_schedules to R.string.settings_watch_desc_schedules),
            WatchNotificationMode.PERIODIC_AND_SCHEDULES to (R.string.settings_watch_mode_all to R.string.settings_watch_desc_all)
        )

        modes.forEach { (modeOption, strings) ->
            val (titleRes, descRes) = strings
            val isSelected = mode == modeOption

            Surface(
                onClick = { onModeChange(modeOption) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onModeChange(modeOption) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (mode != WatchNotificationMode.OFF) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_watch_quick_presets),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Presets rápidos
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(5, 10, 15, 30, 60)
                presets.forEach { preset ->
                    FilterChip(
                        selected = intervalMinutes == preset,
                        onClick = { onIntervalChange(preset) },
                        label = { Text("$preset min") },
                        leadingIcon = if (intervalMinutes == preset) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            NumericSettingField(
                value = intervalMinutes,
                onValueChange = onIntervalChange,
                label = stringResource(R.string.settings_watch_interval_label),
                min = 5,
                max = 180
            )

            Spacer(modifier = Modifier.height(12.dp))

            NumericSettingField(
                value = startMinute,
                onValueChange = onStartMinuteChange,
                label = stringResource(R.string.settings_watch_start_minute_label),
                min = 0,
                max = 59
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_watch_start_minute_help),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onTestNotification,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_test_notification))
        }
    }
}

@Composable
private fun GlucoseAlarmsCard(
    lowEnabled: Boolean,
    highEnabled: Boolean,
    useCalibrated: Boolean,
    predictiveEnabled: Boolean,
    onLowChange: (Boolean) -> Unit,
    onHighChange: (Boolean) -> Unit,
    onCalibratedChange: (Boolean) -> Unit,
    onPredictiveChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_glucose_alarms)) {
        Text(
            text = stringResource(R.string.settings_glucose_alarms_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        ToggleItem(R.string.settings_low_glucose_alarm_label, lowEnabled, onLowChange)
        Spacer(modifier = Modifier.height(12.dp))
        ToggleItem(R.string.settings_high_glucose_alarm_label, highEnabled, onHighChange)
        Spacer(modifier = Modifier.height(12.dp))
        ToggleItem(R.string.settings_use_calibrated_alarms, useCalibrated, onCalibratedChange)
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(16.dp))

        ToggleItemWithSubtitle(
            title = stringResource(R.string.settings_predictive_alarms),
            subtitle = stringResource(R.string.settings_predictive_alarms_subtitle),
            checked = predictiveEnabled,
            onCheckedChange = onPredictiveChange
        )
    }
}

@Composable
private fun ToggleItemWithSubtitle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumericSettingField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    min: Int,
    max: Int
) {
    var textValue by remember { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        if (textValue != value.toString()) {
            textValue = value.toString()
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            val digitsOnly = newText.filter { it.isDigit() }
            textValue = digitsOnly

            val intValue = digitsOnly.toIntOrNull()
            if (intValue != null && intValue in min..max) {
                onValueChange(intValue)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = textValue.toIntOrNull()?.let { it < min || it > max } == true && textValue.isNotEmpty()
    )
}

@Composable
private fun ToggleItem(labelRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
