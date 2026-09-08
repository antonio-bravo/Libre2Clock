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
 * Two numeric boxes for hour/minute that auto-advance focus and validate ranges.
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
                val digits = input.filter(Char::isDigit).take(2)
                val clamped = digits.toIntOrNull()?.coerceIn(0, 23)?.toString() ?: digits
                onHourChange(clamped)
                if (clamped.length == 2) {
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
                onNext = { minuteFocusRequester.requestFocus() }
            ),
            modifier = Modifier.width(80.dp)
        )
        Text(":", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = minute,
            onValueChange = { input ->
                val digits = input.filter(Char::isDigit).take(2)
                val clamped = digits.toIntOrNull()?.coerceIn(0, 59)?.toString() ?: digits
                onMinuteChange(clamped)
            },
            label = { Text("MM") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier
                .width(80.dp)
                .focusRequester(minuteFocusRequester)
        )
    }
}
