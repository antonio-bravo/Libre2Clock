package com.tonio.libre2clock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Two numeric boxes for hour/minute that auto-advance focus, validate ranges intelligently,
 * and auto-pad single digits with a leading zero for better UX.
        */
@Composable
fun HourMinuteInputFields(
    hour: String,
    onHourChange: (String) -> Unit,
    minute: String,
    onMinuteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val minuteFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = hour,
            onValueChange = { input ->
                // 1. Filtrar solo dígitos y limitar a 2 caracteres
                val digits = input.filter { it.isDigit() }.take(2)
                
                // 2. Validación inteligente: Si son 2 dígitos y > 23, descartamos el último dígito 
                // (ej: "25" se convierte en "2", no en "23" que es confuso).
                val validHour = if (digits.length == 2 && (digits.toIntOrNull() ?: 0) > 23) {
                    digits.dropLast(1)
                } else {
                    digits
                }
                
                onHourChange(validHour)
                
                // 3. Auto-avance al completar 2 dígitos válidos
                if (validHour.length == 2) {
                    minuteFocusRequester.requestFocus()
                }
            },
            label = { Text("HH") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    // 4. UX: Auto-relleno con cero a la izquierda si el usuario pulsa "Siguiente" con 1 dígito
                    if (hour.length == 1) onHourChange("0$hour")
                    minuteFocusRequester.requestFocus()
                }
            ),
            modifier = Modifier.width(80.dp)
        )
        
        Text(
            text = ":", 
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.width(8.dp) // Ancho fijo para evitar saltos de layout
        )
        
        OutlinedTextField(
            value = minute,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(2)
                
                // Validación inteligente para minutos (máximo 59)
                val validMinute = if (digits.length == 2 && (digits.toIntOrNull() ?: 0) > 59) {
                    digits.dropLast(1)
                } else {
                    digits
                }
                
                onMinuteChange(validMinute)
            },
            label = { Text("MM") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    // UX: Auto-relleno con cero a la izquierda al finalizar
                    if (minute.length == 1) onMinuteChange("0$minute")
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .width(80.dp)
                .focusRequester(minuteFocusRequester)
        )
    }
}