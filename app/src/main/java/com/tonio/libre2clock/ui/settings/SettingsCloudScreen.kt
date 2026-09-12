package com.tonio.libre2clock.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val context = LocalContext.current

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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "account_section") {
                AccountSection(
                    firebaseUser = firebaseUser,
                    onSignIn = { viewModel.signInWithGoogle(context) },
                    onSignOut = viewModel::signOutFromGoogle
                )
            }

            if (firebaseUser != null) {
                item(key = "sync_options_section") {
                    val successMsg = stringResource(R.string.cloud_pull_settings_success)
                    val errorMsg = stringResource(R.string.cloud_pull_settings_error)
                    
                    SyncOptionsSection(
                        isEnabled = isEnabled,
                        lastSuccess = lastSuccess,
                        settingsUpdated = settingsUpdated,
                        onToggleSync = viewModel::updateCloudSyncEnabled,
                        onSyncNow = viewModel::triggerCloudSync,
                        onPullSettings = {
                            viewModel.pullCloudSettings { success ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(if (success) successMsg else errorMsg)
                                }
                            }
                        }
                    )
                }
            }

            item(key = "diagnostic_section") {
                DiagnosticSection(
                    debugOutput = debugOutput,
                    isDebugLoading = isDebugLoading,
                    isSignedIn = firebaseUser != null,
                    onRunDiagnostic = viewModel::runCloudSyncDiagnostic,
                    onClearOutput = viewModel::clearCloudSyncDebugOutput
                )
            }

            item(key = "privacy_note") {
                Text(
                    stringResource(R.string.cloud_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (firebaseUser != null) {
                item(key = "danger_zone") {
                    DangerZoneSection(
                        isResetting = isResetting,
                        onResetClick = { showResetConfirm = true }
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        val successMsg = stringResource(R.string.cloud_hard_reset_success)
        val errorMsg = stringResource(R.string.cloud_hard_reset_error)
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.cloud_hard_reset_confirm_title)) },
            text = { Text(stringResource(R.string.cloud_hard_reset_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        isResetting = true
                        viewModel.resetCloudData { success ->
                            isResetting = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) successMsg else errorMsg
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.cloud_hard_reset_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.event_log_close))
                }
            }
        )
    }
}

// --- Componentes Extraídos para Aislar Recomposiciones ---

@Composable
private fun AccountSection(
    firebaseUser: com.google.firebase.auth.FirebaseUser?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
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
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (firebaseUser == null) {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cloud_connect_button))
                }
            } else {
                OutlinedButton(
                    onClick = onSignOut,
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

@Composable
private fun SyncOptionsSection(
    isEnabled: Boolean,
    lastSuccess: Long?,
    settingsUpdated: Long?,
    onToggleSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onPullSettings: () -> Unit
) {
    // OPTIMIZACIÓN: Memoizar el formateo de fechas para evitar recálculos en cada recomposición
    val formattedSettingsUpdated = remember(settingsUpdated) { settingsUpdated?.let { formatTimestamp(it) } }
    val formattedLastSuccess = remember(lastSuccess) { lastSuccess?.let { formatTimestamp(it) } }

    SettingsSection(title = stringResource(R.string.cloud_options_section)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    stringResource(R.string.cloud_auto_sync_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.cloud_auto_sync_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isEnabled, onCheckedChange = onToggleSync)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ✅ CORRECCIÓN: Usar ?.let para garantizar que 'it' sea String (no nulo) 
        // y pasarlo directamente como argumento vararg a stringResource
        Text(
            text = formattedSettingsUpdated?.let { 
                stringResource(R.string.cloud_settings_modified_at, it) 
            } ?: stringResource(R.string.cloud_settings_default),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✅ CORRECCIÓN: Misma lógica segura para el último éxito de sincronización
                Text(
                    text = formattedLastSuccess?.let { 
                        stringResource(R.string.cloud_last_sync, it) 
                    } ?: stringResource(R.string.cloud_sync_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                TextButton(onClick = onSyncNow) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.cloud_sync_now), style = MaterialTheme.typography.labelMedium)
                }
            }
            
            OutlinedButton(
                onClick = onPullSettings,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_pull_settings))
            }
        }
    }
}

@Composable
private fun DiagnosticSection(
    debugOutput: String?,
    isDebugLoading: Boolean,
    isSignedIn: Boolean,
    onRunDiagnostic: () -> Unit,
    onClearOutput: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.cloud_diag_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRunDiagnostic,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDebugLoading && isSignedIn
            ) {
                if (isDebugLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
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
                            Text(
                                stringResource(R.string.cloud_diag_log_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onClearOutput) {
                                Text(stringResource(R.string.cloud_diag_close))
                            }
                        }
                        
                        // OPTIMIZACIÓN: Scroll interno y altura máxima para logs grandes, evitando bloqueos de UI
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                                .padding(top = 8.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = debugOutput.trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DangerZoneSection(
    isResetting: Boolean,
    onResetClick: () -> Unit
) {
    Column {
        // OPTIMIZACIÓN: Eliminado el Spacer superior redundante, ya que LazyColumn usa spacedBy(16.dp)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.cloud_danger_zone),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.cloud_hard_reset_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(R.string.cloud_hard_reset_desc),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onResetClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isResetting
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text(stringResource(R.string.cloud_hard_reset_button))
                    }
                }
            }
        }
    }
}

// OPTIMIZACIÓN: DateTimeFormatter es thread-safe por diseño, eliminando la necesidad de 'synchronized'
private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private fun formatTimestamp(timestamp: Long): String {
    return timestampFormatter.format(Instant.ofEpochMilli(timestamp))
}