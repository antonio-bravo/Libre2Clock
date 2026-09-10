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
import kotlinx.coroutines.delay

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
        // OPTIMIZACIÓN: verticalArrangement y contentPadding eliminan la necesidad de Spacers manuales
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // OPTIMIZACIÓN: Cada sección es un 'item' independiente con 'key' para aislar recomposiciones
            item(key = "sensor_duration") {
                SensorDurationSection(
                    currentDays = sensorDurationDays,
                    onDaysChange = viewModel::updateSensorDurationDays
                )
            }

            item(key = "demo_mode") {
                DemoModeSection(
                    isDemoMode = isDemoMode,
                    onDemoModeChange = viewModel::updateDemoMode
                )
            }
        }
    }
}

// --- Componentes Extraídos para Aislar Recomposiciones ---

@Composable
private fun SensorDurationSection(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_sensor_duration_label)) {
        DebouncedDurationField(
            initialValue = currentDays,
            onValueChange = onDaysChange
        )
    }
}

@Composable
private fun DebouncedDurationField(
    initialValue: Int,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue.toString()) }
    
    // Sincronizar si el valor cambia externamente (ej. reset de fábrica)
    LaunchedEffect(initialValue) {
        if (textValue != initialValue.toString()) {
            textValue = initialValue.toString()
        }
    }

    // OPTIMIZACIÓN: Debounce de 600ms antes de guardar en DataStore/DB
    LaunchedEffect(textValue) {
        delay(600)
        textValue.toIntOrNull()?.let { days ->
            if (days in 1..30) {
                onValueChange(days)
            }
        }
    }

    val isValid = textValue.toIntOrNull()?.let { it in 1..30 } ?: true

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            // Permitir solo dígitos
            textValue = newText.filter { it.isDigit() }
        },
        label = { Text(stringResource(R.string.settings_sensor_duration_label)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = textValue.isNotEmpty() && !isValid,
        supportingText = {
            if (textValue.isNotEmpty() && !isValid) {
                Text(
                    text = stringResource(R.string.settings_sensor_duration_error), // Asegúrate de tener este string en strings.xml
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
private fun DemoModeSection(
    isDemoMode: Boolean,
    onDemoModeChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(R.string.demo_mode)) {
        Text(
            text = stringResource(R.string.settings_demo_mode_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_enable_demo_mode),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = isDemoMode,
                onCheckedChange = onDemoModeChange
            )
        }
    }
}