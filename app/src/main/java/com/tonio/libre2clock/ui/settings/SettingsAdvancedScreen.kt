package com.tonio.libre2clock.ui.settings

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.util.SectionPerfTelemetry
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Delete

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
    
    // Nuevos estados para Cloud Sync
    val isCloudDebugLoading by viewModel.isCloudSyncDebugLoading.collectAsStateWithLifecycle()
    val cloudDebugOutput by viewModel.cloudSyncDebugOutput.collectAsStateWithLifecycle()

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Sección de Rendimiento
            item(key = "perf_section") {
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

            // Items de estadísticas de rendimiento (Lazy Loading real)
            if (sectionPerfStats.isEmpty()) {
                item(key = "perf_empty") {
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

            // Sección de Diagnóstico API
            item(key = "api_diagnostic") {
                ApiDiagnosticSection(
                    isApiDebugLoading = isApiDebugLoading,
                    apiDebugOutput = apiDebugOutput,
                    onRunDiagnostic = viewModel::runDirectApiDiagnostic,
                    onClearOutput = viewModel::clearApiDebugOutput
                )
            }
            
            // NUEVA Sección de Diagnóstico Cloud Sync
            item(key = "cloud_diagnostic") {
                CloudDiagnosticSection(
                    isCloudDebugLoading = isCloudDebugLoading,
                    cloudDebugOutput = cloudDebugOutput,
                    onRunDiagnostic = viewModel::runCloudSyncDiagnostic,
                    onClearOutput = viewModel::clearCloudSyncDebugOutput
                )
            }
            
            // Enlace a Event Log
            item(key = "event_log_link") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                SettingsCategoryItem(
                    title = stringResource(R.string.event_log_title),
                    description = stringResource(R.string.settings_event_log_desc),
                    icon = Icons.AutoMirrored.Filled.List,
                    onClick = onNavigateToEventLog
                )
            }
        }
    }
}

// --- Componentes Extraídos ---

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
                    stat.hitRatePercent.roundToInt(), // FIX: roundToInt() en vez de toInt() para evitar truncamiento
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
            Spacer(modifier = Modifier.height(12.dp))
            // OPTIMIZACIÓN: Reutilizamos el componente DebugLogCard
            DebugLogCard(
                title = stringResource(R.string.settings_api_diagnostic_title),
                logText = apiDebugOutput,
                onClear = onClearOutput
            )
        }
    }
}

@Composable
private fun CloudDiagnosticSection(
    isCloudDebugLoading: Boolean,
    cloudDebugOutput: String?,
    onRunDiagnostic: () -> Unit,
    onClearOutput: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_cloud_diagnostic_title)) {
        Text(
            text = stringResource(R.string.settings_cloud_diagnostic_desc), 
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onRunDiagnostic, 
            enabled = !isCloudDebugLoading, 
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isCloudDebugLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.settings_cloud_diagnostic_run))
        }

        if (cloudDebugOutput != null) {
            Spacer(modifier = Modifier.height(12.dp))
            // Reutilizamos el componente DebugLogCard
            DebugLogCard(
                title = stringResource(R.string.settings_cloud_diagnostic_title),
                logText = cloudDebugOutput,
                onClear = onClearOutput
            )
        }
    }
}

/**
 * Componente reutilizable para mostrar logs de diagnóstico.
 * Incluye: fuente monoespaciada, scroll interno, selección nativa y botón de copiar.
 */
@Composable
private fun DebugLogCard(
    title: String,
    logText: String,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    // 1. Obtenemos el scope de la corrutina
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cabecera con título y botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            val clipData = ClipData.newPlainText(title, logText)
                            // 2. Ejecutamos la función suspend dentro de una corrutina
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(clipData))
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_log_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.settings_copy_log),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.settings_api_diagnostic_clear),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Contenedor del log con scroll interno y selección de texto nativa
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp) // Altura máxima para no saturar la pantalla
            ) {
                SelectionContainer {
                    Text(
                        text = logText.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}