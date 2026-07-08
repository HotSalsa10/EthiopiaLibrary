package com.ethiopialibrary.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R

/**
 * Staff-PIN gate for overriding a blocked borrowing-limit checkout, so the
 * limit stays a real stop for anyone without the PIN but never a dead end
 * for staff who know it.
 */
@Composable
fun PinOverrideDialog(
    wrongPin: Boolean,
    onConfirm: (String) -> Unit,
    onPinChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error_limit_reached)) },
        text = {
            Column {
                Text(stringResource(R.string.override_limit_prompt))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(6)
                        onPinChanged()
                    },
                    label = { Text(stringResource(R.string.enter_pin)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = wrongPin,
                )
                if (wrongPin) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.wrong_pin), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length in 4..6, onClick = { onConfirm(pin) }) {
                Text(stringResource(R.string.override_limit_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
