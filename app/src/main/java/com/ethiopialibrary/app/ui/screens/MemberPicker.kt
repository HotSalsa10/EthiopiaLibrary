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
import com.ethiopialibrary.app.data.MemberStatus
import com.ethiopialibrary.app.data.MemberWithLoanCount
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppSearchField
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.ScannerView

/**
 * Scan-or-search member finder for checkout, mirroring CopyPickerStep. Scanning a
 * member's QR submits the exact code; otherwise staff search by name or member
 * code and pick from the matching results.
 */
@Composable
fun MemberPickerStep(
    query: String,
    results: List<MemberWithLoanCount>,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onAddMember: () -> Unit,
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
        placeholder = stringResource(R.string.search_member_hint),
        modifier = Modifier.fillMaxWidth(),
        autoFocus = true,
        onSubmit = {
            // Conservative: only auto-pick when exactly one row is actually
            // selectable, never guess among several ambiguous matches.
            val eligible = results.filter { it.member.status == MemberStatus.ACTIVE }
            if (eligible.size == 1) onPick(eligible.single().member.memberCode)
        },
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
        MemberResultCard(row, row.member.status == MemberStatus.ACTIVE, onPick)
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(12.dp))
    BigOutlinedButton(stringResource(R.string.add_member)) { onAddMember() }
}

/** One search result: member name, member code, and active-loan count or suspended status. */
@Composable
private fun MemberResultCard(row: MemberWithLoanCount, selectable: Boolean, onPick: (String) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (selectable) {
            { onPick(row.member.memberCode) }
        } else {
            null
        },
    ) {
        Text(
            row.member.fullName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selectable) MaterialTheme.colorScheme.onSurface else muted,
        )
        Text(row.member.memberCode, style = MaterialTheme.typography.bodyLarge, color = muted)
        Spacer(Modifier.height(4.dp))
        if (row.member.status == MemberStatus.SUSPENDED) {
            Text(
                stringResource(R.string.error_member_not_active),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                stringResource(R.string.member_active_loans, row.activeLoans),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
    }
}
