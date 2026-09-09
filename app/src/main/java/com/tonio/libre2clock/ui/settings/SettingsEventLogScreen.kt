package com.tonio.libre2clock.ui.settings

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.tonio.libre2clock.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.util.LogEvent
import com.tonio.libre2clock.util.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEventLogScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val events by viewModel.eventLogs.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
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
                    IconButton(onClick = { viewModel.clearEventLogs() }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.event_log_clear_tooltip))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.event_log_empty), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(events) { event ->
                    EventLogItem(
                        event = event,
                        formatTimestamp = viewModel::formatLogTimestamp,
                        onClick = { selectedEvent = event }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (selectedEvent != null) {
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text("${selectedEvent!!.level} - ${selectedEvent!!.tag}") },
            text = {
                Column {
                    Text(viewModel.formatLogTimestamp(selectedEvent!!.timestamp), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(selectedEvent!!.message, fontWeight = FontWeight.Bold)
                    if (selectedEvent!!.detail != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                selectedEvent!!.detail!!,
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
                    val textToCopy = "Time: ${viewModel.formatLogTimestamp(selectedEvent!!.timestamp)}\n" +
                            "Level: ${selectedEvent!!.level}\n" +
                            "Tag: ${selectedEvent!!.tag}\n" +
                            "Message: ${selectedEvent!!.message}\n" +
                            "Detail: ${selectedEvent!!.detail ?: "N/A"}"
                    clipboardManager.setText(AnnotatedString(textToCopy))
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
    val color = when (event.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> Color(0xFFFFA500)
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
    }

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
                text = formatTimestamp(event.timestamp),
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
    }
}
