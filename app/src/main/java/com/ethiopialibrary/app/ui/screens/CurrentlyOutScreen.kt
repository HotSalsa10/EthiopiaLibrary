package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.ui.AppSearchField
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.CurrentlyOutViewModel
import com.ethiopialibrary.app.ui.LoanStatus
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.PageColumn
import com.ethiopialibrary.app.ui.RenewConfirmDialog
import com.ethiopialibrary.app.ui.ReturnConfirmDialog
import com.ethiopialibrary.app.ui.StatusBadge
import com.ethiopialibrary.app.ui.StatusEdgeCard
import com.ethiopialibrary.app.ui.color
import com.ethiopialibrary.app.ui.renewResultMessageRes
import java.util.Locale

// Same value DashboardScreen.kt uses locally; no shared constant exists to import.
private const val MILLIS_PER_DAY = 86_400_000L

/** Which loans the filter chips row narrows the list down to. */
private enum class LoanFilter { ALL, DUE_SOON, OVERDUE }

/**
 * Pure due-date -> [LoanStatus] classification, presentation-only: it maps facts the
 * caller already knows (the due date, the current time, and the configured due-soon
 * window) to a status for display, the same boundary discipline [LoanStatus] itself
 * follows. [dueSoonDays] is the same configured value Dashboard's own due-soon list
 * already honors, so this never invents a second, inconsistent definition of "due soon".
 * `internal` (not `private`) so it's plain-JUnit-testable from the test source set,
 * mirroring the precedent set by `activityTypeColor`/`statusText` in earlier tasks.
 */
internal fun classify(dueAt: Long, now: Long, dueSoonDays: Int?): LoanStatus = when {
    dueAt < now -> LoanStatus.OVERDUE
    dueSoonDays != null && dueAt <= now + dueSoonDays * MILLIS_PER_DAY -> LoanStatus.DUE_SOON
    else -> LoanStatus.ON_LOAN
}

/** Every book on loan right now, soonest-due first, filterable by status, with per-row renew and return. */
@Composable
fun CurrentlyOutScreen(
    vm: CurrentlyOutViewModel,
    onBack: () -> Unit,
    initialFilter: String? = null,
) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val dueSoonDays by vm.dueSoonDays.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    var renewTarget by remember { mutableStateOf<LoanWithDetails?>(null) }
    var returnTarget by remember { mutableStateOf<LoanWithDetails?>(null) }
    var filterMode by remember {
        mutableStateOf(if (initialFilter == "overdue") LoanFilter.OVERDUE else LoanFilter.ALL)
    }
    val now = System.currentTimeMillis()

    renewTarget?.let { target ->
        val preview by produceState<Long?>(null, target) { value = vm.renewPreviewDueAt(target.loan.id) }
        RenewConfirmDialog(
            bookTitle = target.bookTitle,
            memberName = target.memberName,
            newDueAt = preview,
            locale = locale,
            onConfirm = {
                vm.renew(target.loan.id) { result ->
                    Toast.makeText(context, renewResultMessageRes(result), Toast.LENGTH_SHORT).show()
                }
                renewTarget = null
            },
            onDismiss = { renewTarget = null },
        )
    }

    returnTarget?.let { target ->
        ReturnConfirmDialog(
            bookTitle = target.bookTitle,
            memberName = target.memberName,
            onConfirm = {
                vm.returnBook(target.copyCode) { success ->
                    val message = if (success) R.string.return_success else R.string.error_no_active_loan
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                returnTarget = null
            },
            onDismiss = { returnTarget = null },
        )
    }

    val filteredLoans = loans.filter { item ->
        when (filterMode) {
            LoanFilter.ALL -> true
            LoanFilter.DUE_SOON -> classify(item.loan.dueAt, now, dueSoonDays) == LoanStatus.DUE_SOON
            LoanFilter.OVERDUE -> classify(item.loan.dueAt, now, dueSoonDays) == LoanStatus.OVERDUE
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Density-independent landscape/portrait check (no WindowSizeClass dependency),
        // same technique as Tasks 2/3: a wider-than-tall tablet gets a 2-column grid.
        val isGrid = maxWidth > maxHeight
        PageColumn(maxWidth = if (isGrid) 1100.dp else 760.dp, scrollable = false) {
            AppTopBar(stringResource(R.string.currently_out), onBack)
            Spacer(Modifier.height(12.dp))
            AppSearchField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = stringResource(R.string.search_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterMode == LoanFilter.ALL,
                    onClick = { filterMode = LoanFilter.ALL },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                FilterChip(
                    selected = filterMode == LoanFilter.DUE_SOON,
                    onClick = { filterMode = LoanFilter.DUE_SOON },
                    label = { Text(stringResource(R.string.status_due_soon)) },
                )
                FilterChip(
                    selected = filterMode == LoanFilter.OVERDUE,
                    onClick = { filterMode = LoanFilter.OVERDUE },
                    label = { Text(stringResource(R.string.status_overdue)) },
                )
            }
            Spacer(Modifier.height(16.dp))

            Box(Modifier.weight(1f)) {
                if (filteredLoans.isEmpty()) {
                    Text(
                        stringResource(
                            if (query.isBlank() && filterMode == LoanFilter.ALL) {
                                R.string.no_books_out
                            } else {
                                R.string.no_matches
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (isGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filteredLoans, key = { it.loan.id }) { loan ->
                            OutLoanCard(
                                item = loan,
                                status = classify(loan.loan.dueAt, now, dueSoonDays),
                                locale = locale,
                                onRenew = { renewTarget = loan },
                                onReturn = { returnTarget = loan },
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredLoans, key = { it.loan.id }) { loan ->
                            OutLoanCard(
                                item = loan,
                                status = classify(loan.loan.dueAt, now, dueSoonDays),
                                locale = locale,
                                onRenew = { renewTarget = loan },
                                onReturn = { returnTarget = loan },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutLoanCard(
    item: LoanWithDetails,
    status: LoanStatus,
    locale: Locale,
    onRenew: () -> Unit,
    onReturn: () -> Unit,
) {
    StatusEdgeCard(status = status, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${item.bookTitle} — ${item.copyCode}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${item.memberName} (${item.memberCode})",
            style = MaterialTheme.typography.bodyLarge,
        )
        // Dual-date emphasis: the due date itself gets bigger/colored when it matters
        // (due-soon/overdue), rather than reading as a same-weight line among others.
        Text(
            "${stringResource(R.string.due_date)}: " +
                DualCalendarFormatter.format(item.loan.dueAt, locale, LocalCalendarMode.current),
            style = MaterialTheme.typography.bodyLarge,
            color = if (status == LoanStatus.ON_LOAN) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                status.color()
            },
        )
        Spacer(Modifier.height(8.dp))
        StatusBadge(status)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigOutlinedButton(stringResource(R.string.renew), Modifier.weight(1f)) { onRenew() }
            BigOutlinedButton(stringResource(R.string.nav_return), Modifier.weight(1f)) { onReturn() }
        }
    }
}
