package com.tonio.libre2clock.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.EmergencyContact
import com.tonio.libre2clock.util.EmergencyLocationManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEmergencyScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val emergencyConfig by viewModel.emergencyConfig.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val locationManager = remember(context) { EmergencyLocationManager(context) }

    val hasSmsPermission = remember(context, emergencyConfig) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val smsGranted = permissions[Manifest.permission.SEND_SMS] ?: false

        if (!fineGranted && !coarseGranted && emergencyConfig.includeLocation) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.emergency_permission_denied_warning))
            }
        }
        if (!smsGranted && emergencyConfig.contacts.any { it.sendViaSms }) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.emergency_sms_permission_warning))
            }
        }
    }

    LaunchedEffect(emergencyConfig.includeLocation, emergencyConfig.contacts) {
        val permissionsToRequest = mutableListOf<String>()
        if (emergencyConfig.includeLocation && !locationManager.hasLocationPermission()) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (emergencyConfig.contacts.any { it.sendViaSms } && !hasSmsPermission) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    var showContactDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var isTestingAlert by remember { mutableStateOf(false) }
    var showTelegramHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.emergency_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "enable_switch") {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (emergencyConfig.enabled) 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) 
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.emergency_enable_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.emergency_enable_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = emergencyConfig.enabled,
                            onCheckedChange = { viewModel.setEmergencyAlertsEnabled(it) }
                        )
                    }
                }
            }

            item(key = "threshold_settings") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.emergency_params_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Selector de tipo de valor de glucosa (Calibrada vs Raw)
                        Text(
                            text = stringResource(R.string.emergency_glucose_measurement_type),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = emergencyConfig.useCalibratedValue,
                                onClick = { viewModel.setEmergencyUseCalibratedValue(true) },
                                label = { Text(stringResource(R.string.emergency_glucose_calibrated)) }
                            )
                            FilterChip(
                                selected = !emergencyConfig.useCalibratedValue,
                                onClick = { viewModel.setEmergencyUseCalibratedValue(false) },
                                label = { Text(stringResource(R.string.emergency_glucose_raw)) }
                            )
                        }

                        HorizontalDivider()

                        // Umbral de Glucosa
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.emergency_threshold_label))
                            Text(
                                "${emergencyConfig.thresholdMgDl} mg/dL",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Slider(
                            value = emergencyConfig.thresholdMgDl.toFloat(),
                            onValueChange = { viewModel.setEmergencyGlucoseThreshold(it.toInt()) },
                            valueRange = 40f..80f,
                            steps = 7
                        )

                        HorizontalDivider()

                        // Tiempo de espera (Cooldown)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.emergency_cooldown_label))
                            Text("${emergencyConfig.cooldownMinutes} min", fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = emergencyConfig.cooldownMinutes.toFloat(),
                            onValueChange = { viewModel.setEmergencyCooldownMinutes(it.toInt()) },
                            valueRange = 5f..60f,
                            steps = 10
                        )

                        HorizontalDivider()

                        // Franja Horaria de Actividad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.emergency_schedule_title),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.emergency_schedule_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = emergencyConfig.useSchedule,
                                onCheckedChange = { viewModel.setEmergencyUseSchedule(it) }
                            )
                        }

                        if (emergencyConfig.useSchedule) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = emergencyConfig.startTime,
                                    onValueChange = { viewModel.setEmergencyStartTime(it) },
                                    label = { Text(stringResource(R.string.emergency_start_time_label)) },
                                    placeholder = { Text("00:00") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = emergencyConfig.endTime,
                                    onValueChange = { viewModel.setEmergencyEndTime(it) },
                                    label = { Text(stringResource(R.string.emergency_end_time_label)) },
                                    placeholder = { Text("23:59") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }

                        HorizontalDivider()

                        // Switch GPS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.emergency_include_gps_title),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.emergency_include_gps_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = emergencyConfig.includeLocation,
                                onCheckedChange = { viewModel.setEmergencyIncludeLocation(it) }
                            )
                        }
                    }
                }
            }

            item(key = "telegram_bot_token") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.emergency_telegram_token_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { showTelegramHelpDialog = true }) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = stringResource(R.string.emergency_telegram_help_title),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.emergency_telegram_token_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = emergencyConfig.telegramBotToken,
                            onValueChange = { viewModel.setEmergencyTelegramBotToken(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ej: 123456789:ABCdefGHIjklMNOpqrsTUVwxyZ") },
                            singleLine = true
                        )
                        TextButton(
                            onClick = { showTelegramHelpDialog = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.emergency_telegram_help_button))
                        }
                    }
                }
            }

            item(key = "custom_webhook_url") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.emergency_webhook_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = stringResource(R.string.emergency_webhook_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = emergencyConfig.customWebhookUrl,
                            onValueChange = { viewModel.setEmergencyCustomWebhookUrl(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://wasenderapi.com/... ó tu webhook") },
                            singleLine = true
                        )
                    }
                }
            }

            if (emergencyConfig.contacts.any { it.sendViaSms } && !hasSmsPermission) {
                item(key = "sms_permission_warning") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.emergency_sms_permission_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(R.string.emergency_sms_permission_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS)) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.emergency_grant_sms_permission))
                            }
                        }
                    }
                }
            }

            item(key = "contacts_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.emergency_contacts_title, emergencyConfig.contacts.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            editingContact = EmergencyContact(name = "")
                            showContactDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.emergency_add_contact))
                    }
                }
            }

            if (emergencyConfig.contacts.isEmpty()) {
                item(key = "empty_contacts") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.emergency_empty_contacts),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(emergencyConfig.contacts, key = { it.id }) { contact ->
                    EmergencyContactCard(
                        contact = contact,
                        onEdit = {
                            editingContact = contact
                            showContactDialog = true
                        },
                        onDelete = { viewModel.deleteEmergencyContact(contact.id) }
                    )
                }
            }

            item(key = "test_button") {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isTestingAlert = true
                            viewModel.testEmergencyAlert(EmergencyLocationTestMode.FULL_PIPELINE) { resultMsg ->
                                isTestingAlert = false
                                scope.launch { snackbarHostState.showSnackbar(resultMsg) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isTestingAlert,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isTestingAlert) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.emergency_testing_button))
                        } else {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.emergency_test_full_button))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isTestingAlert = true
                                viewModel.testEmergencyAlert(EmergencyLocationTestMode.CELLULAR_ONLY) { resultMsg ->
                                    isTestingAlert = false
                                    scope.launch { snackbarHostState.showSnackbar(resultMsg) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isTestingAlert
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.emergency_test_cell_button), style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingAlert = true
                                viewModel.testEmergencyAlert(EmergencyLocationTestMode.WIFI_ONLY) { resultMsg ->
                                    isTestingAlert = false
                                    scope.launch { snackbarHostState.showSnackbar(resultMsg) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isTestingAlert
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.emergency_test_wifi_button), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showContactDialog && editingContact != null) {
        EmergencyContactDialog(
            contact = editingContact!!,
            onDismiss = { showContactDialog = false },
            onSave = { updatedContact ->
                viewModel.saveEmergencyContact(updatedContact)
                showContactDialog = false
            }
        )
    }

    if (showTelegramHelpDialog) {
        AlertDialog(
            onDismissRequest = { showTelegramHelpDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
            title = { Text(stringResource(R.string.emergency_telegram_help_title)) },
            text = {
                Text(
                    text = stringResource(R.string.emergency_telegram_help_content),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { showTelegramHelpDialog = false }) {
                    Text(stringResource(R.string.emergency_btn_close))
                }
            }
        )
    }
}

@Composable
fun EmergencyContactCard(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (contact.phoneNumber.isNotBlank()) {
                    Text(
                        text = "Tel: ${contact.phoneNumber}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (contact.telegramChatId.isNotBlank()) {
                    Text(
                        text = "Telegram Chat ID: ${contact.telegramChatId}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (contact.sendViaWhatsApp) {
                        AssistChip(
                            onClick = {},
                            label = { Text("WhatsApp", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (contact.sendViaTelegram) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Telegram", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (contact.sendViaSms) {
                        AssistChip(
                            onClick = {},
                            label = { Text("SMS", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.emergency_btn_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.emergency_btn_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun EmergencyContactDialog(
    contact: EmergencyContact,
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit
) {
    var name by remember { mutableStateOf(contact.name) }
    var phone by remember { mutableStateOf(contact.phoneNumber) }
    var telegramChatId by remember { mutableStateOf(contact.telegramChatId) }
    var whatsAppApiKey by remember { mutableStateOf(contact.whatsAppApiKey) }
    var whatsAppGroupId by remember { mutableStateOf(contact.whatsAppGroupId) }
    var sendViaWhatsApp by remember { mutableStateOf(contact.sendViaWhatsApp) }
    var sendViaTelegram by remember { mutableStateOf(contact.sendViaTelegram) }
    var sendViaSms by remember { mutableStateOf(contact.sendViaSms) }
    var showWhatsAppHelpDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (contact.name.isBlank()) stringResource(R.string.emergency_dialog_add_title)
                else stringResource(R.string.emergency_dialog_edit_title)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.emergency_contact_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.emergency_contact_phone_label)) },
                    placeholder = { Text("Ej: +34612345678") },
                    supportingText = { Text(stringResource(R.string.emergency_contact_phone_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (sendViaWhatsApp) {
                    OutlinedTextField(
                        value = whatsAppApiKey,
                        onValueChange = { whatsAppApiKey = it },
                        label = { Text(stringResource(R.string.emergency_contact_whatsapp_apikey_label)) },
                        placeholder = { Text("Ej: 123456") },
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.emergency_contact_whatsapp_apikey_hint),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { showWhatsAppHelpDialog = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("¿Cómo obtener?", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = whatsAppGroupId,
                        onValueChange = { whatsAppGroupId = it },
                        label = { Text(stringResource(R.string.emergency_contact_whatsapp_group_id_label)) },
                        placeholder = { Text("Ej: 120363012345678901@g.us") },
                        supportingText = { Text(stringResource(R.string.emergency_contact_whatsapp_group_id_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = telegramChatId,
                    onValueChange = { telegramChatId = it },
                    label = { Text(stringResource(R.string.emergency_contact_telegram_label)) },
                    placeholder = { Text("Ej: 987654321") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.emergency_contact_channels),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sendViaWhatsApp, onCheckedChange = { sendViaWhatsApp = it })
                    Text(stringResource(R.string.emergency_channel_whatsapp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sendViaTelegram, onCheckedChange = { sendViaTelegram = it })
                    Text(stringResource(R.string.emergency_channel_telegram))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sendViaSms, onCheckedChange = { sendViaSms = it })
                    Text(stringResource(R.string.emergency_channel_sms))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        contact.copy(
                            name = name.trim(),
                            phoneNumber = phone.trim(),
                            telegramChatId = telegramChatId.trim(),
                            whatsAppApiKey = whatsAppApiKey.trim(),
                            whatsAppGroupId = whatsAppGroupId.trim(),
                            sendViaWhatsApp = sendViaWhatsApp,
                            sendViaTelegram = sendViaTelegram,
                            sendViaSms = sendViaSms
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.emergency_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.emergency_btn_cancel))
            }
        }
    )

    if (showWhatsAppHelpDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsAppHelpDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.emergency_whatsapp_help_title)) },
            text = {
                Text(
                    text = stringResource(R.string.emergency_whatsapp_help_content),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { showWhatsAppHelpDialog = false }) {
                    Text(stringResource(R.string.emergency_btn_close))
                }
            }
        )
    }
}
