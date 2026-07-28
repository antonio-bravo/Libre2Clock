package com.tonio.libre2clock.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val watchAlertsEnabled by viewModel.watchAlertsEnabled.collectAsStateWithLifecycle()
    val watchAlertIntervalMinutes by viewModel.watchAlertIntervalMinutes.collectAsStateWithLifecycle()
    val watchAlertStartMinute by viewModel.watchAlertStartMinute.collectAsStateWithLifecycle()
    val lowGlucoseAlarmEnabled by viewModel.lowGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val highGlucoseAlarmEnabled by viewModel.highGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val useCalibratedForAlarms by viewModel.useCalibratedForAlarms.collectAsStateWithLifecycle()
    val lastHistoryBackupRequestAt by viewModel.lastHistoryBackupRequestAt.collectAsStateWithLifecycle()
    val historyRetentionDays by viewModel.historyRetentionDays.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsStateWithLifecycle()
    val watchSchedules by viewModel.watchNotificationSchedules.collectAsStateWithLifecycle()
    val alarmSchedules by viewModel.glucoseAlarmSchedules.collectAsStateWithLifecycle()

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
                title = { Text("Settings") },
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
                    text = "Global Manual Offset",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Applied to ALL readings in addition to range-based offsets.",
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
                    label = { Text("Manual Offset (mg/dL)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Switch(checked = autoAdjustEnabled, onCheckedChange = viewModel::updateAutoAdjustEnabled)
                Text(
                    text = "Auto-adjust using stored capillary readings",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Watch notifications",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Send glucose and trend notifications at a fixed interval so Zepp Life can mirror them to your Bip S.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable periodic watch push",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = watchAlertsEnabled,
                        onCheckedChange = viewModel::updateWatchAlertsEnabled
                    )
                }
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
                    label = { Text("Interval (minutes, 5-180)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = watchAlertsEnabled
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
                    label = { Text("Start minute (0-59)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = watchAlertsEnabled
                )
                Text(
                    text = "Notifications are sent when minute matches this start point and then every interval.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Watch Schedules
                Spacer(modifier = Modifier.height(12.dp))
                Text("Watch Active Schedules", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (watchSchedules.isEmpty()) {
                    Text("Always active (Global switch). Add a schedule to restrict.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                    Text("Add Watch Schedule", style = MaterialTheme.typography.labelMedium)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Glucose alarms",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Enable or disable high/low alarms independently. Periodic watch push remains independent.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Low glucose alarm (< 70 mg/dL)",
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
                        text = "High glucose alarm (> 180 mg/dL)",
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
                Text("Alarm Active Schedules", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (alarmSchedules.isEmpty()) {
                    Text("Always active (Global switches). Add a schedule to restrict.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                    Text("Add Alarm Schedule", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Demo Mode",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Simulate glucose readings and sensor status for testing purposes.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Demo Mode",
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
                    text = "History & Backup",
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
                    label = { Text("Retention days (30-365)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    text = "Libre2Clock keeps your history locally and asks Android to back it up to your Google account.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Google Cloud Backup Section
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Google Cloud Backup", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = lastHistoryBackupRequestAt?.let { "Last request: ${formatBackupTimestamp(it)}" } ?: "No request sent yet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = viewModel::requestHistoryBackupNow,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Request Google Backup Now")
                        }
                    }
                }

                // Local File Section
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Local JSON Backup", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::exportLocalBackupToDownloads, modifier = Modifier.weight(1f)) {
                                Text("Export")
                            }
                            OutlinedButton(onClick = { localBackupRestoreLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                                Text("Restore")
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
                        Text("Advanced Partial Actions")
                    }
                    DropdownMenu(
                        expanded = showAdvancedDropdown,
                        onDismissRequest = { showAdvancedDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text("BACKUP ONLY", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.secondary)
                        DropdownMenuItem(
                            text = { Text("Glucose History") },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.requestPartialHistoryBackup(includeHistoricalGlucose = true, includeCapillaryReadings = false, includeInsulinDoses = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Capillary Readings") },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.requestPartialHistoryBackup(includeHistoricalGlucose = false, includeCapillaryReadings = true, includeInsulinDoses = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Insulin Doses") },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.requestPartialHistoryBackup(includeHistoricalGlucose = false, includeCapillaryReadings = false, includeInsulinDoses = true)
                            }
                        )
                        HorizontalDivider()
                        Text("RESTORE ONLY (Merge)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.secondary)
                        DropdownMenuItem(
                            text = { Text("Glucose History") },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.restorePartialHistoryFromBackup(includeHistoricalGlucose = true, includeCapillaryReadings = false, includeInsulinDoses = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Capillary Readings") },
                            onClick = { 
                                showAdvancedDropdown = false
                                viewModel.restorePartialHistoryFromBackup(includeHistoricalGlucose = false, includeCapillaryReadings = true, includeInsulinDoses = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Insulin Doses") },
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
                    text = "Range-Based Offsets",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Define specific offsets for different glucose ranges.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            if (ranges.isEmpty()) {
                item {
                    Text(
                        text = "No ranges defined. Click 'Add Range' to start.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(ranges) { range ->
                    RangeItem(
                        range = range,
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
                        Text("Add Range")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Watch Sync Test",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Trigger a mock notification to verify that glucose data is correctly mirrored to your Amazfit Bip S.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Button(
                    onClick = onTestNotification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 80.dp) // Space for FAB
                ) {
                    Text("Test Notification")
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
        title = { Text(if (initialSchedule == null) "Add Active Schedule" else "Edit Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start (HH:mm)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End (HH:mm)") }, modifier = Modifier.weight(1f))
                }
                
                if (isWatchSchedule) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Watch Overrides (Optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text("Interval (m)") },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Global") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = startMinuteText,
                            onValueChange = { startMinuteText = it },
                            label = { Text("Start Minute") },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Global") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Text("Active Days", style = MaterialTheme.typography.labelMedium)
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
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
                    text = "Range: ${range.min} - $maxText mg/dL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fixed offset: ${if (range.offset >= 0) "+" else ""}${range.offset} mg/dL",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Percentage offset: ${if (range.percentage >= 0) "+" else ""}${range.percentage}%",
                    style = MaterialTheme.typography.bodyMedium
                )
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
        title = { Text(if (initialRange == null) "Add Range" else "Edit Range") },
        text = {
            Column {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text("Min Glucose") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text("Max Glucose (leave empty for ∞)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = offsetText,
                    onValueChange = { offsetText = it },
                    label = { Text("Fixed offset (mg/dL)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = percentageText,
                    onValueChange = { percentageText = it },
                    label = { Text("Percentage offset (%)") },
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
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
