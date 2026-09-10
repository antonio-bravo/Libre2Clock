package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onNavigateToBattery: () -> Unit,
    onNavigateToDevice: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToCloud: () -> Unit,
    onNavigateToAdvanced: () -> Unit
) {
    val sectionPerfStats by viewModel.sectionPerfStats.collectAsStateWithLifecycle()
    val libreLinkUpEmail by viewModel.libreLinkUpEmail.collectAsStateWithLifecycle()

    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // OPTIMIZACIÓN: Memoizar las búsquedas para evitar ejecutarlas en cada recomposición
    val dashboardEnterStats = remember(sectionPerfStats) { 
        sectionPerfStats.find { it.section == "dashboard_screen_enter" } 
    }
    val historicalStats = remember(sectionPerfStats) { 
        sectionPerfStats.find { it.section == "historical_metrics_v2" } 
    }
    val calibrationStats = remember(sectionPerfStats) { 
        sectionPerfStats.find { it.section == "settings_range_insights_v1" } 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        // OPTIMIZACIÓN: Usar spacedBy elimina la necesidad de Spacers manuales entre items
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // OPTIMIZACIÓN: Cada sección es un 'item' independiente con 'key' para aislar recomposiciones
            item(key = "account_header") {
                Spacer(modifier = Modifier.height(8.dp))
                AccountHeaderSection(email = libreLinkUpEmail)
            }

            item(key = "alerts") {
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_watch_notifications),
                    description = stringResource(R.string.settings_watch_notifications_desc),
                    icon = Icons.Default.Watch,
                    onClick = onNavigateToAlerts
                )
            }

            item(key = "calibration") {
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_range_based_offsets),
                    description = stringResource(R.string.settings_range_based_offsets_desc),
                    icon = Icons.Default.Tune,
                    perfStats = calibrationStats,
                    onClick = onNavigateToCalibration
                )
            }

            item(key = "battery") {
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_battery_optimization),
                    description = stringResource(R.string.settings_battery_optimization_desc),
                    icon = Icons.Default.BatteryChargingFull,
                    onClick = onNavigateToBattery
                )
            }

            item(key = "device") {
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_device_title),
                    description = stringResource(R.string.settings_device_desc),
                    icon = Icons.Default.Memory,
                    onClick = onNavigateToDevice
                )
            }

            item(key = "data") {
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_history_backup),
                    description = stringResource(R.string.settings_history_backup_desc),
                    icon = Icons.Default.Backup,
                    perfStats = historicalStats,
                    onClick = onNavigateToData
                )
            }

            item(key = "cloud") {
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_cloud_sync_title),
                    description = stringResource(R.string.settings_cloud_sync_desc),
                    icon = Icons.Default.CloudSync,
                    onClick = onNavigateToCloud
                )
            }

            item(key = "advanced") {
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_perf_title),
                    description = stringResource(R.string.settings_perf_desc),
                    icon = Icons.Default.Analytics,
                    perfStats = dashboardEnterStats,
                    onClick = onNavigateToAdvanced
                )
            }

            item(key = "logout_section") {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_logout))
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirmDialog = false
                    viewModel.logout()
                }) {
                    Text(stringResource(R.string.settings_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

// --- Componente Extraído para Aislar Recomposiciones ---

@Composable
private fun AccountHeaderSection(email: String?) {
    SettingsSection(title = stringResource(R.string.settings_llu_account_header)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = email ?: stringResource(R.string.settings_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.settings_llu_account_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}