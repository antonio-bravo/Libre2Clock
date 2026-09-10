package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    // 1. Recopilación de estado optimizada
    val watchNotificationMode by viewModel.watchNotificationMode.collectAsStateWithLifecycle()
    val watchAlertIntervalMinutes by viewModel.watchAlertIntervalMinutes.collectAsStateWithLifecycle()
    val watchAlertStartMinute by viewModel.watchAlertStartMinute.collectAsStateWithLifecycle()
    val watchSchedules by viewModel.watchNotificationSchedules.collectAsStateWithLifecycle()
    val alarmSchedules by viewModel.glucoseAlarmSchedules.collectAsStateWithLifecycle()
    val lowGlucoseAlarmEnabled by viewModel.lowGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val highGlucoseAlarmEnabled by viewModel.highGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val useCalibratedForAlarms by viewModel.useCalibratedForAlarms.collectAsStateWithLifecycle()

    // 2. Gestión de diálogos unificada y limpia
    var editingWatchSchedule by remember { mutableStateOf<AlarmSchedule?>(null) }
    var editingAlarmSchedule by remember { mutableStateOf<AlarmSchedule?>(null) }
    var showAddWatchSchedule by remember { mutableStateOf(false) }
    var showAddAlarmSchedule by remember { mutableStateOf(false) }

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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // OPTIMIZACIÓN: Cada sección es un 'item' independiente para aislar recomposiciones
            item {
                WatchNotificationSection(
                    mode = watchNotificationMode,
                    intervalMinutes = watchAlertIntervalMinutes,
                    startMinute = watchAlertStartMinute,
                    onModeChange = viewModel::updateWatchNotificationMode,
                    onIntervalChange = viewModel::updateWatchAlertIntervalMinutes,
                    onStartMinuteChange = viewModel::updateWatchAlertStartMinute,
                    onTestNotification = onTestNotification
                )
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_watch_active_schedules)) {
                    if (watchSchedules.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_schedules_global_desc), 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // OPTIMIZACIÓN: Uso de 'items' con 'key' en lugar de 'forEach' para Lazy Loading real
            items(
                items = watchSchedules,
                key = { schedule -> "${schedule.startTime}_${schedule.daysOfWeek.hashCode()}" } // Clave estable
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
            }

            item {
                GlucoseAlarmsSection(
                    lowEnabled = lowGlucoseAlarmEnabled,
                    highEnabled = highGlucoseAlarmEnabled,
                    useCalibrated = useCalibratedForAlarms,
                    onLowChange = viewModel::updateLowGlucoseAlarmEnabled,
                    onHighChange = viewModel::updateHighGlucoseAlarmEnabled,
                    onCalibratedChange = viewModel::updateUseCalibratedForAlarms
                )
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_alarm_active_schedules)) {
                    if (alarmSchedules.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_schedules_global_desc), 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            items(
                items = alarmSchedules,
                key = { schedule -> "${schedule.startTime}_${schedule.daysOfWeek.hashCode()}" }
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
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 3. Diálogos renderizados condicionalmente de forma limpia
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

// --- Componentes Extraídos para Aislar Recomposiciones ---

@Composable
private fun WatchNotificationSection(
    mode: WatchNotificationMode,
    intervalMinutes: Int,
    startMinute: Int,
    onModeChange: (WatchNotificationMode) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onTestNotification: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_watch_notifications)) {
        Text(
            text = stringResource(R.string.settings_watch_notifications_desc),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        
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
            modes.forEach { (modeOption, labelRes) ->
                FilterChip(
                    selected = mode == modeOption,
                    onClick = { onModeChange(modeOption) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
        
        if (mode != WatchNotificationMode.OFF) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // OPTIMIZACIÓN UX: Validación suave que permite escribir "1" antes de "15"
            NumericSettingField(
                value = intervalMinutes,
                onValueChange = onIntervalChange,
                label = stringResource(R.string.settings_watch_interval_label),
                min = 5,
                max = 180
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            NumericSettingField(
                value = startMinute,
                onValueChange = onStartMinuteChange,
                label = stringResource(R.string.settings_watch_start_minute_label),
                min = 0,
                max = 59
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onTestNotification,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_test_notification))
        }
    }
}

@Composable
private fun GlucoseAlarmsSection(
    lowEnabled: Boolean,
    highEnabled: Boolean,
    useCalibrated: Boolean,
    onLowChange: (Boolean) -> Unit,
    onHighChange: (Boolean) -> Unit,
    onCalibratedChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_glucose_alarms)) {
        Text(
            text = stringResource(R.string.settings_glucose_alarms_desc), 
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        ToggleItem(R.string.settings_low_glucose_alarm_label, lowEnabled, onLowChange)
        Spacer(modifier = Modifier.height(8.dp))
        ToggleItem(R.string.settings_high_glucose_alarm_label, highEnabled, onHighChange)
        Spacer(modifier = Modifier.height(8.dp))
        ToggleItem(R.string.settings_use_calibrated_alarms, useCalibrated, onCalibratedChange)
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
    
    // Sincronizar si el valor cambia externamente (ej. reset)
    LaunchedEffect(value) {
        if (textValue != value.toString()) {
            textValue = value.toString()
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            // Permitir solo dígitos
            val digitsOnly = newText.filter { it.isDigit() }
            textValue = digitsOnly
            
            // Actualizar el ViewModel solo si es un número válido en el rango
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
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}