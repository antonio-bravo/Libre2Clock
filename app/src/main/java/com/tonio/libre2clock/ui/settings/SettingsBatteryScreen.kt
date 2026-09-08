package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun SettingsBatteryScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val batteryLowThreshold by viewModel.batteryLowThreshold.collectAsStateWithLifecycle()
    val batteryCriticalThreshold by viewModel.batteryCriticalThreshold.collectAsStateWithLifecycle()
    val disableFastOnSlowCharge by viewModel.disableFastRefreshOnSlowCharge.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_battery_optimization)) },
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
                SettingsSection(title = stringResource(R.string.settings_battery_optimization)) {
                    Text(text = stringResource(R.string.settings_battery_optimization_desc), style = MaterialTheme.typography.bodyMedium)
                    
                    Column {
                        Text(text = stringResource(R.string.settings_battery_low_threshold, batteryLowThreshold), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = batteryLowThreshold.toFloat(),
                            onValueChange = { viewModel.updateBatteryLowThreshold(it.toInt()) },
                            valueRange = 5f..50f,
                            steps = 8
                        )
                        Text(text = stringResource(R.string.settings_battery_low_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column {
                        Text(text = stringResource(R.string.settings_battery_critical_threshold, batteryCriticalThreshold), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = batteryCriticalThreshold.toFloat(),
                            onValueChange = { viewModel.updateBatteryCriticalThreshold(it.toInt()) },
                            valueRange = 1f..15f,
                            steps = 13
                        )
                        Text(text = stringResource(R.string.settings_battery_critical_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.settings_slow_charge_protection), style = MaterialTheme.typography.bodyMedium)
                            Text(text = stringResource(R.string.settings_slow_charge_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = disableFastOnSlowCharge, onCheckedChange = viewModel::updateDisableFastRefreshOnSlowCharge)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
