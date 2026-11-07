package com.tonio.libre2clock.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.FollowedPatient
import com.tonio.libre2clock.data.model.FollowerInfo
import com.tonio.libre2clock.data.model.ShareInvite
import com.tonio.libre2clock.ui.icons.ContentCopy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    onDismissRequest: () -> Unit,
    followersList: List<FollowerInfo>,
    followedPatients: List<FollowedPatient>,
    activePatientUid: String?,
    onGenerateInvite: (String, Long?, (Result<ShareInvite>) -> Unit) -> Unit,
    onRedeemInvite: (String, String?, (Result<FollowedPatient>) -> Unit) -> Unit,
    onRevokeFollower: (String, (Result<Unit>) -> Unit) -> Unit,
    onSelectPatient: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.share_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(android.R.string.cancel))
                    }
                }

                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.share_tab_generate)) },
                        icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.share_tab_join)) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (selectedTab == 0) {
                    // TAB 0: GENERAR CÓDIGO (PACIENTE)
                    var labelInput by remember { mutableStateOf("") }
                    var selectedExpiryType by remember { mutableIntStateOf(0) } // 0: No limit, 1: 7d, 2: 30d, 3: Custom
                    var customExpiryMs by remember { mutableStateOf<Long?>(null) }
                    var showDatePicker by remember { mutableStateOf(false) }
                    val datePickerState = rememberDatePickerState()

                    var generatedInvite by remember { mutableStateOf<ShareInvite?>(null) }
                    var isGenerating by remember { mutableStateOf(false) }

                    val targetExpiresAtMs: Long? = when (selectedExpiryType) {
                        0 -> null
                        1 -> customExpiryMs ?: (System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
                        else -> null
                    }

                    if (showDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    val sel = datePickerState.selectedDateMillis
                                    if (sel != null) {
                                        customExpiryMs = sel
                                        selectedExpiryType = 1
                                    }
                                    showDatePicker = false
                                }) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }

                    Text(
                        stringResource(R.string.share_generate_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text(stringResource(R.string.share_follower_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(stringResource(R.string.share_expiry_label), style = MaterialTheme.typography.labelMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedExpiryType == 0,
                            onClick = { selectedExpiryType = 0; customExpiryMs = null },
                            label = { Text(stringResource(R.string.share_no_limit)) }
                        )

                        val customChipText = if (selectedExpiryType == 1 && customExpiryMs != null) {
                            val formatted = Instant.ofEpochMilli(customExpiryMs!!).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            stringResource(R.string.share_until_date, formatted)
                        } else {
                            stringResource(R.string.share_custom_date)
                        }

                        FilterChip(
                            selected = selectedExpiryType == 1,
                            onClick = { showDatePicker = true },
                            label = { Text(customChipText) },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    Button(
                        onClick = {
                            if (labelInput.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.share_follower_label), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isGenerating = true
                            onGenerateInvite(labelInput, targetExpiresAtMs) { result ->
                                isGenerating = false
                                result.fold(
                                    onSuccess = { invite ->
                                        generatedInvite = invite
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, err.message ?: "Error", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.share_generate_button))
                        }
                    }

                    generatedInvite?.let { invite ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.share_code_for, invite.label),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                SelectionContainer {
                                    Text(
                                        invite.code,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Button(onClick = {
                                    clipboardManager.setText(AnnotatedString(invite.code))
                                    Toast.makeText(context, context.getString(R.string.share_copy_success), Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.share_copy_button))
                                }
                            }
                        }
                    }

                    if (followersList.isNotEmpty()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.share_authorized_followers), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        followersList.forEach { follower ->
                            val expirySubtitle = if (follower.expiresAtMs != null) {
                                val dateStr = Instant.ofEpochMilli(follower.expiresAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                stringResource(R.string.share_expires_on, dateStr)
                            } else {
                                stringResource(R.string.share_unlimited_access)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(follower.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(expirySubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(
                                    onClick = {
                                        onRevokeFollower(follower.followerUid) { res ->
                                            res.fold(
                                                onSuccess = {},
                                                onFailure = { err -> Toast.makeText(context, err.message ?: "Error", Toast.LENGTH_SHORT).show() }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.share_revoke_button))
                                }
                            }
                        }
                    }

                } else {
                    // TAB 1: UNIRSE COMO SEGUIDOR
                    var codeInput by remember { mutableStateOf("") }
                    var customLabelInput by remember { mutableStateOf("") }
                    var isRedeeming by remember { mutableStateOf(false) }

                    Text(
                        stringResource(R.string.share_join_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase() },
                        label = { Text(stringResource(R.string.share_code_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customLabelInput,
                        onValueChange = { customLabelInput = it },
                        label = { Text(stringResource(R.string.share_patient_label_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (codeInput.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.share_code_hint), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isRedeeming = true
                            onRedeemInvite(codeInput, customLabelInput) { result ->
                                isRedeeming = false
                                result.fold(
                                    onSuccess = { _ ->
                                        codeInput = ""
                                        customLabelInput = ""
                                        onDismissRequest()
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, err.message ?: "Error", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRedeeming
                    ) {
                        if (isRedeeming) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.share_connect_button))
                        }
                    }

                    if (followedPatients.isNotEmpty()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.share_followed_patients), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        followedPatients.forEach { patient ->
                            val isSelected = patient.patientUid == activePatientUid
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(patient.label, fontWeight = FontWeight.Bold)
                                    if (!isSelected) {
                                        TextButton(onClick = { onSelectPatient(patient.patientUid) }) {
                                            Text(stringResource(R.string.share_change_patient))
                                        }
                                    } else {
                                        Text(stringResource(R.string.share_viewing_active), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
