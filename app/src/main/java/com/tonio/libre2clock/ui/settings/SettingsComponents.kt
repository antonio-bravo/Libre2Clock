package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.AlarmSchedule
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.RangeOffsetInsight
import com.tonio.libre2clock.util.SectionPerfTelemetry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsCategoryItem(
    title: String,
    description: String,
    icon: ImageVector,
    perfStats: SectionPerfTelemetry.Snapshot? = null,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (perfStats != null) {
                PerformanceMetricBadge(perfStats)
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun ScheduleItem(
    schedule: AlarmSchedule,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = schedule.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${schedule.startTime} - ${schedule.endTime}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (schedule.intervalMinutes != null || schedule.startMinute != null) {
                    val intv = schedule.intervalMinutes?.toString() ?: stringResource(R.string.settings_global_placeholder)
                    val start = schedule.startMinute?.toString() ?: stringResource(R.string.settings_global_placeholder)
                    Text(
                        text = stringResource(R.string.settings_schedule_interval_row, intv, start),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                val days = schedule.daysOfWeek.sorted().map { day ->
                    when (day) {
                        1 -> stringResource(R.string.day_mon)
                        2 -> stringResource(R.string.day_tue)
                        3 -> stringResource(R.string.day_wed)
                        4 -> stringResource(R.string.day_thu)
                        5 -> stringResource(R.string.day_fri)
                        6 -> stringResource(R.string.day_sat)
                        7 -> stringResource(R.string.day_sun)
                        else -> ""
                    }
                }.joinToString(", ")
                Text(text = days, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = schedule.isEnabled, onCheckedChange = onToggle, modifier = Modifier.scale(0.7f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    isWatchSchedule: Boolean,
    initialSchedule: AlarmSchedule? = null,
    onDismiss: () -> Unit,
    onConfirm: (AlarmSchedule) -> Unit
) {
    var name by remember { mutableStateOf(initialSchedule?.name ?: "Normal Schedule") }
    var startTime by remember { mutableStateOf(initialSchedule?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(initialSchedule?.endTime ?: "22:00") }
    var selectedDays by remember { mutableStateOf(initialSchedule?.daysOfWeek?.toSet() ?: (1..7).toSet()) }
    
    var intervalText by remember { mutableStateOf(initialSchedule?.intervalMinutes?.toString() ?: "") }
    var startMinuteText by remember { mutableStateOf(initialSchedule?.startMinute?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialSchedule == null) R.string.settings_add_active_schedule else R.string.settings_edit_schedule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.settings_schedule_name)) }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text(stringResource(R.string.settings_schedule_start)) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text(stringResource(R.string.settings_schedule_end)) }, modifier = Modifier.weight(1f))
                }
                
                if (isWatchSchedule) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.settings_watch_overrides), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text(stringResource(R.string.settings_override_interval)) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.settings_global_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = startMinuteText,
                            onValueChange = { startMinuteText = it },
                            label = { Text(stringResource(R.string.settings_override_start_minute)) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.settings_global_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Text(stringResource(R.string.settings_active_days), style = MaterialTheme.typography.labelMedium)
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { day ->
                        val label = when(day) {
                            1 -> stringResource(R.string.day_mon_short)
                            2 -> stringResource(R.string.day_tue_short)
                            3 -> stringResource(R.string.day_wed_short)
                            4 -> stringResource(R.string.day_thu_short)
                            5 -> stringResource(R.string.day_fri_short)
                            6 -> stringResource(R.string.day_sat_short)
                            7 -> stringResource(R.string.day_sun_short)
                            else -> ""
                        }
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                if (day in selectedDays) selectedDays = selectedDays - day
                                else selectedDays = selectedDays + day
                            },
                            label = { Text(label) },
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    AlarmSchedule(
                        id = initialSchedule?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name,
                        startTime = startTime,
                        endTime = endTime,
                        daysOfWeek = selectedDays.toList(),
                        isEnabled = initialSchedule?.isEnabled ?: true,
                        intervalMinutes = intervalText.toIntOrNull(),
                        startMinute = startMinuteText.toIntOrNull()
                    )
                )
            }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
fun RangeItem(
    range: GlucoseOffsetRange,
    insight: RangeOffsetInsight?,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val maxText = range.max?.toString() ?: "∞"
                Text(text = stringResource(R.string.settings_range_label, range.min, maxText), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.settings_range_fixed_offset, if (range.offset >= 0) "+" else "", range.offset), style = MaterialTheme.typography.bodyMedium)
                Text(text = stringResource(R.string.settings_range_percentage_offset, if (range.percentage >= 0) "+" else "", range.percentage), style = MaterialTheme.typography.bodyMedium)
                if (insight != null) {
                    Text(
                        text = stringResource(R.string.settings_range_sensor_audit, if (insight.signedRawDeviationPct >= 0) "+" else "", insight.signedRawDeviationPct, insight.sampleCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (abs(insight.signedRawDeviationPct) > 15.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
fun RangeDialog(
    initialRange: GlucoseOffsetRange? = null,
    onDismiss: () -> Unit,
    onConfirm: (GlucoseOffsetRange) -> Unit
) {
    var minText by remember { mutableStateOf(initialRange?.min?.toString() ?: "") }
    var maxText by remember { mutableStateOf(initialRange?.max?.toString() ?: "") }
    var offsetText by remember { mutableStateOf(initialRange?.offset?.toString() ?: "") }
    var percentageText by remember { mutableStateOf(initialRange?.percentage?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialRange == null) R.string.settings_add_range else R.string.settings_edit_range)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = minText, onValueChange = { minText = it }, label = { Text(stringResource(R.string.settings_min_glucose)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = maxText, onValueChange = { maxText = it }, label = { Text(stringResource(R.string.settings_max_glucose_label)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = offsetText, onValueChange = { offsetText = it }, label = { Text(stringResource(R.string.settings_fixed_offset_label)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = percentageText, onValueChange = { percentageText = it }, label = { Text(stringResource(R.string.settings_percentage_offset_label)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val min = minText.toIntOrNull() ?: 0
                val max = maxText.toIntOrNull()
                val offset = offsetText.toIntOrNull() ?: 0
                val percentage = percentageText.toIntOrNull() ?: 0
                onConfirm(GlucoseOffsetRange(min, max, offset, percentage))
            }) { Text(stringResource(R.string.settings_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
fun getLocalizedSectionName(section: String): String {
    return when (section) {
        "dashboard_metrics_v1", "dashboard_metrics_v2", "historical_metrics_v2" -> stringResource(R.string.settings_perf_section_dashboard)
        "report_metrics_v1", "report_metrics_v2" -> stringResource(R.string.settings_perf_section_report_metrics)
        "report_agp_v1", "report_agp_v2" -> stringResource(R.string.settings_perf_section_report_agp)
        "report_daily_v1", "report_daily_v2" -> stringResource(R.string.settings_perf_section_report_daily)
        "settings_range_insights_v1" -> stringResource(R.string.settings_perf_section_range_insights)
        "insulin_basal_expiry" -> stringResource(R.string.settings_perf_section_insulin_basal_expiry)
        "insulin_bolus_calc" -> stringResource(R.string.settings_perf_section_insulin_bolus_calc)
        "capillary_screen_enter" -> stringResource(R.string.settings_perf_section_capillary_enter)
        "capillary_screen_stats" -> stringResource(R.string.settings_perf_section_capillary_stats)
        "dashboard_screen_enter" -> stringResource(R.string.settings_perf_section_dashboard_enter)
        else -> stringResource(R.string.settings_perf_section_unknown, section)
    }
}

@Composable
fun SectionPerformanceCard(
    stats: SectionPerfTelemetry.Snapshot,
    label: String
) {
    val statusColor = when {
        stats.avgDurationMs < 50 -> Color(0xFF4CAF50)
        stats.avgDurationMs < 200 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.05f)
        ),
        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stringResource(R.string.settings_perf_hits_row).substringBefore("|").trim()} ${stats.calls} | Avg: ${stats.avgDurationMs.roundToInt()}${stringResource(R.string.settings_perf_ms_short)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PerformanceMetricBadge(
    stats: SectionPerfTelemetry.Snapshot?,
    modifier: Modifier = Modifier
) {
    if (stats == null) return
    
    val color = when {
        stats.avgDurationMs < 50 -> Color(0xFF4CAF50)
        stats.avgDurationMs < 200 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${stats.avgDurationMs.roundToInt()}${stringResource(R.string.settings_perf_ms_short)}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

fun formatBackupTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestamp))
}
