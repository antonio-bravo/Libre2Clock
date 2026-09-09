package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
                .padding(horizontal = 16.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_perf_title)) {
                    Text(text = stringResource(R.string.settings_perf_desc), style = MaterialTheme.typography.bodySmall)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::refreshSectionPerfStats, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_perf_refresh))
                        }
                        OutlinedButton(onClick = viewModel::recomputeAllCache, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_perf_recompute))
                        }
                    }
                    OutlinedButton(onClick = viewModel::resetSectionPerfStats, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_perf_reset))
                    }

                    if (sectionPerfStats.isEmpty()) {
                        Text(text = stringResource(R.string.settings_perf_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        sectionPerfStats.forEach { stat ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = getLocalizedSectionName(stat.section), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(text = stringResource(R.string.settings_perf_hits_row, stat.calls, stat.hitRatePercent.roundToInt(), stat.cacheHits), style = MaterialTheme.typography.bodySmall)
                                    Text(text = stringResource(R.string.settings_perf_times_row, stat.avgDurationMs, stat.maxDurationMs, stat.cacheMisses), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_api_diagnostic_title)) {
                    Text(text = stringResource(R.string.settings_api_diagnostic_desc), style = MaterialTheme.typography.bodyMedium)
                    
                    Button(onClick = viewModel::runDirectApiDiagnostic, enabled = !isApiDebugLoading, modifier = Modifier.fillMaxWidth()) {
                        if (isApiDebugLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.settings_api_diagnostic_run))
                    }

                    if (apiDebugOutput != null) {
                        OutlinedButton(onClick = viewModel::clearApiDebugOutput, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_api_diagnostic_clear))
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                            SelectionContainer {
                                Text(text = apiDebugOutput!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

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
