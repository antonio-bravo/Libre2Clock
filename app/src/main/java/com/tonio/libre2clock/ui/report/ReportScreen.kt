package com.tonio.libre2clock.ui.report

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val range by viewModel.selectedRange.collectAsStateWithLifecycle()
    val useOffset by viewModel.useOffsetValues.collectAsStateWithLifecycle()
    val metrics by viewModel.reportMetrics.collectAsStateWithLifecycle()
    val agpData by viewModel.agpData.collectAsStateWithLifecycle()
    val dailySummaries by viewModel.dailySummaries.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    
    var selectedLayout by remember { mutableStateOf(ReportLayout.FULL) }

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
                                        range = range,
                                        useOffset = useOffset,
                                        layout = selectedLayout
                                    )
                                }
                                
                                viewModel.setGenerating(false)
                                if (file != null) sharePdf(context, file)
                                else Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
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
                RangeSelector(selected = range, onSelect = viewModel::setRange)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (useOffset) "Using Calibrated (Offset)" else "Using Raw (Real)")
                    Switch(checked = useOffset, onCheckedChange = { viewModel.setUseOffsetValues(it) })
                }

                LayoutSelector(selected = selectedLayout, onSelect = { selectedLayout = it })

                metrics?.let { m ->
                    GlucoseStatsSection(m)
                    InsulinStatsSection(m)
                }

                Text(text = "Preview (Daily Trends)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val previewData = remember(dailySummaries) { dailySummaries.flatMap { it.glucose }.take(200) }
                InteractiveTrendGraph(
                    measurements = previewData,
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            if (isGenerating) {
                GenerationLoadingDialog()
            }
        }
    }
}

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
                Text("Generating Report...", style = MaterialTheme.typography.bodyMedium)
                Text("Please wait, this may take a moment for long ranges.", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun RangeSelector(selected: ReportRange, onSelect: (ReportRange) -> Unit) {
    val ranges = ReportRange.entries
    ScrollableTabRow(
        selectedTabIndex = ranges.indexOf(selected),
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {}
    ) {
        ranges.forEach { range ->
            val label = when (range) {
                ReportRange.ONE_DAY -> stringResource(R.string.report_range_1d)
                ReportRange.SEVEN_DAYS -> stringResource(R.string.report_range_7d)
                ReportRange.FOURTEEN_DAYS -> stringResource(R.string.report_range_14d)
                ReportRange.THIRTY_DAYS -> stringResource(R.string.report_range_30d)
                ReportRange.NINETY_DAYS -> stringResource(R.string.report_range_90d)
            }
            Tab(
                selected = selected == range,
                onClick = { onSelect(range) },
                text = { Text(label) }
            )
        }
    }
}

@Composable
fun LayoutSelector(selected: ReportLayout, onSelect: (ReportLayout) -> Unit) {
    Column {
        Text(text = "Report Type", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LayoutButton(ReportLayout.SNAPSHOT, stringResource(R.string.report_layout_snapshot), selected == ReportLayout.SNAPSHOT, onSelect, Modifier.weight(1f))
            LayoutButton(ReportLayout.DAILY_LOG, stringResource(R.string.report_layout_daily), selected == ReportLayout.DAILY_LOG, onSelect, Modifier.weight(1f))
            LayoutButton(ReportLayout.FULL, stringResource(R.string.report_layout_full), selected == ReportLayout.FULL, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
fun LayoutButton(layout: ReportLayout, label: String, isSelected: Boolean, onSelect: (ReportLayout) -> Unit, modifier: Modifier) {
    FilterChip(
        selected = isSelected,
        onClick = { onSelect(layout) },
        label = { Text(label, maxLines = 1) },
        modifier = modifier
    )
}

@Composable
fun GlucoseStatsSection(metrics: ReportMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Glucose Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            MetricRow(stringResource(R.string.report_avg_glucose), "%.0f mg/dL".format(metrics.avgGlucose))
            MetricRow(stringResource(R.string.report_gmi), "%.1f %%".format(metrics.gmi))
            MetricRow("Variability (CV)", "%.1f %%".format(metrics.cv))
            
            Spacer(modifier = Modifier.height(16.dp))
            TirBarAdvanced(metrics)
        }
    }
}

@Composable
fun InsulinStatsSection(metrics: ReportMetrics) {
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
fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TirBarAdvanced(m: ReportMetrics) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Time In Range (70-180)", style = MaterialTheme.typography.labelSmall)
            Text(text = "%.0f%%".format(m.tir), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        
        Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
            if (m.tbrVLow > 0) Box(Modifier.weight(m.tbrVLow.toFloat()).fillMaxHeight().background(Color(0xFF8B0000)))
            if (m.tbrLow > 0) Box(Modifier.weight(m.tbrLow.toFloat()).fillMaxHeight().background(Color.Red))
            if (m.tir > 0) Box(Modifier.weight(m.tir.toFloat()).fillMaxHeight().background(Color(0xFF008000)))
            if (m.tarHigh > 0) Box(Modifier.weight(m.tarHigh.toFloat()).fillMaxHeight().background(Color(0xFFFFA500)))
            if (m.tarVHigh > 0) Box(Modifier.weight(m.tarVHigh.toFloat()).fillMaxHeight().background(Color(0xFFFF4500)))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Low: %.0f%%".format(m.tbrLow + m.tbrVLow), style = MaterialTheme.typography.labelSmall, color = Color.Red)
            Text(text = "High: %.0f%%".format(m.tarHigh + m.tarVHigh), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA500))
        }
    }
}

private fun sharePdf(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Report"))
}
