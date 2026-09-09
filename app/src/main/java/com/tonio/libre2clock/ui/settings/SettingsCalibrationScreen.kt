package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.GlucoseOffsetRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCalibrationScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val offset by viewModel.glucoseOffset.collectAsStateWithLifecycle()
    val ranges by viewModel.glucoseOffsetRanges.collectAsStateWithLifecycle()
    val autoAdjustEnabled by viewModel.autoAdjustEnabled.collectAsStateWithLifecycle()
    val autoRangeOffsetMode by viewModel.autoRangeOffsetMode.collectAsStateWithLifecycle()
    val rangeInsights by viewModel.rangeOffsetInsights.collectAsStateWithLifecycle()
    val sectionPerfStats by viewModel.sectionPerfStats.collectAsStateWithLifecycle()

    val calibrationStats = sectionPerfStats.find { it.section == "settings_range_insights_v1" }

    var showAddRangeDialog by remember { mutableStateOf(false) }
    var editingRange by remember { mutableStateOf<GlucoseOffsetRange?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_range_based_offsets)) },
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
                SettingsSection(title = stringResource(R.string.settings_global_manual_offset)) {
                    Text(text = stringResource(R.string.settings_global_offset_desc), style = MaterialTheme.typography.bodyMedium)
                    var offsetText by remember(offset) { mutableStateOf(offset.toString()) }
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = {
                            offsetText = it
                            it.toIntOrNull()?.let { viewModel.updateOffset(it) }
                        },
                        label = { Text(stringResource(R.string.settings_manual_offset_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = autoAdjustEnabled, onCheckedChange = viewModel::updateAutoAdjustEnabled)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_auto_adjust_capillary), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_auto_range_label)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.settings_auto_range_label), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = autoRangeOffsetMode != AutoRangeOffsetMode.OFF,
                            onCheckedChange = { enabled ->
                                viewModel.updateAutoRangeOffsetMode(if (enabled) AutoRangeOffsetMode.BY_RANGE else AutoRangeOffsetMode.OFF)
                            }
                        )
                    }
                    Text(
                        text = when (autoRangeOffsetMode) {
                            AutoRangeOffsetMode.OFF -> stringResource(R.string.settings_auto_range_off_desc)
                            AutoRangeOffsetMode.GLOBAL -> stringResource(R.string.settings_auto_range_global_desc)
                            AutoRangeOffsetMode.BY_RANGE -> stringResource(R.string.settings_auto_range_by_range_desc)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    if (rangeInsights.isNotEmpty()) {
                        val applicable = rangeInsights.count { it.sampleCount >= 2 }
                        Button(onClick = { viewModel.applySuggestedRangeOffsets() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_apply_intelligent_suggestions, applicable))
                        }
                    }
                }
                
                Text(
                    text = stringResource(R.string.settings_range_based_offsets), 
                    style = MaterialTheme.typography.labelLarge, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (ranges.isEmpty()) {
                item {
                    Text(stringResource(R.string.settings_no_ranges_defined), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                items(ranges) { range ->
                    val insight = rangeInsights.firstOrNull { it.min == range.min && it.max == range.max }
                    RangeItem(
                        range = range,
                        insight = insight,
                        onDelete = { viewModel.removeRange(range) },
                        onEdit = { editingRange = range }
                    )
                }
            }
            
            item {
                OutlinedButton(
                    onClick = { viewModel.recomputeAllCache() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_perf_recompute_long))
                }

                OutlinedButton(
                    onClick = { viewModel.addDefaultRange() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_add_range))
                }

                calibrationStats?.let { stats ->
                    SectionPerformanceCard(stats, stringResource(R.string.settings_perf_algorithm_latency))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAddRangeDialog) {
        RangeDialog(onDismiss = { showAddRangeDialog = false }, onConfirm = { viewModel.addRange(it); showAddRangeDialog = false })
    }
    editingRange?.let { r ->
        RangeDialog(initialRange = r, onDismiss = { editingRange = null }, onConfirm = { viewModel.updateRange(r, it); editingRange = null })
    }
}
