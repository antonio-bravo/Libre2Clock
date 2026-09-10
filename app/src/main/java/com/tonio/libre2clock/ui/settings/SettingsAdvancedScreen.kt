package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.util.SectionPerfTelemetry
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAdvancedScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToEventLog: () -> Unit
) {
    val isApiDebugLoading by viewModel.isApiDebugLoading.collectAsStateWithLifecycle()
    val apiDebugOutput by viewModel.apiDebugOutput.collectAsStateWithLifecycle()
    val sectionPerfStats by viewModel.sectionPerfStats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_perf_title)) },
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
            // Sección de Rendimiento
            item {
                SettingsSection(title = stringResource(R.string.settings_perf_title)) {
                    Text(
                        text = stringResource(R.string.settings_perf_desc), 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::refreshSectionPerfStats, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_perf_refresh))
                        }
                        OutlinedButton(onClick = viewModel::recomputeAllCache, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_perf_recompute))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::resetSectionPerfStats, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_perf_reset))
                    }
                }
            }

            // OPTIMIZACIÓN: Uso de 'items' en lugar de 'forEach' para lazy loading real
            if (sectionPerfStats.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_perf_no_data), 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            } else {
                items(
                    items = sectionPerfStats,
                    key = { it.section } 
                ) { stat ->
                    PerfStatItem(stat = stat)
                }
            }

            // Sección de Diagnóstico API (Extraída para aislar su recomposición)
            item {
                ApiDiagnosticSection(
                    isApiDebugLoading = isApiDebugLoading,
                    apiDebugOutput = apiDebugOutput,
                    onRunDiagnostic = viewModel::runDirectApiDiagnostic,
                    onClearOutput = viewModel::clearApiDebugOutput
                )
            }
            
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                SettingsCategoryItem(
                    title = stringResource(R.string.event_log_title),
                    description = stringResource(R.string.settings_event_log_desc),
                    icon = Icons.AutoMirrored.Filled.List,
                    onClick = onNavigateToEventLog
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// --- Componentes Extraídos para Aislar Recomposiciones ---

@Composable
private fun PerfStatItem(stat: SectionPerfTelemetry.Snapshot) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = LocalizedSectionName(stat.section),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.settings_perf_hits_row, 
                    stat.calls, 
                    stat.hitRatePercent.toInt(), 
                    stat.cacheHits
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.settings_perf_times_row, 
                    stat.avgDurationMs, 
                    stat.maxDurationMs, 
                    stat.cacheMisses
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ApiDiagnosticSection(
    isApiDebugLoading: Boolean,
    apiDebugOutput: String?,
    onRunDiagnostic: () -> Unit,
    onClearOutput: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_api_diagnostic_title)) {
        Text(
            text = stringResource(R.string.settings_api_diagnostic_desc), 
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onRunDiagnostic, 
            enabled = !isApiDebugLoading, 
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isApiDebugLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.settings_api_diagnostic_run))
        }

        if (apiDebugOutput != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onClearOutput, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_api_diagnostic_clear))
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.small
            ) {
                SelectionContainer {
                    Text(
                        text = apiDebugOutput, 
                        style = MaterialTheme.typography.bodySmall, 
                        modifier = Modifier.padding(12.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace // Mejora la legibilidad de logs
                    )
                }
            }
        }
    }
}