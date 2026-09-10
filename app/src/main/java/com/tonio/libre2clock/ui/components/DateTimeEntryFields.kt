package com.tonio.libre2clock.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Date + hour/minute entry: a tappable date button (opens a calendar picker) plus two
 * numeric boxes for hour/minute that auto-advance focus, instead of a single free-text field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateHourMinuteInput(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    hour: String,
    onHourChange: (String) -> Unit,
    minute: String,
    onMinuteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    dateLabel: String? = null
) {
    // OPTIMIZACIÓN 1: Simplificación del estado (evita variable intermedia innecesaria)
    var showDatePicker by remember { mutableStateOf(false) }
    
    // OPTIMIZACIÓN 2: Formateador de fecha localizado (ej: "24 oct 2023" en lugar de "2023-10-24")
    // Se usa 'remember' para evitar crear una nueva instancia en cada recomposición.
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val zoneId = remember { ZoneId.systemDefault() }

    Column(modifier = modifier) {
        OutlinedButton(
            onClick = { showDatePicker = true }, 
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.CalendarMonth, 
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = dateLabel ?: date.format(dateFormatter))
        }

        Spacer(modifier = Modifier.height(8.dp))

        HourMinuteInputFields(
            hour = hour,
            onHourChange = onHourChange,
            minute = minute,
            onMinuteChange = onMinuteChange,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDatePicker) {
        // OPTIMIZACIÓN 3: Corrección crítica de Zona Horaria.
        // Usar ZoneOffset.UTC causaba que la fecha se desplazara al día anterior 
        // en zonas horarias negativas (ej: América). Ahora usa la zona del sistema.
        val initialMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
                        onDateChange(selectedDate)
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
            DatePicker(state = pickerState)
        }
    }
}