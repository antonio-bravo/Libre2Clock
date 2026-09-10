package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_battery_optimization)) {
                    Text(
                        text = stringResource(R.string.settings_battery_optimization_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // OPTIMIZACIÓN: Componente extraído para aislar recomposición
                    BatteryThresholdSlider(
                        value = batteryLowThreshold,
                        onValueChange = { newValue ->
                            // Validación: low debe ser > critical
                            if (newValue > batteryCriticalThreshold) {
                                viewModel.updateBatteryLowThreshold(newValue)
                            }
                        },
                        label = stringResource(R.string.settings_battery_low_threshold, batteryLowThreshold),
                        description = stringResource(R.string.settings_battery_low_desc),
                        valueRange = (batteryCriticalThreshold + 1)..50,
                        icon = Icons.Default.Warning,
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // OPTIMIZACIÓN: Componente extraído para aislar recomposición
                    BatteryThresholdSlider(
                        value = batteryCriticalThreshold,
                        onValueChange = { newValue ->
                            // Validación: critical debe ser < low
                            if (newValue < batteryLowThreshold) {
                                viewModel.updateBatteryCriticalThreshold(newValue)
                            }
                        },
                        label = stringResource(R.string.settings_battery_critical_threshold, batteryCriticalThreshold),
                        description = stringResource(R.string.settings_battery_critical_desc),
                        valueRange = 1..(batteryLowThreshold - 1),
                        icon = Icons.Default.BatteryAlert,
                        iconTint = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // OPTIMIZACIÓN: Componente extraído para aislar recomposición
                    SlowChargeProtectionSwitch(
                        checked = disableFastOnSlowCharge,
                        onCheckedChange = viewModel::updateDisableFastRefreshOnSlowCharge
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// --- Componentes Extraídos para Aislar Recomposiciones ---

@Composable
private fun BatteryThresholdSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    description: String,
    valueRange: IntRange,
    icon: ImageVector,
    iconTint: Color
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SlowChargeProtectionSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_slow_charge_protection),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_slow_charge_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}