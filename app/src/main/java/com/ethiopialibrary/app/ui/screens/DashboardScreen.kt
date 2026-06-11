package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.DashboardViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    repo: LibraryRepository,
    onNavigate: (String) -> Unit,
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val overdue by vm.overdue.collectAsStateWithLifecycle()
    val pendingSync by vm.pendingSync.collectAsStateWithLifecycle()
    val lastBackupAt by vm.lastBackupAt.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton(stringResource(R.string.nav_checkout), Modifier.weight(1f)) {
                onNavigate("checkout")
            }
            BigButton(stringResource(R.string.nav_return), Modifier.weight(1f)) {
                onNavigate("return")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigOutlinedButton(stringResource(R.string.nav_books), Modifier.weight(1f)) {
                onNavigate("books")
            }
            BigOutlinedButton(stringResource(R.string.nav_members), Modifier.weight(1f)) {
                onNavigate("members")
            }
            BigOutlinedButton(stringResource(R.string.nav_settings), Modifier.weight(1f)) {
                onNavigate("settings")
            }
        }
        Spacer(Modifier.height(20.dp))

        stats?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(s.totalBooks, stringResource(R.string.stat_total_books), Modifier.weight(1f))
                StatCard(s.totalMembers, stringResource(R.string.stat_total_members), Modifier.weight(1f))
                StatCard(s.activeLoans, stringResource(R.string.stat_active_loans), Modifier.weight(1f))
                StatCard(
                    s.overdueCount,
                    stringResource(R.string.stat_overdue),
                    Modifier.weight(1f),
                    highlight = s.overdueCount > 0,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        val backupText = buildString {
            append(stringResource(R.string.last_backup))
            append(": ")
            append(
                lastBackupAt?.let { DualCalendarFormatter.format(it, locale) }
                    ?: stringResource(R.string.never_backed_up),
            )
            if (pendingSync > 0) {
                append(" • ")
                append(stringResource(R.string.pending_changes, pendingSync))
            }
        }
        Text(
            backupText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.overdue_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (overdue.isEmpty()) {
            Text(stringResource(R.string.no_overdue), style = MaterialTheme.typography.bodyLarge)
        } else {
            overdue.forEach { loan ->
                OverdueCard(loan, locale) {
                    scope.launch {
                        repo.renewLoan(loan.loan.id)
                        Toast.makeText(context, R.string.renew_done, Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatCard(value: Int, label: String, modifier: Modifier, highlight: Boolean = false) {
    Card(
        modifier = modifier,
        colors = if (highlight) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("$value", style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OverdueCard(item: LoanWithDetails, locale: Locale, onRenew: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${item.bookTitle} — ${item.copyCode}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${item.memberName} (${item.memberCode})",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${stringResource(R.string.due_date)}: " +
                    DualCalendarFormatter.format(item.loan.dueAt, locale),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRenew) {
                Text(stringResource(R.string.renew))
            }
        }
    }
}
