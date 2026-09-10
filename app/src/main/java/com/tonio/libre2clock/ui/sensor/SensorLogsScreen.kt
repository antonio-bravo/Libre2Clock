package com.tonio.libre2clock.ui.sensor

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.ui.settings.SettingsViewModel
import com.tonio.libre2clock.util.buildSensorErrorSummary
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Constante para evitar recrear el formateador cada vez que se abre el diálogo
private val dialogDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

private fun currentDateTimeText(): String = dialogDateFormatter.format(Instant.now())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorLogsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val sensorLogs by viewModel.sensorLogs.collectAsStateWithLifecycle()
    val capillaryReadings by viewModel.capillaryReadings.collectAsStateWithLifecycle()
    
    var editingLog by remember { mutableStateOf<SensorLog?>(null) }
    var showOnlyFailed by remember { mutableStateOf(false) }

    // OPTIMIZACIÓN 1: Mapa para búsqueda O(1) en lugar de .find() O(N) en cada item
    val sensorErrorSummary = remember(sensorLogs, capillaryReadings) {
        buildSensorErrorSummary(sensorLogs, capillaryReadings)
    }
    val errorSummaryMap = remember(sensorErrorSummary) {
        sensorErrorSummary.associateBy { it.serialNumber }
    }

    val filteredLogs = remember(sensorLogs, showOnlyFailed) {
        if (showOnlyFailed) sensorLogs.filter { it.hasFailed } else sensorLogs
    }

    // OPTIMIZACIÓN 2: Cálculo de estadísticas en una sola pasada O(N) en lugar de filter+map+sorted
    val failedStats = remember(sensorLogs) {
        var count = 0
        var minDate: String? = null
        var maxDate: String? = null

        for (log in sensorLogs) {
            if (log.hasFailed) {
                count++
                val date = log.startDate
                if (date.isNotBlank()) {
                    if (minDate == null || date < minDate) minDate = date
                    if (maxDate == null || date > maxDate) maxDate = date
                }
            }
        }
        
        if (count == 0) null else Triple(count, minDate ?: "-", maxDate ?: "-")
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = showOnlyFailed,
                        onClick = { showOnlyFailed = !showOnlyFailed },
                        label = { Text(stringResource(R.string.sensor_log_filter_failed)) },
                        leadingIcon = if (showOnlyFailed) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }

                failedStats?.let { (count, minDate, maxDate) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = count.toString(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = stringResource(R.string.sensor_log_stats_date_range, minDate, maxDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // OPTIMIZACIÓN 3: Clave estable para evitar recomposiciones innecesarias al hacer scroll/filtrar
                    items(
                        items = filteredLogs,
                        key = { "${it.serialNumber}_${it.startDate}" }
                    ) { log ->
                        val summary = errorSummaryMap[log.serialNumber]
                        SensorLogItem(
                            log = log,
                            errorSummary = summary,
                            onEdit = { editingLog = log },
                            onDelete = { viewModel.removeSensorLog(log) }
                        )
                    }
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
fun SensorLogItem(
    log: SensorLog,
    errorSummary: com.tonio.libre2clock.util.SensorErrorSummary?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // OPTIMIZACIÓN 4: Formateador memoizado
    val displayFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy, HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

    // OPTIMIZACIÓN 5: Parseo y formateo memoizado por campo individual
    val displayStartDate = remember(log.startDate) {
        TimestampParser.parseFlexibleInstant(log.startDate)?.let { displayFormatter.format(it) } ?: log.startDate
    }
    val displayExpiryDate = remember(log.expiryDate) {
        TimestampParser.parseFlexibleInstant(log.expiryDate)?.let { displayFormatter.format(it) } ?: log.expiryDate
    }
    val displayEndDate = remember(log.endDate) {
        log.endDate?.let { 
            TimestampParser.parseFlexibleInstant(it)?.let { displayFormatter.format(it) } ?: it 
        }
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SN: ${log.serialNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (log.hasFailed) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sensor_log_start, displayStartDate),
                    style = MaterialTheme.typography.bodySmall
                )
                if (displayEndDate != null) {
                    Column {
                        Text(
                            text = stringResource(R.string.sensor_log_actual_end, displayEndDate),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (log.hasFailed) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                        if (log.hasFailed && log.actualDaysUsed != null) {
                            Text(
                                text = "${stringResource(R.string.sensor_log_days_used_label)}: ${log.actualDaysUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.sensor_log_expiry, displayExpiryDate),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if ((!log.hasFailed && log.actualDaysUsed != null) || log.errorCode != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!log.hasFailed) {
                            log.actualDaysUsed?.let {
                                Text(
                                    text = "${stringResource(R.string.sensor_log_days_used_label)}: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        log.errorCode?.let {
                            Text(
                                text = "${stringResource(R.string.sensor_log_error_code_label)}: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                if (!log.notes.isNullOrBlank()) {
                    Text(
                        text = log.notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                if (errorSummary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.sensor_error_summary_row,
                            errorSummary.samples,
                            errorSummary.avgAbsoluteDeviationPct,
                            errorSummary.avgSignedDeviationPct
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Row {
                IconButton(onClick = {
                    val text = buildString {
                        append("Sensor Log\nSN: ${log.serialNumber}\nStart: $displayStartDate\n")
                        if (displayEndDate != null) append("End: $displayEndDate\n") 
                        else append("Expected Expiry: $displayExpiryDate\n")
                        if (log.hasFailed) append("FAILED (Code: ${log.errorCode ?: "-"})\n")
                        if (log.actualDaysUsed != null) append("Days used: ${log.actualDaysUsed}\n")
                        if (!log.notes.isNullOrBlank()) append("Notes: ${log.notes}")
                    }
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