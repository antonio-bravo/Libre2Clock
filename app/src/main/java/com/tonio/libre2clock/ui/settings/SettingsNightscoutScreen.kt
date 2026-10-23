package com.tonio.libre2clock.ui.settings

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNightscoutScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val nightscoutEnabled by viewModel.nightscoutEnabled.collectAsStateWithLifecycle()
    val nightscoutUrl by viewModel.nightscoutUrl.collectAsStateWithLifecycle()
    val nightscoutApiSecret by viewModel.nightscoutApiSecret.collectAsStateWithLifecycle()
    val nightscoutUseCalibrated by viewModel.nightscoutUseCalibrated.collectAsStateWithLifecycle()
    val xdripBroadcastEnabled by viewModel.xdripBroadcastEnabled.collectAsStateWithLifecycle()
    val isTestingNightscout by viewModel.isTestingNightscout.collectAsStateWithLifecycle()

    var urlInput by remember(nightscoutUrl) { mutableStateOf(nightscoutUrl) }
    var secretInput by remember(nightscoutApiSecret) { mutableStateOf(nightscoutApiSecret) }
    var showSecret by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_nightscout_title)) },
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
            item(key = "nightscout_section") {
                SettingsSection(title = stringResource(R.string.nightscout_enable_title)) {
                    Text(
                        text = stringResource(R.string.settings_nightscout_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Activar Nightscout
                    NightscoutToggleOption(
                        title = stringResource(R.string.nightscout_enable_title),
                        description = stringResource(R.string.nightscout_enable_desc),
                        checked = nightscoutEnabled,
                        onCheckedChange = viewModel::setNightscoutEnabled,
                        icon = Icons.Default.Share
                    )

                    if (nightscoutEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // URL del Servidor
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                viewModel.setNightscoutUrl(it)
                            },
                            label = { Text(stringResource(R.string.nightscout_url_label)) },
                            placeholder = { Text(stringResource(R.string.nightscout_url_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // API Secret
                        OutlinedTextField(
                            value = secretInput,
                            onValueChange = {
                                secretInput = it
                                viewModel.setNightscoutApiSecret(it)
                            },
                            label = { Text(stringResource(R.string.nightscout_secret_label)) },
                            placeholder = { Text(stringResource(R.string.nightscout_secret_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) Icons.Default.Lock else Icons.Default.Lock,
                                        contentDescription = null
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Usar Valor Calibrado vs Original
                        NightscoutToggleOption(
                            title = stringResource(R.string.nightscout_use_calibrated_title),
                            description = stringResource(R.string.nightscout_use_calibrated_desc),
                            checked = nightscoutUseCalibrated,
                            onCheckedChange = viewModel::setNightscoutUseCalibrated,
                            icon = Icons.Default.Edit
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botón de Probar Conexión
                        OutlinedButton(
                            onClick = {
                                viewModel.testNightscoutConnection { success, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isTestingNightscout && urlInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingNightscout) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.nightscout_testing_button))
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.nightscout_test_button))
                            }
                        }
                    }
                }
            }

            // --- Transmisión Local xDrip+ / AndroidAPS ---
            item(key = "xdrip_section") {
                SettingsSection(title = stringResource(R.string.xdrip_broadcast_title)) {
                    NightscoutToggleOption(
                        title = stringResource(R.string.xdrip_broadcast_title),
                        description = stringResource(R.string.xdrip_broadcast_desc),
                        checked = xdripBroadcastEnabled,
                        onCheckedChange = viewModel::setXdripBroadcastEnabled,
                        icon = Icons.Default.Share
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun NightscoutToggleOption(
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
