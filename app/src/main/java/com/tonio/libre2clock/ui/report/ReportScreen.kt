package com.tonio.libre2clock.ui.report

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.ui.dashboard.InteractiveTrendGraph
import com.tonio.libre2clock.util.PdfReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// OPTIMIZACIÓN: Colores como constantes para evitar asignaciones en cada recomposición/animación
private val ColorTir = Color(0xFF4CAF50)
private val ColorTbrLow = Color.Red
private val ColorTbrVLow = Color(0xFF8B0000)
private val ColorTarHigh = Color(0xFFFFA500)
private val ColorTarVHigh = Color(0xFFFF4500)
private val ColorBgBar = Color.LightGray.copy(alpha = 0.2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val startDate by viewModel.startDate.collectAsStateWithLifecycle()
    val endDate by viewModel.endDate.collectAsStateWithLifecycle()
    val useOffset by viewModel.useOffsetValues.collectAsStateWithLifecycle()
    val metrics by viewModel.reportMetrics.collectAsStateWithLifecycle()
    val agpData by viewModel.agpData.collectAsStateWithLifecycle()
    val dailySummaries by viewModel.dailySummaries.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    
    var selectedLayout by remember { mutableStateOf(ReportLayout.FULL) }
    var showDatePicker by remember { mutableStateOf<DatePickerType?>(null) }

    val reportFailedMsg = stringResource(R.string.report_failed_generate)
    val shareReportTitle = stringResource(R.string.report_share_chooser)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !isGenerating && metrics != null,
                        onClick = {
                            scope.launch {
                                viewModel.setGenerating(true)
                                val m = metrics ?: return@launch
                                
                                val file = withContext(Dispatchers.IO) {
                                    PdfReportGenerator.generateFullReport(
                                        context = context,
                                        metrics = m,
                                        agpData = agpData,
                                        dailySummaries = dailySummaries,
                                        startDate = startDate,
                                        endDate = endDate,
                                        useOffset = useOffset,
                                        layout = selectedLayout
                                    )
                                }
                                
                                viewModel.setGenerating(false)
                                if (file != null) sharePdf(context, file, shareReportTitle)
                                else Toast.makeText(context, reportFailedMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.settings_export))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PresetsSelector(onSelect = viewModel::setRange)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DateDisplay(
                            label = stringResource(R.string.report_start_date),
                            date = startDate,
                            onClick = { showDatePicker = DatePickerType.START },
                            modifier = Modifier.weight(1f)
                        )
                        DateDisplay(
                            label = stringResource(R.string.report_end_date),
                            date = endDate,
                            onClick = { showDatePicker = DatePickerType.END },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (useOffset) stringResource(R.string.report_using_calibrated) else stringResource(R.string.report_using_raw))
                    Switch(checked = useOffset, onCheckedChange = viewModel::setUseOffsetValues)
                }

                LayoutSelector(selected = selectedLayout, onSelect = { selectedLayout = it })

                metrics?.let { m ->
                    GlucoseStatsSection(m)
                    InsulinStatsSection(m)
                }

                Text(text = stringResource(R.string.report_preview_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                // OPTIMIZACIÓN CRÍTICA: Evitar flatMap en toda la lista. 
                // Solo tomamos 200 elementos sin crear listas intermedias gigantes en memoria.
                val previewData = remember(dailySummaries) {
                    val result = mutableListOf<com.tonio.libre2clock.data.model.GlucoseMeasurement>()
                    var count = 0
                    for (summary in dailySummaries) {
                        for (g in summary.glucose) {
                            result.add(g)
                            count++
                            if (count >= 200) break
                        }
                        if (count >= 200) break
                    }
                    result
                }
                
                InteractiveTrendGraph(
                    measurements = previewData,
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            if (isGenerating) {
                GenerationLoadingDialog()
            }

            // OPTIMIZACIÓN: Diálogo de fecha unificado y limpio
            showDatePicker?.let { type ->
                val initialDate = if (type == DatePickerType.START) startDate else endDate
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
                
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = null },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                                if (type == DatePickerType.START) {
                                    viewModel.setCustomRange(selectedDate, endDate)
                                } else {
                                    viewModel.setCustomRange(startDate, selectedDate)
                                }
                            }
                            showDatePicker = null
                        }) { Text(stringResource(android.R.string.ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = null }) { 
                            Text(stringResource(android.R.string.cancel)) 
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
        }
    }
}

// Enum auxiliar para manejar qué fecha se está editando
private enum class DatePickerType { START, END }

