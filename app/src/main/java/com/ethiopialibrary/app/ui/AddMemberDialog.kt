package com.ethiopialibrary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_full_name)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.field_phone)) }, singleLine = true)
                OutlinedTextField(nationalId, { nationalId = it }, label = { Text(stringResource(R.string.field_national_id)) }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.field_address)) }, singleLine = false, minLines = 2)
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
}
