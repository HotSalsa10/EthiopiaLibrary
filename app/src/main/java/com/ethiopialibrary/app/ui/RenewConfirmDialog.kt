package com.ethiopialibrary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import java.util.Locale

/**
 * Confirms a renewal and shows the new due date before it takes effect, so a
 * stray tap can't silently extend a loan. Staff can edit the number of days;
 * [previewDueAt] recomputes the resulting due date (never-shorten included)
 * for whatever [initialDays]-derived value is currently typed.
 */
@Composable
fun RenewConfirmDialog(
    bookTitle: String,
    memberName: String,
    initialDays: Int,
    previewDueAt: suspend (Int) -> Long,
    locale: Locale,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var daysText by remember { mutableStateOf(initialDays.toString()) }
    val days = daysText.toIntOrNull()?.coerceIn(1, LibraryRepository.MAX_LOAN_PERIOD_DAYS) ?: 1
    val preview by produceState<Long?>(null, days) { value = previewDueAt(days) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.renew)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$bookTitle — $memberName", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.settings_loan_period)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    if (preview != null) {
                        stringResource(
                            R.string.renew_new_due,
                            DualCalendarFormatter.format(preview!!, locale, LocalCalendarMode.current),
                        )
                    } else {
                        "…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(days) }) { Text(stringResource(R.string.renew)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
