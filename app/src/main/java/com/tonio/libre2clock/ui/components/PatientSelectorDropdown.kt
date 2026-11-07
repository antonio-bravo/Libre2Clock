package com.tonio.libre2clock.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.FollowedPatient

@Composable
fun PatientSelectorDropdown(
    followedPatients: List<FollowedPatient>,
    activePatientUid: String?,
    onSelectPatient: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (followedPatients.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val currentPatient = followedPatients.firstOrNull { it.patientUid == activePatientUid } ?: followedPatients.first()

    Box(modifier = modifier) {
        Surface(
            onClick = { if (followedPatients.size > 1) expanded = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = stringResource(R.string.share_viewing_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = currentPatient.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (followedPatients.size > 1) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.share_change_patient),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            followedPatients.forEach { patient ->
                val isSelected = patient.patientUid == currentPatient.patientUid
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = patient.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Text("(Activo)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectPatient(patient.patientUid)
                    }
                )
            }
        }
    }
}
