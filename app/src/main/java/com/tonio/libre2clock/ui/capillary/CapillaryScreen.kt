package com.tonio.libre2clock.ui.capillary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.ui.components.DateHourMinuteInput
import com.tonio.libre2clock.ui.settings.SettingsViewModel
import com.tonio.libre2clock.util.SectionPerfTelemetry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapillaryScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val screenEnterAtMs = remember { System.currentTimeMillis() }
    var enterTimingRecorded by remember { mutableStateOf(false) }

    val capillaryReadings by viewModel.capillaryReadings.collectAsStateWithLifecycle()
    val currentGlucose by viewModel.currentGlucose.collectAsStateWithLifecycle()

    LaunchedEffect(capillaryReadings, currentGlucose) {
        if (!enterTimingRecorded) {
            enterTimingRecorded = true
            SectionPerfTelemetry.record(
                section = "capillary_screen_enter",
                durationMs = System.currentTimeMillis() - screenEnterAtMs,
                cacheHit = true
            )
        }
    }

    var showCapillaryDialog by remember { mutableStateOf(false) }
    var capillaryValueText by remember { mutableStateOf("") }
    var capillaryDate by remember { mutableStateOf(LocalDate.now()) }
    var capillaryHour by remember { mutableStateOf("") }
    var capillaryMinute by remember { mutableStateOf("") }

    val avgDeviation = remember(capillaryReadings) {
        val duration = measureTimeMillis {
            var totalDeviation = 0.0
            var validCount = 0
            
            for (reading in capillaryReadings) {
                val sensor = reading.sensorValue ?: continue
                if (sensor == 0) continue

                totalDeviation += abs(reading.value - sensor).toDouble() / sensor * 100.0
                validCount++
            }
            
            if (validCount > 0) totalDeviation / validCount else null
        }
        SectionPerfTelemetry.record(section = "capillary_screen_stats", durationMs = duration, cacheHit = true)
        if (capillaryReadings.isNotEmpty()) {
            var totalDeviation = 0.0
            var validCount = 0
            for (reading in capillaryReadings) {
                val sensor = reading.sensorValue ?: continue
                if (sensor == 0) continue
                totalDeviation += abs(reading.value - sensor).toDouble() / sensor * 100.0
                validCount++
            }
            if (validCount > 0) totalDeviation / validCount else null
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capillary_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val now = LocalTime.now()
                capillaryDate = LocalDate.now()
                capillaryHour = now.hour.toString().padStart(2, '0')
                capillaryMinute = now.minute.toString().padStart(2, '0')
                showCapillaryDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_capillary_reading))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (avgDeviation != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.capillary_avg_deviation, avgDeviation),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (capillaryReadings.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.capillary_no_readings),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // OPTIMIZACIÓN: Usar key para mejorar el rendimiento de LazyColumn
                items(
                    items = capillaryReadings,
                    key = { it.timestamp }
                ) { reading ->
                    CapillaryItem(
                        reading = reading,
                        onDelete = { viewModel.removeCapillaryReading(reading) }
                    )
                }
            }
        }
    }

    if (showCapillaryDialog) {
        AlertDialog(
            onDismissRequest = { showCapillaryDialog = false },
            title = { Text(stringResource(R.string.save_capillary_reading)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = capillaryValueText,
                        onValueChange = { capillaryValueText = it },
                        label = { Text(stringResource(R.string.capillary_value_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DateHourMinuteInput(
                        date = capillaryDate,
                        onDateChange = { capillaryDate = it },
                        hour = capillaryHour,
                        onHourChange = { capillaryHour = it },
                        minute = capillaryMinute,
                        onMinuteChange = { capillaryMinute = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val sensorValue = currentGlucose?.value
                    OutlinedTextField(
                        value = sensorValue?.toString() ?: stringResource(R.string.no_sensor_data),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.capillary_current_sensor)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = capillaryValueText.toIntOrNull() ?: return@TextButton
                    val sensorValue = currentGlucose?.value
                    val delta = sensorValue?.let { value - it }
                    val hour = capillaryHour.toIntOrNull() ?: 0
                    val minute = capillaryMinute.toIntOrNull() ?: 0
                    val timestamp = "${capillaryDate.format(DateTimeFormatter.ISO_LOCAL_DATE)} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                    
                    viewModel.addCapillaryReading(
                        CapillaryMeasurement(
                            value = value,
                            timestamp = timestamp,
                            sensorValue = sensorValue,
                            delta = delta
                        )
                    )
                    capillaryValueText = ""
                    val resetNow = LocalTime.now()
                    capillaryDate = LocalDate.now()
                    capillaryHour = resetNow.hour.toString().padStart(2, '0')
                    capillaryMinute = resetNow.minute.toString().padStart(2, '0')
                    showCapillaryDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCapillaryDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CapillaryItem(
    reading: CapillaryMeasurement,
    onDelete: () -> Unit
) {
    // OPTIMIZACIÓN: Pre-calcular la desviación una sola vez por item
    val deviation = remember(reading) {
        reading.sensorValue?.let { sensor ->
            if (sensor != 0) {
                abs(reading.value - sensor).toDouble() / sensor * 100.0
            } else null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${reading.value} mg/dL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = reading.timestamp,
                    style = MaterialTheme.typography.bodySmall
                )
                
                reading.sensorValue?.let { sensor ->
                    if (sensor != 0) {
                        Text(
                            text = stringResource(R.string.capillary_sensor_value, sensor),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (deviation != null) {
                            Text(
                                text = stringResource(R.string.capillary_deviation, deviation),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                reading.delta?.let {
                    val deltaText = if (it >= 0) "+$it" else "$it"
                    Text(
                        text = stringResource(R.string.capillary_delta, deltaText),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}