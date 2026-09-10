package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
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
import com.tonio.libre2clock.data.model.RangeOffsetInsight
import kotlinx.coroutines.delay

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

    // OPTIMIZACIÓN: Búsqueda O(1) en lugar de .find() O(N) en cada recomposición
    val calibrationStats = remember(sectionPerfStats) {
        sectionPerfStats.find { it.section == "settings_range_insights_v1" }
    }

    // OPTIMIZACIÓN: Mapa para búsqueda O(1) de insights por rango
    val insightsMap = remember(rangeInsights) {
        rangeInsights.associateBy { "${it.min}_${it.max}" }
    }

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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // OPTIMIZACIÓN: Cada sección es un 'item' independiente para aislar recomposiciones
            item {
                GlobalOffsetSection(
                    offset = offset,
                    autoAdjustEnabled = autoAdjustEnabled,
                    onOffsetChange = viewModel::updateOffset,
                    onAutoAdjustChange = viewModel::updateAutoAdjustEnabled
                )
            }

            item {
                AutoRangeSection(
                    mode = autoRangeOffsetMode,
                    rangeInsights = rangeInsights,
                    onModeChange = viewModel::updateAutoRangeOffsetMode,
                    onApplySuggestions = viewModel::applySuggestedRangeOffsets
                )
            }

            item {
                Text(
                    text = stringResource(R.string.settings_range_based_offsets),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (ranges.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.settings_no_ranges_defined),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // OPTIMIZACIÓN: Uso de 'items' con 'key' para lazy loading real
                items(
                    items = ranges,
                    key = { range -> "${range.min}_${range.max}_${range.offset}" }
                ) { range ->
                    // Búsqueda O(1) usando el mapa pre-construido
                    val insight = insightsMap["${range.min}_${range.max}"]
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_perf_recompute_long))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.addDefaultRange() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_add_range))
                }

                if (calibrationStats != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionPerformanceCard(
                        calibrationStats,
                        stringResource(R.string.settings_perf_algorithm_latency)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    editingRange?.let { range ->
        RangeDialog(
            initialRange = range,
            onDismiss = { editingRange = null },
            onConfirm = {
                viewModel.updateRange(range, it)
                editingRange = null
            }
        )
    }
}

// --- Componentes Extraídos para Aislar Recomposiciones ---

@Composable
private fun GlobalOffsetSection(
    offset: Int,
    autoAdjustEnabled: Boolean,
    onOffsetChange: (Int) -> Unit,
    onAutoAdjustChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_global_manual_offset)) {
        Text(
            text = stringResource(R.string.settings_global_offset_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        // OPTIMIZACIÓN: Debounce para evitar escrituras excesivas en DataStore
        DebouncedOffsetField(
            initialValue = offset,
            onValueChange = onOffsetChange,
            label = stringResource(R.string.settings_manual_offset_label)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_auto_adjust_capillary),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = autoAdjustEnabled, onCheckedChange = onAutoAdjustChange)
        }
    }
}

@Composable
private fun DebouncedOffsetField(
    initialValue: Int,
    onValueChange: (Int) -> Unit,
    label: String
) {
    var textValue by remember { mutableStateOf(initialValue.toString()) }

    // Sincronizar si el valor cambia externamente
    LaunchedEffect(initialValue) {
        if (textValue != initialValue.toString()) {
            textValue = initialValue.toString()
        }
    }

    // OPTIMIZACIÓN: Debounce de 600ms antes de guardar
    LaunchedEffect(textValue) {
        delay(600)
        textValue.toIntOrNull()?.let { onValueChange(it) }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            // Permitir solo dígitos y signo negativo al inicio
            val filtered = newText.filter { it.isDigit() || (it == '-' && newText.indexOf('-') == 0) }
            textValue = filtered
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun AutoRangeSection(
    mode: AutoRangeOffsetMode,
    rangeInsights: List<RangeOffsetInsight>,
    onModeChange: (AutoRangeOffsetMode) -> Unit,
    onApplySuggestions: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_auto_range_label)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_auto_range_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = mode != AutoRangeOffsetMode.OFF,
                onCheckedChange = { enabled ->
                    onModeChange(if (enabled) AutoRangeOffsetMode.BY_RANGE else AutoRangeOffsetMode.OFF)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (mode) {
                AutoRangeOffsetMode.OFF -> stringResource(R.string.settings_auto_range_off_desc)
                AutoRangeOffsetMode.GLOBAL -> stringResource(R.string.settings_auto_range_global_desc)
                AutoRangeOffsetMode.BY_RANGE -> stringResource(R.string.settings_auto_range_by_range_desc)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (rangeInsights.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val applicable = rangeInsights.count { it.sampleCount >= 2 }
            Button(
                onClick = onApplySuggestions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_apply_intelligent_suggestions, applicable))
            }
        }
    }
}