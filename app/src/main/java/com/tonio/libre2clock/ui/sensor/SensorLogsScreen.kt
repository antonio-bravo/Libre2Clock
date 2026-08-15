package com.tonio.libre2clock.ui.sensor

import android.content.ClipData
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.ui.settings.SettingsViewModel
import com.tonio.libre2clock.util.buildSensorErrorSummary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorLogsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val sensorLogs by viewModel.sensorLogs.collectAsStateWithLifecycle()
    val capillaryReadings by viewModel.capillaryReadings.collectAsStateWithLifecycle()
    val sensorErrorSummary = remember(sensorLogs, capillaryReadings) {
        buildSensorErrorSummary(sensorLogs, capillaryReadings)
    }
    var editingLog by remember { mutableStateOf<SensorLog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sensor_logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (sensorLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.sensor_log_no_logs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (sensorErrorSummary.isNotEmpty()) {
                    item {
                        SensorErrorSummaryCard(sensorErrorSummary = sensorErrorSummary)
                    }
                }

                items(sensorLogs) { log ->
                    SensorLogItem(
                        log = log,
                        onEdit = { editingLog = log },
                        onDelete = { viewModel.removeSensorLog(log) }
                    )
                }
            }
        }
    }

    editingLog?.let { log ->
        SensorLogEditDialog(
            log = log,
            onDismiss = { editingLog = null },
            onConfirm = { updated ->
                viewModel.updateSensorLog(updated)
                editingLog = null
            }
        )
    }
}

@Composable
private fun SensorErrorSummaryCard(
    sensorErrorSummary: List<com.tonio.libre2clock.util.SensorErrorSummary>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sensor_error_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            sensorErrorSummary.forEach { summary ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SN: ${summary.serialNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(
                            R.string.sensor_error_summary_row,
                            summary.samples,
                            summary.avgAbsoluteDeviationPct,
                            summary.avgSignedDeviationPct
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun SensorLogItem(
    log: SensorLog,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (log.hasFailed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SN: ${log.serialNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (log.hasFailed) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = stringResource(R.string.sensor_log_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sensor_log_start, log.startDate),
                    style = MaterialTheme.typography.bodySmall
                )
                if (log.endDate != null) {
                    Text(
                        text = stringResource(R.string.sensor_log_actual_end, log.endDate),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (log.hasFailed) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sensor_log_expiry, log.expiryDate),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (log.actualDaysUsed != null || log.errorCode != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        log.actualDaysUsed?.let {
                            Text(
                                text = stringResource(R.string.sensor_log_days_used_label) + ": $it",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        log.errorCode?.let {
                            Text(
                                text = stringResource(R.string.sensor_log_error_code_label) + ": $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                log.notes?.let {
                    if (it.isNotBlank()) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            Row {
                IconButton(onClick = {
                    val text = "Sensor Log\nSN: ${log.serialNumber}\nStart: ${log.startDate}\n" +
                            (if (log.endDate != null) "End: ${log.endDate}" else "Expected Expiry: ${log.expiryDate}") +
                            (if (log.hasFailed) "\nFAILED (Code: ${log.errorCode ?: "-"})" else "") +
                            (if (log.actualDaysUsed != null) "\nDays used: ${log.actualDaysUsed}" else "")
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Sensor Log", text)))
                    }
                    Toast.makeText(context, context.getString(R.string.sensor_log_copy_success), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
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
fun SensorLogEditDialog(
    log: SensorLog,
    onDismiss: () -> Unit,
    onConfirm: (SensorLog) -> Unit
) {
    var hasFailed by remember { mutableStateOf(log.hasFailed) }
    var endDate by remember { mutableStateOf(log.endDate ?: "") }
    var notes by remember { mutableStateOf(log.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sensor_log_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasFailed,
                        onCheckedChange = { 
                            hasFailed = it
                            if (it && endDate.isBlank()) {
                                endDate = currentDateTimeText()
                            }
                        }
                    )
                    Text(stringResource(R.string.sensor_log_mark_failed))
                }
                
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text(stringResource(R.string.sensor_log_end_date_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("yyyy-MM-dd HH:mm") }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.sensor_log_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    log.copy(
                        hasFailed = hasFailed,
                        endDate = endDate.ifBlank { null },
                        notes = notes.ifBlank { null }
                    )
                )
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private fun currentDateTimeText(): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())
}
