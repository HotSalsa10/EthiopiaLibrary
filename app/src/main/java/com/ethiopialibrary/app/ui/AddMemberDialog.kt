package com.ethiopialibrary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R

/** Shared with checkout's quick-add-on-MEMBER_NOT_FOUND flow, so both call sites stay in sync. */
@Composable
fun AddMemberDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String?, nationalId: String?, address: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val idFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(stringResource(R.string.field_full_name)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(nameFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { phoneFocus.requestFocus() }),
                )
                OutlinedTextField(
                    phone, { phone = it },
                    label = { Text(stringResource(R.string.field_phone)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(phoneFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { idFocus.requestFocus() }),
                )
                OutlinedTextField(
                    nationalId, { nationalId = it },
                    label = { Text(stringResource(R.string.field_national_id)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(idFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { addressFocus.requestFocus() }),
                )
                OutlinedTextField(
                    address, { address = it },
                    label = { Text(stringResource(R.string.field_address)) },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.focusRequester(addressFocus),
                    // Multi-line field: Next doesn't apply the same way - Done is fine,
                    // and this is the dialog's last field either way.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        name.trim(),
                        phone.trim().ifBlank { null },
                        nationalId.trim().ifBlank { null },
                        address.trim().ifBlank { null },
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
}
