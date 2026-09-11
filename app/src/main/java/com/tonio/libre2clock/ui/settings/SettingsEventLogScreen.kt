package com.tonio.libre2clock.ui.settings

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.util.LogEvent
import com.tonio.libre2clock.util.LogLevel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEventLogScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val events by viewModel.eventLogs.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var selectedEvent by remember { mutableStateOf<LogEvent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.event_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearEventLogs() },
                        enabled = events.isNotEmpty() // OPTIMIZACIÓN: Deshabilitar si está vacío
                    ) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = stringResource(R.string.event_log_clear_tooltip)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding), 
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.event_log_empty), 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                // OPTIMIZACIÓN: Clave estable para reciclado eficiente de elementos
                items(
                    items = events,
                    key = { event -> "${event.timestamp}_${event.tag}" }
                ) { event ->
                    EventLogItem(
                        event = event,
                        formatTimestamp = viewModel::formatLogTimestamp,
                        onClick = { selectedEvent = event }
                    )
                }
            }
        }
    }

    // OPTIMIZACIÓN: Uso de ?.let para evitar el uso de !! y hacer el código más seguro
    selectedEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text("${event.level.name} - ${event.tag}") },
            text = {
                // OPTIMIZACIÓN: Memoizar el formateo de la fecha para evitar recálculos
                val formattedTime = remember(event.timestamp) { 
                    viewModel.formatLogTimestamp(event.timestamp) 
                }
                
                Column {
                    Text(
                        text = formattedTime, 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = event.message, 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    event.detail?.let { detail ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = detail,
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // OPTIMIZACIÓN: buildString es mucho más eficiente que la concatenación con +
                    val textToCopy = buildString {
                        append("Time: ").appendLine(viewModel.formatLogTimestamp(event.timestamp))
                        append("Level: ").appendLine(event.level.name)
                        append("Tag: ").appendLine(event.tag)
                        append("Message: ").appendLine(event.message)
                        append("Detail: ").append(event.detail ?: "N/A")
                    }
                    
                    coroutineScope.launch {
                        val clipData = ClipData.newPlainText("Event Log", textToCopy)
                        clipboard.setClipEntry(ClipEntry(clipData))
                    }
                    selectedEvent = null
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.event_log_copy_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEvent = null }) {
                    Text(stringResource(R.string.event_log_close))
                }
            }
        )
    }
}

@Composable
private fun EventLogItem(
    event: LogEvent,
    formatTimestamp: (Long) -> String,
    onClick: () -> Unit
) {
    // OPTIMIZACIÓN: El color se calcula una vez por item.
    // Al cubrir todos los casos del enum, 'else' no es necesario.
    val color = when (event.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> Color(0xFFFFA500) // Naranja para warnings
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
    }

    // OPTIMIZACIÓN: Evita formatear la fecha en cada recomposición
    val formattedTime = remember(event.timestamp) { formatTimestamp(event.timestamp) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.level.name,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "[${event.tag}] ${event.message}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2
        )
        // OPTIMIZACIÓN: Divider integrado en el item para una estructura de LazyColumn más limpia
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}