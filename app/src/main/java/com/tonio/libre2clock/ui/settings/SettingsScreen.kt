package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import kotlin.math.roundToInt

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
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    val sectionPerfStats by viewModel.sectionPerfStats.collectAsStateWithLifecycle()
    val dashboardEnterStats = sectionPerfStats.find { it.section == "dashboard_screen_enter" }
    val historicalStats = sectionPerfStats.find { it.section == "historical_metrics_v2" }
    val calibrationStats = sectionPerfStats.find { it.section == "settings_range_insights_v1" }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsCategoryItem(
                    title = stringResource(R.string.settings_watch_notifications),
                    description = stringResource(R.string.settings_watch_notifications_desc),
                    icon = Icons.Default.Watch,
                    onClick = onNavigateToAlerts
                )

                SettingsCategoryItem(
                    title = stringResource(R.string.settings_range_based_offsets),
                    description = stringResource(R.string.settings_range_based_offsets_desc),
                    icon = Icons.Default.Tune,
                    perfStats = calibrationStats,
                    onClick = onNavigateToCalibration
                )

                SettingsCategoryItem(
                    title = stringResource(R.string.settings_battery_optimization),
                    description = stringResource(R.string.settings_battery_optimization_desc),
                    icon = Icons.Default.BatteryChargingFull,
                    onClick = onNavigateToBattery
                )

                SettingsCategoryItem(
                    title = stringResource(R.string.settings_device_title),
                    description = stringResource(R.string.settings_device_desc),
                    icon = Icons.Default.Memory,
                    onClick = onNavigateToDevice
                )

                SettingsCategoryItem(
                    title = stringResource(R.string.settings_history_backup),
                    description = stringResource(R.string.settings_history_backup_desc),
                    icon = Icons.Default.Backup,
                    perfStats = historicalStats,
                    onClick = onNavigateToData
                )

                SettingsCategoryItem(
                    title = "Sincronización en la Nube",
                    description = "Configura tu cuenta de Google para sincronizar entre dispositivos.",
                    icon = Icons.Default.CloudSync,
                    onClick = onNavigateToCloud
                )

                SettingsCategoryItem(
                    title = stringResource(R.string.settings_perf_title),
                    description = stringResource(R.string.settings_perf_desc),
                    icon = Icons.Default.Analytics,
                    perfStats = dashboardEnterStats,
                    onClick = onNavigateToAdvanced
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
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