@Composable
private fun GenerationLoadingDialog() {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.report_generating), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.report_generating_wait), 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun PresetsSelector(onSelect: (ReportRange) -> Unit) {
    Column {
        Text(text = stringResource(R.string.report_presets_label), style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportRange.entries.forEach { range ->
                val labelRes = when (range) {
                    ReportRange.ONE_DAY -> R.string.report_range_1d
                    ReportRange.SEVEN_DAYS -> R.string.report_range_7d
                    ReportRange.FIFTEEN_DAYS -> R.string.report_range_15d
                    ReportRange.THIRTY_DAYS -> R.string.report_range_30d
                    ReportRange.NINETY_DAYS -> R.string.report_range_90d
                }
                OutlinedButton(
                    onClick = { onSelect(range) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(stringResource(labelRes), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DateDisplay(label: String, date: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable { onClick() }) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            text = date.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LayoutSelector(selected: ReportLayout, onSelect: (ReportLayout) -> Unit) {
    Column {
        Text(text = stringResource(R.string.report_type_label), style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LayoutButton(ReportLayout.SNAPSHOT, stringResource(R.string.report_layout_snapshot), selected == ReportLayout.SNAPSHOT, onSelect, Modifier.weight(1f))
            LayoutButton(ReportLayout.DAILY_LOG, stringResource(R.string.report_layout_daily), selected == ReportLayout.DAILY_LOG, onSelect, Modifier.weight(1f))
            LayoutButton(ReportLayout.FULL, stringResource(R.string.report_layout_full), selected == ReportLayout.FULL, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LayoutButton(layout: ReportLayout, label: String, isSelected: Boolean, onSelect: (ReportLayout) -> Unit, modifier: Modifier) {
    FilterChip(
        selected = isSelected,
        onClick = { onSelect(layout) },
        label = { Text(label, maxLines = 1) },
        modifier = modifier
    )
}

@Composable
private fun GlucoseStatsSection(metrics: ReportMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.report_glucose_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            MetricRow(stringResource(R.string.report_avg_glucose), "%.0f mg/dL".format(metrics.avgGlucose))
            MetricRow(stringResource(R.string.report_gmi), "%.1f %%".format(metrics.gmi))
            MetricRow(stringResource(R.string.report_variability_cv), "%.1f %%".format(metrics.cv))
            
            Spacer(modifier = Modifier.height(16.dp))
            TirBarAdvanced(metrics)
        }
    }
}

@Composable
private fun InsulinStatsSection(metrics: ReportMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.report_insulin_stats), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            MetricRow(stringResource(R.string.report_avg_tdi), "%.1f U".format(metrics.avgTdi))
            MetricRow(stringResource(R.string.report_basal_bolus), "%.0f%% / %.0f%%".format(metrics.basalPercentage, metrics.bolusPercentage))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TirBarAdvanced(m: ReportMetrics) {
    val tirAnim by animateFloatAsState(targetValue = m.tir.toFloat(), animationSpec = tween(1000), label = "tir")
    val tbrLowAnim by animateFloatAsState(targetValue = m.tbrLow.toFloat(), animationSpec = tween(1000), label = "tbr_low")
    val tbrVLowAnim by animateFloatAsState(targetValue = m.tbrVLow.toFloat(), animationSpec = tween(1000), label = "tbr_vlow")
    val tarHighAnim by animateFloatAsState(targetValue = m.tarHigh.toFloat(), animationSpec = tween(1000), label = "tar_high")
    val tarVHighAnim by animateFloatAsState(targetValue = m.tarVHigh.toFloat(), animationSpec = tween(1000), label = "tar_vhigh")

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(R.string.report_time_in_range_label), style = MaterialTheme.typography.labelSmall)
            Text(text = "%.0f%%".format(m.tir), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        
        Row(modifier = Modifier.fillMaxWidth().height(12.dp).background(ColorBgBar)) {
            if (tbrVLowAnim > 0.1f) Box(Modifier.weight(tbrVLowAnim).fillMaxHeight().background(ColorTbrVLow))
            if (tbrLowAnim > 0.1f) Box(Modifier.weight(tbrLowAnim).fillMaxHeight().background(ColorTbrLow))
            if (tirAnim > 0.1f) Box(Modifier.weight(tirAnim).fillMaxHeight().background(ColorTir))
            if (tarHighAnim > 0.1f) Box(Modifier.weight(tarHighAnim).fillMaxHeight().background(ColorTarHigh))
            if (tarVHighAnim > 0.1f) Box(Modifier.weight(tarVHighAnim).fillMaxHeight().background(ColorTarVHigh))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.report_low_percent, m.tbrLow + m.tbrVLow), 
                style = MaterialTheme.typography.labelSmall, 
                color = ColorTbrLow
            )
            Text(
                text = stringResource(R.string.report_high_percent, m.tarHigh + m.tarVHigh), 
                style = MaterialTheme.typography.labelSmall, 
                color = ColorTarHigh
            )
        }
    }
}

private fun sharePdf(context: android.content.Context, file: File, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}