package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCloudScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val firebaseUser by viewModel.firebaseUser.collectAsStateWithLifecycle()
    val isEnabled by viewModel.isCloudSyncEnabled.collectAsStateWithLifecycle()
    val lastSuccess by viewModel.cloudSyncLastSuccessAt.collectAsStateWithLifecycle()
    val settingsUpdated by viewModel.settingsUpdatedAt.collectAsStateWithLifecycle()
    val debugOutput by viewModel.cloudSyncDebugOutput.collectAsStateWithLifecycle()
    val isDebugLoading by viewModel.isCloudSyncDebugLoading.collectAsStateWithLifecycle()
    
    var showResetConfirm by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_cloud_sync_title)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.cloud_account_section)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = if (firebaseUser != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    if (firebaseUser != null) stringResource(R.string.cloud_connected_as) else stringResource(R.string.cloud_not_connected),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    firebaseUser?.email ?: stringResource(R.string.cloud_connect_hint),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (firebaseUser == null) {
                            Button(
                                onClick = { viewModel.signInWithGoogle(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.cloud_connect_button))
                            }
                        } else {
                            OutlinedButton(
                                onClick = viewModel::signOutFromGoogle,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.cloud_logout_button))
                            }
                        }
                    }
                }
            }

            if (firebaseUser != null) {
                item {
                    SettingsSection(title = stringResource(R.string.cloud_options_section)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cloud_auto_sync_label))
                                Text(
                                    stringResource(R.string.cloud_auto_sync_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = viewModel::updateCloudSyncEnabled
                            )
                        }
                        
                        Text(
                            text = settingsUpdated?.let { "Ajustes modificados: ${formatTimestamp(it)}" } ?: "Usando valores por defecto",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                lastSuccess?.let { stringResource(R.string.cloud_last_sync, formatTimestamp(it)) } ?: stringResource(R.string.cloud_sync_pending),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::triggerCloudSync) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.cloud_sync_now), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.cloud_diag_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::runCloudSyncDiagnostic,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isDebugLoading && firebaseUser != null
                        ) {
                            if (isDebugLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(stringResource(R.string.cloud_diag_run))
                            }
                        }

                        if (debugOutput != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.cloud_diag_log_title), style = MaterialTheme.typography.labelMedium)
                                        TextButton(onClick = viewModel::clearCloudSyncDebugOutput) {
                                            Text(stringResource(R.string.cloud_diag_close))
                                        }
                                    }
                                    Text(
                                        debugOutput!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Text(
                    stringResource(R.string.cloud_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item {
                if (firebaseUser != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    HorizontalDivider()
                    Text(
                        "Zona de Peligro",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Hard Reset de la Nube",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Borra todos los datos actuales de la nube y sube tu estado local como la nueva copia maestra. Útil para corregir duplicados tras cambios de versión.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Button(
                                onClick = { showResetConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isResetting
                            ) {
                                if (isResetting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                                } else {
                                    Text("Borrar Nube y Sincronizar Local")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("¿Confirmar Hard Reset?") },
            text = { Text("Se borrarán permanentemente los datos de la nube para este paciente y se sustituirán por los de este móvil. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        isResetting = true
                        viewModel.resetCloudData { success ->
                            isResetting = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) "Reseteo completado con éxito" else "Error al resetear la nube"
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("SÍ, BORRAR TODO")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(date)
}
