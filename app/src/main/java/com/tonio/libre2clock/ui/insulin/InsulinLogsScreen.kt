package com.tonio.libre2clock.ui.insulin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinLogsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val doses by viewModel.insulinDoses.collectAsStateWithLifecycle()
    val rapidDurationMins by viewModel.rapidDurationMins.collectAsStateWithLifecycle()
    val slowDurationMins by viewModel.slowDurationMins.collectAsStateWithLifecycle()

    var editingDose by remember { mutableStateOf<InsulinDose?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_insulin_logs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (doses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(text = stringResource(R.string.insulin_no_doses))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                items(doses) { dose ->
                    DoseItem(
                        dose = dose,
                        onDelete = { viewModel.removeInsulinDose(dose) },
                        onEdit = { editingDose = dose }
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    editingDose?.let { dose ->
        InsulinDoseDialog(
            initialDose = dose,
            rapidDuration = rapidDurationMins,
            slowDuration = slowDurationMins,
            onDismiss = { editingDose = null },
            onConfirm = { newDose ->
                viewModel.updateInsulinDose(dose, newDose)
                editingDose = null
            }
        )
    }
}
