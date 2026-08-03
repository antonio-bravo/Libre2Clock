package com.tonio.libre2clock.ui.insulin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.data.repository.InsulinProcessor
import com.tonio.libre2clock.ui.settings.SettingsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinHubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val doses by viewModel.insulinDoses.collectAsStateWithLifecycle()
    val rapidDurationMins by viewModel.rapidDurationMins.collectAsStateWithLifecycle()
    val slowDurationMins by viewModel.slowDurationMins.collectAsStateWithLifecycle()
    val icRuleConstant by viewModel.icRuleConstant.collectAsStateWithLifecycle()
    val isfRuleConstant by viewModel.isfRuleConstant.collectAsStateWithLifecycle()
    val manualTdi by viewModel.manualTdi.collectAsStateWithLifecycle()
    val manualIsf by viewModel.manualIsf.collectAsStateWithLifecycle()
    val targetGlucose by viewModel.targetGlucose.collectAsStateWithLifecycle()
    val currentGlucoseData by viewModel.currentGlucose.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    val calculatedTdi = remember(doses) { InsulinProcessor.calculateAverageDaily(doses, 30) }
    val tdi = manualTdi ?: calculatedTdi
    val calculatedIsf = if (tdi > 0) isfRuleConstant.toDouble() / tdi else 0.0
    val isf = manualIsf ?: calculatedIsf
    val icRatio = if (tdi > 0) icRuleConstant.toDouble() / tdi else 0.0

    val totalIOB = remember(doses) { InsulinProcessor.calculateTotalIOB(doses) }
    val rapidIOB = remember(doses) { doses.filter { it.type == InsulinType.RAPID }.let { InsulinProcessor.calculateTotalIOB(it) } }
    val slowIOB = remember(doses) { doses.filter { it.type == InsulinType.SLOW }.let { InsulinProcessor.calculateTotalIOB(it) } }
    val activeThreads = remember(doses) { doses.count { InsulinProcessor.calculateIOB(it) > 0 } }

    val today = LocalDate.now()
    val todayTotal = remember(doses, today) { InsulinProcessor.calculateDailyTotal(doses, today) }
    val todayRapid = remember(doses) { InsulinProcessor.calculateDailyTotal(doses, today, InsulinType.RAPID) }
    val todaySlow = remember(doses) { InsulinProcessor.calculateDailyTotal(doses, today, InsulinType.SLOW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insulin_hub_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_insulin_dose))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: ACTIVE STATUS
            item {
                ActiveInsulinCard(
                    total = totalIOB,
                    rapid = rapidIOB,
                    slow = slowIOB,
                    fs = isf,
                    activeThreads = activeThreads,
                    isManualFs = manualIsf != null,
                    calculatedTdi = calculatedTdi,
                    calculatedIsf = calculatedIsf,
                    todayTotal = todayTotal,
                    isManualTdi = manualTdi != null
                )
            }

            // Section 2: SUMMARY
            item {
                InsulinSummaryCard(
                    tdi = tdi,
                    calculatedTdi = calculatedTdi,
                    isf = isf,
                    calculatedIsf = calculatedIsf,
                    icRatio = icRatio,
                    todayTotal = todayTotal,
                    isManualTdi = manualTdi != null,
                    isManualIsf = manualIsf != null
                )
            }

            // Section 3: CALCULATOR
            item {
                BolusCalculatorCard(
                    tdi = tdi,
                    calculatedTdi = calculatedTdi,
                    icRatio = icRatio,
                    isf = isf,
                    calculatedIsf = calculatedIsf,
                    manualIsf = manualIsf,
                    manualTdi = manualTdi,
                    icConstant = icRuleConstant,
                    isfConstant = isfRuleConstant,
                    targetGlucose = targetGlucose,
                    currentGlucose = currentGlucoseData,
                    doses = doses,
                    viewModel = viewModel
                )
            }

            // Section 4: TODAY STATS
            item {
                TodayStatsCard(todayRapid, todaySlow)
            }

            // Section 5: VIEW LOGS (Bottom link)
            item {
                Button(
                    onClick = onNavigateToLogs,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
            // Section 6: SETTINGS (Always Visible)
                }
            }

            // Section 5: SETTINGS (Always Visible)
            item {
                AdvancedSettingsCard(
                    rapidDurationMins,
                    slowDurationMins,
                    icRuleConstant,
                    isfRuleConstant,
                    manualTdi,
                    manualIsf,
                    viewModel
                )
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        InsulinDoseDialog(
            rapidDuration = rapidDurationMins,
            slowDuration = slowDurationMins,
            onDismiss = { showAddDialog = false },
            onConfirm = {
                viewModel.addInsulinDose(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun InsulinSummaryCard(
    tdi: Double,
    calculatedTdi: Double,
    isf: Double,
    calculatedIsf: Double,
    icRatio: Double,
    todayTotal: Double,
    isManualTdi: Boolean,
    isManualIsf: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.insulin_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.insulin_tdi_full), style = MaterialTheme.typography.labelSmall)
                    Text(text = "%.1f U".format(tdi), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isManualTdi) stringResource(R.string.insulin_manual) else stringResource(R.string.insulin_auto_30d),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.insulin_isf_full), style = MaterialTheme.typography.labelSmall)
                    Text(text = "%.1f".format(isf), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isManualIsf) stringResource(R.string.insulin_manual) else stringResource(R.string.insulin_calculated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.insulin_ic_full), style = MaterialTheme.typography.labelSmall)
                    Text(text = "%.1f g/U".format(icRatio), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.insulin_calculated_with_tdi),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.insulin_daily_total_today_label), style = MaterialTheme.typography.labelSmall)
                    Text(text = "%.1f U".format(todayTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.insulin_tdi_calc_short, calculatedTdi),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                text = stringResource(R.string.insulin_isf_calc_val, calculatedIsf),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
fun ActiveInsulinCard(
    total: Double,
    rapid: Double,
    slow: Double,
    fs: Double,
    activeThreads: Int,
    isManualFs: Boolean,
    calculatedTdi: Double,
    calculatedIsf: Double,
    todayTotal: Double,
    isManualTdi: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.insulin_active_total), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.insulin_active_units, total),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.insulin_active_split, rapid, slow),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(text = stringResource(R.string.insulin_active_threads, activeThreads), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
            Text(
                text = stringResource(R.string.insulin_daily_total_today_val, todayTotal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dash_fs_label, fs),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isManualFs) stringResource(R.string.insulin_manual) else stringResource(R.string.insulin_calculated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.insulin_tdi_30d_val,
                    calculatedTdi,
                    if (isManualTdi) stringResource(R.string.insulin_manual) else stringResource(R.string.insulin_auto_30d)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = stringResource(R.string.insulin_isf_auto_from_tdi, calculatedIsf),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun BolusCalculatorCard(
    tdi: Double,
    calculatedTdi: Double,
    icRatio: Double,
    isf: Double,
    calculatedIsf: Double,
    manualIsf: Double?,
    manualTdi: Double?,
    icConstant: Int,
    isfConstant: Int,
    targetGlucose: Int,
    currentGlucose: GlucoseMeasurement?,
    doses: List<InsulinDose>,
    viewModel: SettingsViewModel
) {
    var carbsText by remember { mutableStateOf("") }
    
    val initialGlucoseText = remember(currentGlucose) {
        val real = currentGlucose?.value ?: 0
        val cal = currentGlucose?.calibratedValue ?: 0
        if (real > 0 && real != cal) {
            "$real($cal)"
        } else if (real > 0) {
            "$real"
        } else {
            ""
        }
    }
    
    var glucoseText by remember(initialGlucoseText) { mutableStateOf(initialGlucoseText) }
    val isBasalExpiringSoon = remember(doses) { InsulinProcessor.isBasalExpiringSoon(doses) }

    val suggestedResults = remember(carbsText, glucoseText, tdi, isBasalExpiringSoon, isf, targetGlucose) {
        val carbs = carbsText.toDoubleOrNull() ?: 0.0
        
        val cleanText = glucoseText.replace(" ", "")
        val glucoseParts = cleanText.replace(")", "").split("(")
        val realG = glucoseParts.getOrNull(0)?.toIntOrNull() ?: 0
        val calG = glucoseParts.getOrNull(1)?.toIntOrNull() ?: realG
        
        val breakdownReal = InsulinProcessor.getSuggestedBolusDetailed(carbs, realG, targetGlucose, tdi, icConstant, isf, isBasalExpiringSoon)
        val breakdownCal = InsulinProcessor.getSuggestedBolusDetailed(carbs, calG, targetGlucose, tdi, icConstant, isf, isBasalExpiringSoon)
        
        Triple(realG, calG, breakdownReal to breakdownCal)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.calc_bolus_helper), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { carbsText = it },
                    label = { Text(stringResource(R.string.calc_carbs_label)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = glucoseText,
                    onValueChange = { glucoseText = it },
                    label = { Text(stringResource(R.string.calc_glucose_label)) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.insulin_placeholder_real_offset)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = targetGlucose.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateTargetGlucose) },
                label = { Text(stringResource(R.string.settings_target_glucose)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (isBasalExpiringSoon) {
                Text(
                    text = stringResource(R.string.calc_basal_expiring_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            val (realG, calG, breakdowns) = suggestedResults
            val (bReal, bCal) = breakdowns

            val isDual = realG != calG && realG > 0
            
            // Format with floor(x * 100) / 100 to match user's 3.86 expectation
            val formatValue = { v: Double -> (Math.floor(v * 100) / 100.0) }
            
            val suggestedText = if (isDual) {
                "${"%.2f".format(formatValue(bReal.total))}(${"%.2f".format(formatValue(bCal.total))})"
            } else {
                "%.2f".format(formatValue(bReal.total))
            }
            
            var showLogDialog by remember { mutableStateOf(false) }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.calc_suggested_bolus_dual, suggestedText),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Log Suggested Dose")
                    }
                }
                
                if (showLogDialog) {
                    val initialUnits = if (viewModel.useCalibratedForAlarms.collectAsState(initial = true).value) bCal.total else bReal.total
                    InsulinDoseDialog(
                        initialDose = InsulinDose(
                            units = (Math.floor(initialUnits * 100) / 100.0),
                            timestamp = "", // Handled by dialog
                            type = InsulinType.RAPID,
                            durationMinutes = 0, // Handled by dialog
                            carbs = carbsText.toDoubleOrNull()
                        ),
                        rapidDuration = viewModel.rapidDurationMins.collectAsState(initial = 240).value,
                        slowDuration = viewModel.slowDurationMins.collectAsState(initial = 1440).value,
                        onDismiss = { showLogDialog = false },
                        onConfirm = {
                            viewModel.addInsulinDose(it)
                            showLogDialog = false
                        }
                    )
                }
                
                if (isDual) {
                    Text(
                        text = "R: (HC: %.2f + Corr: %.2f) | O: (HC: %.2f + Corr: %.2f)".format(bReal.carbDose, bReal.correctionDose, bCal.carbDose, bCal.correctionDose),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.calc_breakdown_label, bReal.carbDose, bReal.correctionDose),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            // Ratio info
            Text(text = stringResource(R.string.calc_ic_ratio, icConstant, icRatio), style = MaterialTheme.typography.labelMedium)
            Text(
                text = stringResource(R.string.calc_isf, isf) + " " + if (manualIsf != null) stringResource(R.string.insulin_manual) else stringResource(R.string.insulin_calculated),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = stringResource(R.string.insulin_isf_auto_from_tdi, calculatedIsf),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(
                    R.string.insulin_tdi_used_val,
                    tdi,
                    if (manualTdi != null) stringResource(R.string.insulin_manual) else stringResource(R.string.insulin_auto_30d)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(R.string.insulin_tdi_calc_30d_val, calculatedTdi),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun TodayStatsCard(rapid: Double, slow: Double) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.insulin_today_stats), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.insulin_total_breakdown, rapid + slow, rapid, slow),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AdvancedSettingsCard(
    rapidMin: Int,
    slowMin: Int,
    icC: Int,
    isfC: Int,
    mTdi: Double?,
    mIsf: Double?,
    viewModel: SettingsViewModel
) {
    var tdiText by remember(mTdi) { mutableStateOf(mTdi?.toString() ?: "") }
    var isfText by remember(mIsf) { mutableStateOf(mIsf?.toString() ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.settings_advanced_insulin),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            DurationInput(stringResource(R.string.settings_rapid_duration), rapidMin, viewModel::updateRapidDuration)
            DurationInput(stringResource(R.string.settings_slow_duration), slowMin, viewModel::updateSlowDuration)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

            OutlinedTextField(
                value = icC.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateIcRuleConstant) },
                label = { Text(stringResource(R.string.settings_ic_rule)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = isfC.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateIsfRuleConstant) },
                label = { Text(stringResource(R.string.settings_isf_rule)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

            // Manual TDI with Switch
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.settings_manual_tdi), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = mTdi != null,
                        onCheckedChange = { isEnabled ->
                            if (!isEnabled) {
                                viewModel.updateManualTdi(null)
                            } else {
                                viewModel.updateManualTdi(tdiText.toDoubleOrNull() ?: 0.0)
                            }
                        }
                    )
                }
                if (mTdi != null) {
                    OutlinedTextField(
                        value = tdiText,
                        onValueChange = {
                            tdiText = it
                            if (it.isNotBlank()) {
                                it.toDoubleOrNull()?.let(viewModel::updateManualTdi)
                            }
                        },
                        label = { Text(stringResource(R.string.insulin_manual_tdi_value_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            // Manual ISF with Switch
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.settings_manual_isf), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = mIsf != null,
                        onCheckedChange = { isEnabled ->
                            if (!isEnabled) {
                                viewModel.updateManualIsf(null)
                            } else {
                                viewModel.updateManualIsf(isfText.toDoubleOrNull() ?: 0.0)
                            }
                        }
                    )
                }
                if (mIsf != null) {
                    OutlinedTextField(
                        value = isfText,
                        onValueChange = {
                            isfText = it
                            if (it.isNotBlank()) {
                                it.toDoubleOrNull()?.let(viewModel::updateManualIsf)
                            }
                        },
                        label = { Text(stringResource(R.string.insulin_manual_isf_value_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }
    }
}

@Composable
fun DoseItem(
    dose: InsulinDose,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val iob = remember(dose) { InsulinProcessor.calculateIOB(dose) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val typeLabel = if (dose.type == InsulinType.RAPID) stringResource(R.string.insulin_rapid_label) else stringResource(R.string.insulin_slow_label)
                Text(text = "${dose.units} U - $typeLabel" + (if (dose.carbs != null) " (${dose.carbs}g HC)" else ""), fontWeight = FontWeight.Bold)
                Text(text = dose.timestamp, style = MaterialTheme.typography.bodySmall)
                if (iob > 0) {
                    Text(
                        text = "Active: %.2f U".format(iob),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
fun InsulinDoseDialog(
    initialDose: InsulinDose? = null,
    rapidDuration: Int,
    slowDuration: Int,
    onDismiss: () -> Unit,
    onConfirm: (InsulinDose) -> Unit
) {
    var unitsText by remember { mutableStateOf(initialDose?.units?.toString() ?: "") }
    var carbsText by remember { mutableStateOf(initialDose?.carbs?.toString() ?: "") }
    var type by remember { mutableStateOf(initialDose?.type ?: InsulinType.RAPID) }
    
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val currentFormattedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(now)
    val currentFormattedTime = DateTimeFormatter.ofPattern("HH:mm").format(now)

    var dateText by remember { 
        val initialDate = initialDose?.timestamp?.substringBefore(" ") ?: ""
        mutableStateOf(if (initialDate.isBlank()) currentFormattedDate else initialDate) 
    }
    var timeText by remember { 
        val initialTime = initialDose?.timestamp?.substringAfter(" ") ?: ""
        mutableStateOf(if (initialTime.isBlank()) currentFormattedTime else initialTime) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialDose == null) R.string.add_insulin_dose else R.string.edit_insulin_dose)) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitsText,
                        onValueChange = { unitsText = it },
                        label = { Text(stringResource(R.string.insulin_units_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it },
                        label = { Text(stringResource(R.string.calc_carbs_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text(stringResource(R.string.insulin_date_format_label)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text(stringResource(R.string.insulin_time_format_label)) },
                        modifier = Modifier.weight(0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.insulin_type_label), style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == InsulinType.RAPID, onClick = { type = InsulinType.RAPID })
                    Text(stringResource(R.string.insulin_rapid_label))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = type == InsulinType.SLOW, onClick = { type = InsulinType.SLOW })
                    Text(stringResource(R.string.insulin_slow_label))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val units = unitsText.toDoubleOrNull() ?: return@TextButton
                val carbs = carbsText.toDoubleOrNull()
                val timestamp = "$dateText $timeText"
                onConfirm(
                    InsulinDose(
                        units = units,
                        timestamp = timestamp,
                        type = type,
                        durationMinutes = if (type == InsulinType.RAPID) rapidDuration else slowDuration,
                        carbs = carbs
                    )
                )
            }) {
                Text(stringResource(if (initialDose == null) android.R.string.ok else R.string.update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun DurationInput(
    label: String,
    initialMinutes: Int,
    onValueChange: (Int) -> Unit
) {
    val hours = initialMinutes / 60
    val mins = initialMinutes % 60
    var textValue by remember(initialMinutes) { mutableStateOf("%02d:%02d".format(hours, mins)) }

    OutlinedTextField(
        value = textValue,
        onValueChange = {
            textValue = it
            val parts = it.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                onValueChange(h * 60 + m)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("HH:mm") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}
