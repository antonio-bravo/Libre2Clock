package com.tonio.libre2clock.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R

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

    val historicalStats = sectionPerfStats.find { it.section == "historical_metrics_v2" }

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
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("Restaurar Copia de Seguridad") },
            text = { Text("¿Cómo quieres restaurar los datos? \n\nFusionar: Combina con los datos actuales.\nHard Reset: Borra todo y deja solo lo del backup.") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreLocalBackup(restoreUriToProcess!!, isHardReset = false)
                    }) {
                        Text("Fusionar")
                    }
                    TextButton(onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreLocalBackup(restoreUriToProcess!!, isHardReset = true)
                    }) {
                        Text("Hard Reset", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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
                .padding(horizontal = 16.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_retention_days_label)) {
                    var historyRetentionDaysText by remember(historyRetentionDays) {
                        mutableStateOf(historyRetentionDays.toString())
                    }
                    OutlinedTextField(
                        value = historyRetentionDaysText,
                        onValueChange = {
                            historyRetentionDaysText = it
                            it.toIntOrNull()?.let { viewModel.updateHistoryRetentionDays(it) }
                        },
                        label = { Text(stringResource(R.string.settings_retention_days_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                SettingsSection(title = "Sincronización en la Nube") {
                    Text(text = "Sincroniza automáticamente tus datos con tu cuenta de Google.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onNavigateToCloud, modifier = Modifier.fillMaxWidth()) {
                        Text("Configurar Sincronización")
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_local_json_backup)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::exportLocalBackupToDownloads, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_export))
                        }
                        OutlinedButton(onClick = { localBackupRestoreLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_restore))
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

                AdvancedBackupActions(viewModel)
                
                historicalStats?.let { stats ->
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionPerformanceCard(stats, stringResource(R.string.settings_perf_loading_time))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AdvancedBackupActions(viewModel: SettingsViewModel) {
    var showAdvancedDropdown by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedButton(
            onClick = { showAdvancedDropdown = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_advanced_partial_actions))
        }
        DropdownMenu(
            expanded = showAdvancedDropdown,
            onDismissRequest = { showAdvancedDropdown = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Text(stringResource(R.string.settings_backup_only_header), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.secondary)
            val partials = listOf(
                R.string.settings_glucose_history to { viewModel.requestPartialHistoryBackup(true, false, false, false) },
                R.string.menu_capillary to { viewModel.requestPartialHistoryBackup(false, true, false, false) },
                R.string.menu_insulin_logs to { viewModel.requestPartialHistoryBackup(false, false, true, false) },
                R.string.menu_sensor_logs to { viewModel.requestPartialHistoryBackup(false, false, false, true) }
            )
            partials.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { showAdvancedDropdown = false; action() })
            }
            HorizontalDivider()
            Text(stringResource(R.string.settings_restore_only_header), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.secondary)
            val restores = listOf(
                R.string.settings_glucose_history to { viewModel.restorePartialHistoryFromBackup(true, false, false, false) },
                R.string.menu_capillary to { viewModel.restorePartialHistoryFromBackup(false, true, false, false) },
                R.string.menu_insulin_logs to { viewModel.restorePartialHistoryFromBackup(false, false, true, false) },
                R.string.menu_sensor_logs to { viewModel.restorePartialHistoryFromBackup(false, false, false, true) }
            )
            restores.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { showAdvancedDropdown = false; action() })
            }
        }
    }
}
