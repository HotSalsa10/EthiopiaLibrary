package com.ethiopialibrary.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ethiopialibrary.app.R

/** Confirms a return before it takes effect, so a stray tap can't silently return the wrong book. */
@Composable
fun ReturnConfirmDialog(
    bookTitle: String,
    memberName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nav_return)) },
        text = { Text("$bookTitle — $memberName", fontWeight = FontWeight.Bold) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.nav_return)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
