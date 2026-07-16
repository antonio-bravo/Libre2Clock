package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import com.tonio.libre2clock.data.model.CapillaryMeasurement
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
    val capillaryReadings by viewModel.capillaryReadings.collectAsStateWithLifecycle()
    val currentGlucose by viewModel.currentGlucose.collectAsStateWithLifecycle()
    val watchAlertsEnabled by viewModel.watchAlertsEnabled.collectAsStateWithLifecycle()
    val watchAlertIntervalMinutes by viewModel.watchAlertIntervalMinutes.collectAsStateWithLifecycle()
    val watchAlertStartMinute by viewModel.watchAlertStartMinute.collectAsStateWithLifecycle()
    val lowGlucoseAlarmEnabled by viewModel.lowGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val highGlucoseAlarmEnabled by viewModel.highGlucoseAlarmEnabled.collectAsStateWithLifecycle()
    val lastHistoryBackupRequestAt by viewModel.lastHistoryBackupRequestAt.collectAsStateWithLifecycle()

    var showAddRangeDialog by remember { mutableStateOf(false) }
    var editingRange by remember { mutableStateOf<GlucoseOffsetRange?>(null) }
    var showCapillaryDialog by remember { mutableStateOf(false) }
    var capillaryValueText by remember { mutableStateOf("") }
    var capillaryDateText by remember { mutableStateOf("") }

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
                    text = "Capillary readings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Save finger-stick measurements to let the app estimate a better calibration.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OutlinedButton(
                    onClick = {
                        capillaryDateText = currentDateTimeText()
                        showCapillaryDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save capillary reading")
                }
                if (capillaryReadings.isEmpty()) {
                    Text(
                        text = "No capillary readings saved yet.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    capillaryReadings.forEach { reading ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "${reading.value} mg/dL", fontWeight = FontWeight.Bold)
                                    Text(text = reading.timestamp, style = MaterialTheme.typography.bodySmall)
                                    reading.sensorValue?.let {
                                        Text(
                                            text = "Sensor: $it mg/dL",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    reading.delta?.let {
                                        val deltaText = if (it >= 0) "+$it" else "$it"
                                        Text(
                                            text = "Delta capilar-sensor: $deltaText mg/dL",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.removeCapillaryReading(reading) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }

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

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "History Backup",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Libre2Clock keeps the historical glucose archive locally and asks Android to back it up to your Google account. Restore happens automatically when Android restores app data.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = lastHistoryBackupRequestAt?.let {
                        "Last backup request: ${formatBackupTimestamp(it)}"
                    } ?: "No backup request has been sent yet.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = viewModel::requestHistoryBackupNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("Request Google Backup Now")
                }
                Text(
                    text = "Partial backup/restore works over the same Google backup payload file.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(
                    onClick = {
                        viewModel.requestPartialHistoryBackup(
                            includeHistoricalGlucose = true,
                            includeCapillaryReadings = false
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Backup only glucose history")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.requestPartialHistoryBackup(
                            includeHistoricalGlucose = false,
                            includeCapillaryReadings = true
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Backup only capillary readings")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.restorePartialHistoryFromBackup(
                            includeHistoricalGlucose = true,
                            includeCapillaryReadings = false
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Restore only glucose history (merge)")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.restorePartialHistoryFromBackup(
                            includeHistoricalGlucose = false,
                            includeCapillaryReadings = true
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Restore only capillary readings (merge)")
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

    if (showCapillaryDialog) {
        AlertDialog(
            onDismissRequest = { showCapillaryDialog = false },
            title = { Text("Save capillary reading") },
            text = {
                Column {
                    OutlinedTextField(
                        value = capillaryValueText,
                        onValueChange = { capillaryValueText = it },
                        label = { Text("Value (mg/dL)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = capillaryDateText,
                        onValueChange = { capillaryDateText = it },
                        label = { Text("Date and time (yyyy-MM-dd HH:mm)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val sensorValue = currentGlucose?.value
                    OutlinedTextField(
                        value = sensorValue?.toString() ?: "No sensor reading available",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Current sensor reading") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val capillaryValue = capillaryValueText.toIntOrNull()
                    val delta = if (capillaryValue != null && sensorValue != null) {
                        capillaryValue - sensorValue
                    } else {
                        null
                    }
                    if (delta != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val deltaText = if (delta >= 0) "+$delta" else "$delta"
                        Text(
                            text = "Delta at save time: $deltaText mg/dL",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = capillaryValueText.toIntOrNull() ?: return@TextButton
                    val sensorValue = currentGlucose?.value
                    val delta = sensorValue?.let { value - it }
                    val timestamp = capillaryDateText.ifBlank { currentDateTimeText() }
                    viewModel.addCapillaryReading(
                        CapillaryMeasurement(
                            value = value,
                            timestamp = timestamp,
                            sensorValue = sensorValue,
                            delta = delta
                        )
                    )
                    capillaryValueText = ""
                    capillaryDateText = currentDateTimeText()
                    showCapillaryDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCapillaryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatBackupTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestamp))
}

private fun currentDateTimeText(): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())
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
