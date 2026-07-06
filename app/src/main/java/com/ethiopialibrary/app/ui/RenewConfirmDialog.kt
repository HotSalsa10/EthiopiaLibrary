package com.ethiopialibrary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import java.util.Locale

/**
 * Confirms a renewal and shows the new due date before it takes effect, so a
 * stray tap can't silently extend a loan. [newDueAt] is null while it loads.
 */
@Composable
fun RenewConfirmDialog(
    bookTitle: String,
    memberName: String,
    newDueAt: Long?,
    locale: Locale,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.renew)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$bookTitle — $memberName", fontWeight = FontWeight.Bold)
                Text(
                    if (newDueAt != null) {
                        stringResource(
                            R.string.renew_new_due,
                            DualCalendarFormatter.format(newDueAt, locale, LocalCalendarMode.current),
                        )
                    } else {
                        "…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.renew)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
