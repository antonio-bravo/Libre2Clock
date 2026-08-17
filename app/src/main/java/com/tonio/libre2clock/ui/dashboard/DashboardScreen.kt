package com.tonio.libre2clock.ui.dashboard

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.data.model.SensorStatus
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.util.SensorErrorSummary
import com.tonio.libre2clock.data.repository.InsulinProcessor
import com.tonio.libre2clock.ui.insulin.InsulinDoseDialog
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToStrategy: () -> Unit,
    onNavigateToCapillary: () -> Unit,
    onNavigateToSensorLogs: () -> Unit,
    onNavigateToInsulinHub: () -> Unit,
    onNavigateToReports: () -> Unit,
    onAddDose: (InsulinDose) -> Unit
) {
    val currentGlucose by viewModel.currentGlucose.collectAsStateWithLifecycle()
    val sensorStatus by viewModel.sensorStatus.collectAsStateWithLifecycle()
    val graphData by viewModel.graphData.collectAsStateWithLifecycle()
    val insulinDoses by viewModel.insulinDoses.collectAsStateWithLifecycle()
    val manualTdi by viewModel.manualTdi.collectAsStateWithLifecycle()
    val manualIsf by viewModel.manualIsf.collectAsStateWithLifecycle()
    val isfRuleConstant by viewModel.isfRuleConstant.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val isHistoryRefreshing by viewModel.isHistoryRefreshing.collectAsStateWithLifecycle()
    val dashboardMetrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val graphWindowDays by viewModel.graphWindowDays.collectAsStateWithLifecycle()
    val currentSensorError by viewModel.currentSensorError.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var showCapillaryDialog by remember { mutableStateOf(false) }
    var capillaryValueText by remember { mutableStateOf("") }
    var capillaryDateText by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_settings)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_strategies)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToStrategy()
                    },
                    icon = { Icon(Icons.Default.QueryStats, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_capillary)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToCapillary()
                    },
                    icon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_sensor_logs)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSensorLogs()
                    },
                    icon = { Icon(Icons.Default.Sensors, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_insulin_hub)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToInsulinHub()
                    },
                    icon = { Icon(Icons.Default.Vaccines, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_reports)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToReports()
                    },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.dashboard_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            capillaryValueText = ""
                            capillaryDateText = currentDateTimeText()
                            showCapillaryDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = stringResource(R.string.add_capillary_reading),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlucoseCard(currentGlucose, dashboardMetrics)
                Spacer(modifier = Modifier.height(16.dp))
                SensorHealthCard(
                    status = sensorStatus,
                    errorSummary = currentSensorError,
                    isRefreshing = isRefreshing,
                    isDemoMode = isDemoMode,
                    onRefresh = viewModel::refresh
                )
                Spacer(modifier = Modifier.height(16.dp))
                InsulinHealthCard(
                    doses = insulinDoses,
                    onAddDose = onAddDose,
                    onNavigateToHub = onNavigateToInsulinHub,
                    manualTdi = manualTdi,
                    manualIsf = manualIsf,
                    isfRuleConstant = isfRuleConstant
                )
                Spacer(modifier = Modifier.height(16.dp))
                DashboardSlidesCard(
                    metrics = dashboardMetrics,
                    isRefreshing = isHistoryRefreshing,
                    onRefresh = viewModel::refreshHistoryWindow
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Trend Graph", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    var showGraphMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { showGraphMenu = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "${graphWindowDays}d",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showGraphMenu,
                            onDismissRequest = { showGraphMenu = false }
                        ) {
                            listOf(1, 2, 7, 14, 30, 90).forEach { days ->
                                DropdownMenuItem(
                                    text = { Text("${days} days") },
                                    onClick = {
                                        viewModel.setGraphWindow(days)
                                        showGraphMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                InteractiveTrendGraph(
                    measurements = graphData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                )
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
                    OutlinedTextField(
                        value = capillaryDateText,
                        onValueChange = { capillaryDateText = it },
                        label = { Text(stringResource(R.string.capillary_timestamp_label)) },
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
                    val timestamp = capillaryDateText.ifBlank { currentDateTimeText() }
                    viewModel.addCapillaryReading(
                        CapillaryMeasurement(
                            value = value,
                            timestamp = timestamp,
                            sensorValue = sensorValue,
                            delta = delta
                        )
                    )
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

private fun currentDateTimeText(): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())
}

@Composable
fun InsulinHealthCard(
    doses: List<InsulinDose>,
    onAddDose: (InsulinDose) -> Unit,
    onNavigateToHub: () -> Unit,
    manualTdi: Double?,
    manualIsf: Double?,
    isfRuleConstant: Int
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val totalToday = remember(doses) { InsulinProcessor.calculateDailyTotal(doses, today) }
    val todayRapid = remember(doses) { InsulinProcessor.calculateDailyTotal(doses, today, InsulinType.RAPID) }
    val todaySlow = remember(doses) { InsulinProcessor.calculateDailyTotal(doses, today, InsulinType.SLOW) }

    val calculatedTdi = remember(doses) { InsulinProcessor.calculateAverageDaily(doses, 30) }
    val tdi = manualTdi ?: calculatedTdi
    val currentIsf = InsulinProcessor.calculateISF(tdi, isfRuleConstant, manualIsf)
    
    val yesterdaySplit = remember(doses) { InsulinProcessor.calculateDailyTotalSplit(doses, yesterday) }
    val totalIOB = remember(doses) { InsulinProcessor.calculateTotalIOB(doses) }
    val rapidIOB = remember(doses) { doses.filter { it.type == InsulinType.RAPID }.let { InsulinProcessor.calculateTotalIOB(it) } }
    val slowIOB = remember(doses) { doses.filter { it.type == InsulinType.SLOW }.let { InsulinProcessor.calculateTotalIOB(it) } }
    
    val activeThreads = remember(doses) { doses.count { InsulinProcessor.calculateIOB(it) > 0 } }
    
    val weekAvg = remember(doses) { InsulinProcessor.calculateAverageDailySplit(doses, 7) }
    val monthAvg = remember(doses) { InsulinProcessor.calculateAverageDailySplit(doses, 30) }

    var showAddDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = stringResource(R.string.insulin_info), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.insulin_header_breakdown, totalToday, todayRapid, todaySlow),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row {
                    IconButton(onClick = onNavigateToHub) {
                        Icon(Icons.Default.History, contentDescription = "Hub", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Dose", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.height(80.dp)
            ) { page ->
                when (page) {
                    0 -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.insulin_active_rapid), style = MaterialTheme.typography.labelSmall)
                            Text(text = "%.2f U".format(rapidIOB), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        VerticalDivider(modifier = Modifier.height(40.dp).padding(horizontal = 8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.insulin_active_slow), style = MaterialTheme.typography.labelSmall)
                            Text(text = "%.2f U".format(slowIOB), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        VerticalDivider(modifier = Modifier.height(40.dp).padding(horizontal = 8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Total IOB", style = MaterialTheme.typography.labelSmall)
                            Text(text = "%.2f U".format(totalIOB), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(R.string.dash_fs_label, currentIsf) + if (manualIsf != null) " (M)" else " (C)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    1 -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "%.1f U".format(yesterdaySplit.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "%.1fR/%.1fS".format(yesterdaySplit.rapid, yesterdaySplit.slow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            Text(text = stringResource(R.string.insulin_yesterday), style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "%.1f U".format(weekAvg.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "%.1fR/%.1fS".format(weekAvg.rapid, weekAvg.slow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            Text(text = stringResource(R.string.insulin_7d_avg), style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "%.1f U".format(monthAvg.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "%.1fR/%.1fS".format(monthAvg.rapid, monthAvg.slow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            Text(text = stringResource(R.string.insulin_30d_avg), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            
            Row(
                Modifier.fillMaxWidth().height(12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(2) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(color)
                            .size(6.dp)
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        InsulinDoseDialog(
            rapidDuration = 240, // 4h default
            slowDuration = 1440, // 24h default
            onDismiss = { showAddDialog = false },
            onConfirm = {
                onAddDose(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun SensorHealthCard(
    status: SensorStatus?,
    errorSummary: SensorErrorSummary?,
    isRefreshing: Boolean,
    isDemoMode: Boolean,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDemoMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.sensor_health),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (isDemoMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.demo_mode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Row {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(24.dp),
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                modifier = Modifier.size(18.dp),
                                tint = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (status != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = """
                                    Sensor SN: ${status.serialNumber}
                                    ${status.startDate}
                                    ${status.expiryDate}
                                    Remaining: ${status.daysRemaining}
                                """.trimIndent()
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Sensor Info", text)))
                                }
                                Toast.makeText(context, context.getString(R.string.copy_sensor_info), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy sensor info",
                                modifier = Modifier.size(18.dp),
                                tint = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (status != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = status.daysRemaining,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = status.startDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "SN: ${status.serialNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                            textAlign = TextAlign.End
                        )
                    }
                    Text(
                        text = status.expiryDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )

                    if (errorSummary != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = (if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.2f)
                        )
                        Text(
                            text = stringResource(
                                R.string.sensor_error_summary_row,
                                errorSummary.samples,
                                errorSummary.avgAbsoluteDeviationPct,
                                errorSummary.avgSignedDeviationPct
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.no_sensor_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDemoMode) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun GlucoseCard(measurement: GlucoseMeasurement?, metrics: DashboardMetrics) {
    val now = Instant.now()
    val measurementInstant = measurement?.let { m ->
        m.epochSeconds?.let { Instant.ofEpochSecond(it) }
            ?: TimestampParser.parseFlexibleInstant(m.factoryTimestamp)
            ?: TimestampParser.parseFlexibleInstant(m.timestamp)
    }
    
    val isStale = measurementInstant?.let { 
        java.time.Duration.between(it, now).toMinutes() > 15 
    } ?: false

    // Format timestamp to yyyy-MM-dd HH:mm:ss using TimestampParser for flexibility
    val lastSyncText = measurementInstant?.let { instant ->
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } ?: "------ --:--:--"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isStale) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CornerMetric(
                    title = "Estimated HbA1c (90d)",
                    primary = metrics.estimatedA1c.primary,
                    secondary = metrics.estimatedA1c.secondary,
                    isStale = isStale,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                CornerMetric(
                    title = "Avg Glucose",
                    primary = metrics.todayAvg.primary,
                    secondary = metrics.todayAvg.secondary,
                    alignEnd = true,
                    isStale = isStale,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (measurement != null) {
                    if (isStale) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "SIGNAL LOST / STALE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (displayValue.length > 6) 48.sp else 64.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isStale) 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else 
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "mg/dL",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isStale) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (!isStale) TrendIcon(measurement.trendArrow)
                        }
                    }
                    Text(
                        text = stringResource(R.string.last_sync, lastSyncText),
                        style = MaterialTheme.typography.bodySmall,
                        color = (if (isStale) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.7f)
                    )
                } else {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.fetching_data))
                }
            }
        }
    }
}

@Composable
private fun CornerMetric(
    title: String,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    isStale: Boolean = false
) {
    val contentColor = if (isStale)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.7f),
            maxLines = 1
        )
        Text(
            text = primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1
        )
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardSlidesCard(
    metrics: DashboardMetrics,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val pageTitle = when (pagerState.currentPage) {
        0 -> "Avg Glucose"
        1 -> "Avg Glucose Last Month"
        else -> "Hypos Last Month"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { index ->
                            val active = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (active) Color(0xFF0B57D0) else Color(0xFFD6DCE5),
                                        shape = RoundedCornerShape(50)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh historical data"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> MetricsRow(
                        first = metrics.yesterdayAvg,
                        second = metrics.weekAvg,
                        third = metrics.monthAvg,
                        firstLabel = "Yesterday",
                        secondLabel = "Week",
                        thirdLabel = "Month"
                    )

                    1 -> MetricsRow(
                        first = metrics.breakfastMonthAvg,
                        second = metrics.lunchMonthAvg,
                        third = metrics.dinnerMonthAvg,
                        firstLabel = "Breakfast",
                        secondLabel = "Lunch",
                        thirdLabel = "Dinner"
                    )

                    else -> HyposRow(
                        breakfast = metrics.breakfastHypos,
                        lunch = metrics.lunchHypos,
                        dinner = metrics.dinnerHypos
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsRow(
    first: DisplayMetric,
    second: DisplayMetric,
    third: DisplayMetric,
    firstLabel: String,
    secondLabel: String,
    thirdLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCell(metric = first, label = firstLabel, modifier = Modifier.weight(1f))
        MetricCell(metric = second, label = secondLabel, modifier = Modifier.weight(1f))
        MetricCell(metric = third, label = thirdLabel, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HyposRow(
    breakfast: CountMetric,
    lunch: CountMetric,
    dinner: CountMetric
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HypoCell(metric = breakfast, label = "Breakfast", modifier = Modifier.weight(1f))
        HypoCell(metric = lunch, label = "Lunch", modifier = Modifier.weight(1f))
        HypoCell(metric = dinner, label = "Dinner", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(metric: DisplayMetric, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = metric.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (metric.secondary.isNotEmpty()) {
            Text(
                text = metric.secondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun HypoCell(metric: CountMetric, label: String, modifier: Modifier = Modifier) {
    val rawCount = metric.count - metric.offset
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$rawCount(${metric.count})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
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
