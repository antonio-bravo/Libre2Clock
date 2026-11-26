package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
    val isUnifiedSmartCalibrationEnabled by viewModel.isUnifiedSmartCalibrationEnabled.collectAsStateWithLifecycle()
    val targetGlucoseLow by viewModel.targetGlucoseLow.collectAsStateWithLifecycle()
    val targetGlucoseHigh by viewModel.targetGlucoseHigh.collectAsStateWithLifecycle()
    val rangeInsights by viewModel.rangeOffsetInsights.collectAsStateWithLifecycle()
    val sectionPerfStats by viewModel.sectionPerfStats.collectAsStateWithLifecycle()

    val calibrationStats = remember(sectionPerfStats) {
        sectionPerfStats.find { it.section == "settings_range_insights_v1" }
    }

    val insightsMap = remember(rangeInsights) {
        rangeInsights.associateBy { "${it.min}_${it.max}" }
    }

    var editingRange by remember { mutableStateOf<GlucoseOffsetRange?>(null) }
    var showAdvancedOptions by remember { mutableStateOf(false) }

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
            // 1. Interruptor Maestro Unificado: Calibración Automática Inteligente
            item {
                UnifiedSmartCalibrationSection(
                    isEnabled = isUnifiedSmartCalibrationEnabled,
                    onToggle = viewModel::toggleUnifiedSmartCalibration
                )
            }

            // 2. Rango Saludable Objetivo
            item {
                HealthyTargetRangeSection(
                    targetLow = targetGlucoseLow,
                    targetHigh = targetGlucoseHigh,
                    onLowChange = viewModel::updateTargetGlucoseLow,
                    onHighChange = viewModel::updateTargetGlucoseHigh
                )
            }

            // 3. Estado de Calibración Activa por Rango
            item {
                Text(
                    text = stringResource(R.string.settings_range_based_offsets),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
                items(
                    items = ranges,
                    key = { range -> "${range.min}_${range.max}_${range.offset}" }
                ) { range ->
                    val insight = insightsMap["${range.min}_${range.max}"]
                    RangeItem(
                        range = range,
                        insight = insight,
                        autoRangeMode = autoRangeOffsetMode,
                        onDelete = { viewModel.removeRange(range) },
                        onEdit = { editingRange = range }
                    )
                }
            }

            // 4. Opciones Avanzadas (Desplegable)
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showAdvancedOptions = !showAdvancedOptions }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_advanced_calibration_options),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (showAdvancedOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
            }

            if (showAdvancedOptions) {
                item {
                    GlobalOffsetSection(
                        offset = offset,
                        autoAdjustEnabled = autoAdjustEnabled,
                        onOffsetChange = viewModel::updateOffset,
                        onAutoAdjustChange = viewModel::updateAutoAdjustEnabled
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { viewModel.addDefaultRange() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_add_range))
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
                }

                if (calibrationStats != null) {
                    item {
                        SectionPerformanceCard(
                            calibrationStats,
                            stringResource(R.string.settings_perf_algorithm_latency)
                        )
                    }
                }
            }

            item {
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

// --- Componentes Extraídos ---

@Composable
private fun UnifiedSmartCalibrationSection(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_unified_smart_calibration_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_unified_smart_calibration_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_auto_adjust_capillary_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun HealthyTargetRangeSection(
    targetLow: Int,
    targetHigh: Int,
    onLowChange: (Int) -> Unit,
    onHighChange: (Int) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_healthy_target_range)) {
        Text(
            text = stringResource(R.string.settings_healthy_target_range_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                DebouncedOffsetField(
                    initialValue = targetLow,
                    onValueChange = onLowChange,
                    label = stringResource(R.string.settings_target_low_label)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DebouncedOffsetField(
                    initialValue = targetHigh,
                    onValueChange = onHighChange,
                    label = stringResource(R.string.settings_target_high_label)
                )
            }
        }
    }
}