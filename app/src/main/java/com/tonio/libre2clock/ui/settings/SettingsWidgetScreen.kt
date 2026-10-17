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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsWidgetScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val widgetCompactMode by viewModel.widgetCompactMode.collectAsStateWithLifecycle()
    val widgetShowInsulin by viewModel.widgetShowInsulin.collectAsStateWithLifecycle()
    val widgetShowLastUpdate by viewModel.widgetShowLastUpdate.collectAsStateWithLifecycle()
    val widgetBackgroundOpacity by viewModel.widgetBackgroundOpacity.collectAsStateWithLifecycle()
    val widgetUseTargetColorBg by viewModel.widgetUseTargetColorBg.collectAsStateWithLifecycle()

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
                        showInsulin = widgetShowInsulin,
                        showLastUpdate = widgetShowLastUpdate,
                        opacityPercent = widgetBackgroundOpacity,
                        useTargetColor = widgetUseTargetColorBg
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

                    // Mostrar Insulina Activa
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_show_insulin_title),
                        description = stringResource(R.string.widget_show_insulin_desc),
                        checked = widgetShowInsulin,
                        onCheckedChange = viewModel::setWidgetShowInsulin,
                        icon = Icons.Default.Star,
                        enabled = !widgetCompactMode
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mostrar Hora
                    WidgetToggleOption(
                        title = stringResource(R.string.widget_show_last_update_title),
                        description = stringResource(R.string.widget_show_last_update_desc),
                        checked = widgetShowLastUpdate,
                        onCheckedChange = viewModel::setWidgetShowLastUpdate,
                        icon = Icons.Default.Notifications,
                        enabled = !widgetCompactMode
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
    showInsulin: Boolean,
    showLastUpdate: Boolean,
    opacityPercent: Int,
    useTargetColor: Boolean
) {
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
        val baseColor = if (useTargetColor) Color(0xFF9DCA44) else Color(0xFF1E1E1E)
        val cardBgColor = baseColor.copy(alpha = alphaFloat)

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
                        text = "110",
                        fontSize = if (compactMode) 28.sp else 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "→",
                        fontSize = if (compactMode) 24.sp else 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81C784)
                    )
                }

                if (!compactMode && showLastUpdate) {
                    Text(
                        text = "12:34",
                        fontSize = 11.sp,
                        color = Color(0xFFDDDDDD)
                    )
                }

                if (!compactMode && showInsulin) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rápida: 1.5 U",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF82B1FF)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Lenta: 12.0 U",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8A80)
                        )
                    }
                }
            }
        }
    }
}
