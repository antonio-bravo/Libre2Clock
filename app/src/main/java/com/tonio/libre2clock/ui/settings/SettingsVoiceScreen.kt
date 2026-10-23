package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsVoiceScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val ttsAnnounceOnTap by viewModel.ttsAnnounceOnTap.collectAsStateWithLifecycle()
    val ttsAnnouncePeriodic by viewModel.ttsAnnouncePeriodic.collectAsStateWithLifecycle()
    val ttsAnnounceOnAlert by viewModel.ttsAnnounceOnAlert.collectAsStateWithLifecycle()
    val ttsFormatDetailed by viewModel.ttsFormatDetailed.collectAsStateWithLifecycle()
    val ttsPeriodicIntervalMins by viewModel.ttsPeriodicIntervalMins.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_voice_title)) },
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
            item(key = "voice_settings_section") {
                SettingsSection(title = stringResource(R.string.settings_voice_title)) {
                    Text(
                        text = stringResource(R.string.settings_voice_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Anunciar al tocar la glucosa
                    VoiceToggleOption(
                        title = stringResource(R.string.tts_announce_on_tap_title),
                        description = stringResource(R.string.tts_announce_on_tap_desc),
                        checked = ttsAnnounceOnTap,
                        onCheckedChange = viewModel::setTtsAnnounceOnTap,
                        icon = Icons.Default.Notifications
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Anunciar en Alertas / Cambios Rápidos
                    VoiceToggleOption(
                        title = stringResource(R.string.tts_announce_alert_title),
                        description = stringResource(R.string.tts_announce_alert_desc),
                        checked = ttsAnnounceOnAlert,
                        onCheckedChange = viewModel::setTtsAnnounceOnAlert,
                        icon = Icons.Default.Notifications
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Formato Detallado con Insulina IOB
                    VoiceToggleOption(
                        title = stringResource(R.string.tts_format_detailed_title),
                        description = stringResource(R.string.tts_format_detailed_desc),
                        checked = ttsFormatDetailed,
                        onCheckedChange = viewModel::setTtsFormatDetailed,
                        icon = Icons.Default.Info
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Lectura Periódica en Segundo Plano
                    VoiceToggleOption(
                        title = stringResource(R.string.tts_announce_periodic_title),
                        description = stringResource(R.string.tts_announce_periodic_desc),
                        checked = ttsAnnouncePeriodic,
                        onCheckedChange = viewModel::setTtsAnnouncePeriodic,
                        icon = Icons.Default.Settings
                    )

                    if (ttsAnnouncePeriodic) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(modifier = Modifier.padding(start = 28.dp)) {
                            Text(
                                text = stringResource(R.string.tts_interval_label, ttsPeriodicIntervalMins),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = ttsPeriodicIntervalMins.toFloat(),
                                onValueChange = { viewModel.setTtsPeriodicIntervalMins(it.toInt()) },
                                valueRange = 5f..60f,
                                steps = 10,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // --- Test Button ---
            item(key = "voice_test_button") {
                Button(
                    onClick = { viewModel.testVoiceAnnouncement() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tts_test_button))
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun VoiceToggleOption(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
