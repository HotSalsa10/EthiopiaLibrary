package com.ethiopialibrary.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R

/** Scan-or-type entry used by checkout and return: camera first, keyboard always available. */
@Composable
fun CodeEntry(hint: String, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }

    Column {
        if (scanning) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                ScannerView(onCode = { code ->
                    scanning = false
                    onSubmit(code)
                })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.point_camera),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            BigOutlinedButton(stringResource(R.string.stop_scan)) { scanning = false }
        } else {
            BigButton(stringResource(R.string.scan)) { scanning = true }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (text.isNotBlank()) onSubmit(text.trim())
            }),
        )
        Spacer(Modifier.height(8.dp))
        BigOutlinedButton(stringResource(R.string.find)) {
            if (text.isNotBlank()) onSubmit(text.trim())
        }
    }
}
