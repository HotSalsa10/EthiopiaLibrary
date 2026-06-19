package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.CopyStatus
import com.ethiopialibrary.app.data.CopyWithBook
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.ScannerView

/**
 * Scan-or-search copy finder shared by checkout and return. Scanning a QR submits
 * the exact code; otherwise staff search by book title/author/copy code and pick
 * from the matching copies. [selectable] decides which rows can be tapped — copies
 * available to loan (checkout) versus copies currently on loan (return) — so the
 * same component serves both desks.
 */
@Composable
fun CopyPickerStep(
    query: String,
    results: List<CopyWithBook>,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    selectable: (CopyWithBook) -> Boolean,
) {
    var scanning by remember { mutableStateOf(false) }

    if (scanning) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            ScannerView(onCode = { code ->
                scanning = false
                onPick(code)
            })
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.point_camera), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        BigOutlinedButton(stringResource(R.string.stop_scan)) { scanning = false }
    } else {
        BigButton(stringResource(R.string.scan)) { scanning = true }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.search_copy_hint)) },
        singleLine = true,
    )
    Spacer(Modifier.height(12.dp))
    if (query.isNotBlank() && results.isEmpty()) {
        Text(
            stringResource(R.string.no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    results.forEach { row ->
        CopyResultCard(row, selectable(row), onPick)
        Spacer(Modifier.height(8.dp))
    }
}

/** One search result: book title, copy code (+ shelf), and loan/condition status. */
@Composable
private fun CopyResultCard(row: CopyWithBook, selectable: Boolean, onPick: (String) -> Unit) {
    val statusRes = when {
        row.onLoan -> R.string.on_loan
        row.copy.status == CopyStatus.IN_SERVICE -> R.string.available
        row.copy.status == CopyStatus.LOST -> R.string.copy_status_lost
        row.copy.status == CopyStatus.DAMAGED -> R.string.copy_status_damaged
        else -> R.string.copy_status_retired
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (selectable) {
            { onPick(row.copy.copyCode) }
        } else {
            null
        },
    ) {
        Text(
            row.bookTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selectable) MaterialTheme.colorScheme.onSurface else muted,
        )
        Text(
            row.copy.copyCode + (row.copy.shelfLocation?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
            color = muted,
        )
        Text(
            stringResource(statusRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectable) MaterialTheme.colorScheme.primary else muted,
        )
    }
}
