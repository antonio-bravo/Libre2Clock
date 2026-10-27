package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.util.TimestampParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsWidgetScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val widgetCompactMode by viewModel.widgetCompactMode.collectAsStateWithLifecycle()
    val widgetShowRapidInsulin by viewModel.widgetShowRapidInsulin.collectAsStateWithLifecycle()
    val widgetShowSlowInsulin by viewModel.widgetShowSlowInsulin.collectAsStateWithLifecycle()
    val widgetShowFS by viewModel.widgetShowFS.collectAsStateWithLifecycle()
    val widgetShowLastUpdate by viewModel.widgetShowLastUpdate.collectAsStateWithLifecycle()
    val widgetBackgroundOpacity by viewModel.widgetBackgroundOpacity.collectAsStateWithLifecycle()
    val widgetUseTargetColorBg by viewModel.widgetUseTargetColorBg.collectAsStateWithLifecycle()
    val widgetInsulinDecimals by viewModel.widgetInsulinDecimals.collectAsStateWithLifecycle()
    val widgetStaleIndicator by viewModel.widgetStaleIndicator.collectAsStateWithLifecycle()
    val currentGlucose by viewModel.currentGlucose.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_widget_title)) },
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
            // --- Live Widget Preview ---
            item(key = "widget_preview") {
                SettingsSection(title = stringResource(R.string.widget_preview_title)) {
                    WidgetLivePreviewCard(
                        compactMode = widgetCompactMode,
                        showRapid = widgetShowRapidInsulin,
                        showSlow = widgetShowSlowInsulin,
                        showFS = widgetShowFS,
                        showLastUpdate = widgetShowLastUpdate,
                        opacityPercent = widgetBackgroundOpacity,
                        useTargetColor = widgetUseTargetColorBg,
                        decimalsMode = widgetInsulinDecimals,
                        measurement = currentGlucose
                    )
                }
            }

            // --- Widget Settings Controls ---
            item(key = "widget_controls") {
                SettingsSection(title = stringResource(R.string.settings_widget_title)) {
                    // Modo Compacto
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_compact_mode_title),
                        description = stringResource(R.string.widget_compact_mode_desc),
                        checked = widgetCompactMode,
                        onCheckedChange = viewModel::setWidgetCompactMode,
                        icon = Icons.Default.Build
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Transparencia / Opacidad Slider
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
                                text = stringResource(R.string.widget_opacity_title, widgetBackgroundOpacity),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = widgetBackgroundOpacity.toFloat(),
                            onValueChange = { viewModel.setWidgetBackgroundOpacity(it.toInt()) },
                            valueRange = 0f..100f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.widget_opacity_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Color por objetivo de glucosa
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_use_target_color_title),
                        description = stringResource(R.string.widget_use_target_color_desc),
                        checked = widgetUseTargetColorBg,
                        onCheckedChange = viewModel::setWidgetUseTargetColorBg,
                        icon = Icons.Default.Edit
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Indicador inteligente de desincronización
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_stale_indicator_title),
                        description = stringResource(R.string.widget_stale_indicator_desc),
                        checked = widgetStaleIndicator,
                        onCheckedChange = viewModel::setWidgetStaleIndicator,
                        icon = Icons.Default.Warning
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Precisión de Decimales de Insulina
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.widget_decimals_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.widget_decimals_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = widgetInsulinDecimals == 1,
                                onClick = { viewModel.setWidgetInsulinDecimals(1) },
                                label = { Text(stringResource(R.string.widget_decimals_1)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = widgetInsulinDecimals == 2,
                                onClick = { viewModel.setWidgetInsulinDecimals(2) },
                                label = { Text(stringResource(R.string.widget_decimals_2)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mostrar Hora
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_show_last_update_title),
                        description = stringResource(R.string.widget_show_last_update_desc),
                        checked = widgetShowLastUpdate,
                        onCheckedChange = viewModel::setWidgetShowLastUpdate,
                        icon = Icons.Default.Notifications
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mostrar Insulina Rápida
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_show_rapid_title),
                        description = stringResource(R.string.widget_show_rapid_desc),
                        checked = widgetShowRapidInsulin,
                        onCheckedChange = viewModel::setWidgetShowRapidInsulin,
                        icon = Icons.Default.Star
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mostrar Insulina Lenta
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_show_slow_title),
                        description = stringResource(R.string.widget_show_slow_desc),
                        checked = widgetShowSlowInsulin,
                        onCheckedChange = viewModel::setWidgetShowSlowInsulin,
                        icon = Icons.Default.Star
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mostrar Factor de Sensibilidad (FS / ISF)
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_show_fs_title),
                        description = stringResource(R.string.widget_show_fs_desc),
                        checked = widgetShowFS,
                        onCheckedChange = viewModel::setWidgetShowFS,
                        icon = Icons.Default.Info
                    )
                }
            }

            // --- Info Acción al Tocar ---
            item(key = "widget_tap_action") {
                SettingsSection(title = stringResource(R.string.widget_tap_action_title)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.widget_tap_action_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.widget_tap_action_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun WidgetToggleOption(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun WidgetLivePreviewCard(
    compactMode: Boolean,
    showRapid: Boolean,
    showSlow: Boolean,
    showFS: Boolean,
    showLastUpdate: Boolean,
    opacityPercent: Int,
    useTargetColor: Boolean,
    decimalsMode: Int,
    measurement: GlucoseMeasurement? = null
) {
    val syncDateFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }
    val instant = remember(measurement) {
        measurement?.let { TimestampParser.parseMeasurementInstant(it) } ?: Instant.now()
    }
    val previewTimestampStr = remember(instant) {
        syncDateFormatter.format(instant)
    }

    val evalVal = measurement?.calibratedValue ?: measurement?.value ?: 110
    val rawVal = measurement?.value ?: 110
    val glucoseDisplayStr = if (measurement != null) {
        GlucoseProcessor.formatDualValue(rawVal, evalVal)
    } else {
        "110"
    }
    val trendSymbol = if (measurement != null) {
        GlucoseProcessor.getTrendArrowSymbol(measurement.trendArrow ?: 3)
    } else {
        "→"
    }

    // Background simulation container (simulates home screen wallpaper)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2B2B36))
            .border(1.dp, Color(0xFF3F3F4E), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Wallpaper texture pattern / text indicator
        Text(
            text = "HOME SCREEN WALLPAPER",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        )

        // Calculated simulated widget background color
        val alphaFloat = opacityPercent / 100f
        val baseColor = when {
            !useTargetColor -> Color(0xFF1E1E1E)
            evalVal < 70 -> Color(0xFFC83B34)
            evalVal > 180 -> Color(0xFFE89940)
            else -> Color(0xFF9DCA44)
        }
        val cardBgColor = baseColor.copy(alpha = alphaFloat)

        val formattedRapidVal = if (decimalsMode == 1) "1.5" else "1.52"
        val formattedSlowVal = if (decimalsMode == 1) "12.0" else "12.00"

        val rapidLabel = if (compactMode) stringResource(R.string.widget_rapid_compact_label) else stringResource(R.string.widget_rapid_label)
        val slowLabel = if (compactMode) stringResource(R.string.widget_slow_compact_label) else stringResource(R.string.widget_slow_label)

        val rapidText = if (compactMode) stringResource(R.string.widget_rapid_compact_text, rapidLabel, formattedRapidVal)
        else stringResource(R.string.widget_rapid_text, rapidLabel, formattedRapidVal)

        val slowText = if (compactMode) stringResource(R.string.widget_slow_compact_text, slowLabel, formattedSlowVal)
        else stringResource(R.string.widget_slow_text, slowLabel, formattedSlowVal)

        // Widget Card representation
        Box(
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cardBgColor)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glucose Value + Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = glucoseDisplayStr,
                        fontSize = if (compactMode) 28.sp else 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = trendSymbol,
                        fontSize = if (compactMode) 24.sp else 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81C784)
                    )
                }

                if (showLastUpdate) {
                    Text(
                        text = previewTimestampStr,
                        fontSize = if (compactMode) 10.sp else 11.sp,
                        color = Color(0xFFDDDDDD),
                        maxLines = 1
                    )
                }

                val showAnyInsulin = showRapid || showSlow || showFS
                if (showAnyInsulin) {
                    if (!compactMode) {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(vertical = 4.dp),
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.2f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showRapid) {
                            Text(
                                text = rapidText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF82B1FF)
                            )
                        }
                        if (showRapid && (showSlow || showFS)) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (showSlow) {
                            Text(
                                text = slowText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A80)
                            )
                        }
                        if (showSlow && showFS) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (showFS) {
                            Text(
                                text = "FS: 45",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC5E1A5)
                            )
                        }
                    }
                }
            }
        }
    }
}
