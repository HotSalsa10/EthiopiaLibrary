package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.ethiopialibrary.app.ui.AppSearchField
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.LoanStatus
import com.ethiopialibrary.app.ui.ScannerView
import com.ethiopialibrary.app.ui.StatusBadge

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
    AppSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.search_copy_hint),
        modifier = Modifier.fillMaxWidth(),
        autoFocus = true,
        onSubmit = {
            // Conservative: only auto-pick when exactly one row is actually
            // selectable, never guess among several ambiguous matches.
            val eligible = results.filter(selectable)
            if (eligible.size == 1) onPick(eligible.single().copy.copyCode)
        },
    )
    Spacer(Modifier.height(12.dp))
    if (query.isNotBlank() && results.isEmpty()) {
        Text(
            stringResource(R.string.no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (query.isBlank() && results.isEmpty()) {
        Text(
            stringResource(R.string.checkout_copy_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    results.forEach { row ->
        CopyResultCard(row, selectable(row), onPick)
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * One search result: book title, copy code (+ shelf), and loan/condition status.
 * On-loan and available are genuine [LoanStatus] concepts and get the shared
 * [StatusBadge]; lost/damaged/retired are copy *condition*, not loan status, so
 * they keep their existing plain-text treatment rather than being force-fit.
 */
@Composable
private fun CopyResultCard(row: CopyWithBook, selectable: Boolean, onPick: (String) -> Unit) {
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
        Spacer(Modifier.height(4.dp))
        when {
            row.onLoan -> StatusBadge(LoanStatus.ON_LOAN)
            row.copy.status == CopyStatus.IN_SERVICE -> StatusBadge(LoanStatus.AVAILABLE)
            else -> {
                val statusRes = when (row.copy.status) {
                    CopyStatus.LOST -> R.string.copy_status_lost
                    CopyStatus.DAMAGED -> R.string.copy_status_damaged
                    else -> R.string.copy_status_retired
                }
                Text(
                    stringResource(statusRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
        }
    }
}
