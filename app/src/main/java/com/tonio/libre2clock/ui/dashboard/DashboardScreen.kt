package com.tonio.libre2clock.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.SensorStatus
import com.tonio.libre2clock.data.repository.GlucoseProcessor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit
) {
    val currentGlucose by viewModel.currentGlucose.collectAsStateWithLifecycle()
    val sensorStatus by viewModel.sensorStatus.collectAsStateWithLifecycle()
    val historicalData by viewModel.historicalData.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Libre2Clock") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlucoseCard(currentGlucose)
            Spacer(modifier = Modifier.height(16.dp))
            SensorHealthCard(sensorStatus)
            Spacer(modifier = Modifier.height(16.dp))
            InteractiveTrendGraph(
                measurements = historicalData,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SensorHealthCard(status: SensorStatus?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sensor Health",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (status != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${status.daysRemaining} days remaining",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = status.expiryDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "SN: ${status.serialNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(
                    text = "No sensor data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun GlucoseCard(measurement: GlucoseMeasurement?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (measurement != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (displayValue.length > 6) 48.sp else 64.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "mg/dL",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TrendIcon(measurement.trendArrow)
                    }
                }
                Text(
                    text = "Last sync: ${measurement.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            } else {
                CircularProgressIndicator()
                Text("Fetching data...")
            }
        }
    }
}

@Composable
fun TrendIcon(trend: Int?) {
    val symbol = GlucoseProcessor.getTrendArrowSymbol(trend)
    val color = when (trend) {
        1, 2 -> Color.Red
        4, 5 -> Color.Green
        else -> Color.Gray
    }
    Text(
        text = symbol,
        color = color,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold
    )
}
