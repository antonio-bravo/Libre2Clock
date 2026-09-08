package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDeviceScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val sensorDurationDays by viewModel.sensorDurationDays.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_device_title)) },
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
                SettingsSection(title = stringResource(R.string.settings_sensor_duration_label)) {
                    var sensorDurationText by remember(sensorDurationDays) { mutableStateOf(sensorDurationDays.toString()) }
                    OutlinedTextField(
                        value = sensorDurationText,
                        onValueChange = {
                            sensorDurationText = it
                            it.toIntOrNull()?.let { viewModel.updateSensorDurationDays(it) }
                        },
                        label = { Text(stringResource(R.string.settings_sensor_duration_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.demo_mode)) {
                    Text(text = stringResource(R.string.settings_demo_mode_desc), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.settings_enable_demo_mode), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isDemoMode, onCheckedChange = viewModel::updateDemoMode)
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
