package com.tonio.libre2clock.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToCloud: () -> Unit
) {
    val lastHistoryBackupRequestAt by viewModel.lastHistoryBackupRequestAt.collectAsStateWithLifecycle()
    val historyRetentionDays by viewModel.historyRetentionDays.collectAsStateWithLifecycle()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsStateWithLifecycle()
    val sectionPerfStats by viewModel.sectionPerfStats.collectAsStateWithLifecycle()

    // OPTIMIZACIÓN: Búsqueda cacheada con remember
    val historicalStats = remember(sectionPerfStats) {
        sectionPerfStats.find { it.section == "historical_metrics_v2" }
    }

    var restoreUriToProcess by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val localBackupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreUriToProcess = uri
            showRestoreConfirmDialog = true
        }
    }

    if (showRestoreConfirmDialog && restoreUriToProcess != null) {
        AlertDialog(
            onDismissRequest = { 
                showRestoreConfirmDialog = false
                restoreUriToProcess = null
            },
            title = { Text(stringResource(R.string.restore_dialog_title)) },
            // CORRECCIÓN i18n: String hardcodeado reemplazado por stringResource
            text = { Text(stringResource(R.string.restore_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    restoreUriToProcess?.let { uri ->
                        viewModel.restoreLocalBackup(uri, isHardReset = false)
                    }
                    restoreUriToProcess = null
                }) {
                    Text(stringResource(R.string.restore_merge_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRestoreConfirmDialog = false
                    restoreUriToProcess = null
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // OPTIMIZACIÓN: Auto-limpieza del mensaje de estado con cancelación automática
    LaunchedEffect(backupStatusMessage) {
        if (backupStatusMessage != null) {
            delay(4000)
            viewModel.clearBackupStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_history_backup)) },
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
            // OPTIMIZACIÓN: Cada sección es un 'item' independiente con 'key'
            item(key = "retention_section") {
                RetentionDaysSection(
                    currentDays = historyRetentionDays,
                    onDaysChange = viewModel::updateHistoryRetentionDays
                )
            }

            item(key = "cloud_section") {
                CloudSyncSection(onNavigateToCloud = onNavigateToCloud)
            }

            item(key = "local_backup_section") {
                LocalBackupSection(
                    onExport = viewModel::exportLocalBackupToDownloads,
                    onRestore = { localBackupRestoreLauncher.launch(arrayOf("application/json")) }
                )
            }

            if (backupStatusMessage != null) {
                item(key = "status_message") {
                    Text(
                        text = backupStatusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            item(key = "advanced_actions") {
                AdvancedBackupActions(viewModel)
            }

            if (historicalStats != null) {
                item(key = "perf_card") {
                    SectionPerformanceCard(
                        historicalStats,
                        stringResource(R.string.settings_perf_loading_time)
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// --- Componentes Extraídos para Aislar Recomposiciones ---

@Composable
private fun RetentionDaysSection(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_retention_days_label)) {
        RetentionDaysField(
            initialValue = currentDays,
            onValueChange = onDaysChange
        )
    }
}

@Composable
private fun RetentionDaysField(
    initialValue: Int,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue.toString()) }
    
    // Sincronizar si el valor cambia externamente
    LaunchedEffect(initialValue) {
        if (textValue != initialValue.toString()) {
            textValue = initialValue.toString()
        }
    }

    // OPTIMIZACIÓN: Debounce de 600ms antes de guardar
    LaunchedEffect(textValue) {
        delay(600)
        textValue.toIntOrNull()?.let { days ->
            if (days in 30..365) {
                onValueChange(days)
            }
        }
    }

    val isValid = textValue.toIntOrNull()?.let { it in 30..365 } ?: false

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            textValue = newText.filter { it.isDigit() }
        },
        label = { Text(stringResource(R.string.settings_retention_days_label)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        ),
        isError = textValue.isNotEmpty() && !isValid,
        supportingText = {
            if (textValue.isNotEmpty() && !isValid) {
                Text(
                    stringResource(R.string.settings_retention_days_error),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
private fun CloudSyncSection(onNavigateToCloud: () -> Unit) {
    SettingsSection(title = stringResource(R.string.settings_cloud_sync_title)) {
        Text(
            text = stringResource(R.string.settings_cloud_sync_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onNavigateToCloud, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cloud_configure_button))
        }
    }
}

@Composable
private fun LocalBackupSection(
    onExport: () -> Unit,
    onRestore: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_local_json_backup)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_export))
            }
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_restore))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedBackupActions(viewModel: SettingsViewModel) {
    var showAdvancedDropdown by remember { mutableStateOf(false) }
    
    // OPTIMIZACIÓN: Dropdown mejorado con estructura más limpia
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { showAdvancedDropdown = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_advanced_partial_actions))
        }
        
        DropdownMenu(
            expanded = showAdvancedDropdown,
            onDismissRequest = { showAdvancedDropdown = false }
        ) {
            // Sección: Backup parcial
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.settings_backup_only_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                },
                onClick = { },
                enabled = false
            )
            
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_glucose_history)) },
                onClick = { 
                    showAdvancedDropdown = false
                    viewModel.requestPartialHistoryBackup(true, false, false, false)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_capillary)) },
                onClick = { 
                    showAdvancedDropdown = false
                    viewModel.requestPartialHistoryBackup(false, true, false, false)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_insulin_logs)) },
                onClick = { 
                    showAdvancedDropdown = false
                    viewModel.requestPartialHistoryBackup(false, false, true, false)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_sensor_logs)) },
                onClick = { 
                    showAdvancedDropdown = false
                    viewModel.requestPartialHistoryBackup(false, false, false, true)
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            // Sección: Restauración parcial
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.settings_restore_only_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                },
                onClick = { },
                enabled = false
            )
            
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_glucose_history)) },
                onClick = { 
                    showAdvancedDropdown = false
                    viewModel.restorePartialHistoryFromBackup(true, false, false, false)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_capillary)) },
                onClick = { 
                    showAdvancedDropdown = false
                    viewModel.restorePartialHistoryFromBackup(false, true, false, false)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_insulin_logs)) },
                onClick = { 
                    showAdvancedDropdown = false
                    viewModel.restorePartialHistoryFromBackup(false, false, true, false)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_sensor_logs)) },
                onClick = { 
                    showAdvancedDropdown = false
                    viewModel.restorePartialHistoryFromBackup(false, false, false, true)
                }
            )
        }
    }
}