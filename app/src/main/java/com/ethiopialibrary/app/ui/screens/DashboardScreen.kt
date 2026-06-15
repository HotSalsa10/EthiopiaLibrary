package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.DashboardViewModel
import com.ethiopialibrary.app.ui.theme.LibraryAccents
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
    val dueSoon by vm.dueSoon.collectAsStateWithLifecycle()
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
        // Brand header: the library logo + localized name
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.library_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(96.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))

        // Primary actions
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton(
                stringResource(R.string.nav_checkout),
                Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.MenuBook,
            ) { onNavigate("checkout") }
            BigButton(
                stringResource(R.string.nav_return),
                Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.AssignmentReturn,
            ) { onNavigate("return") }
        }
        Spacer(Modifier.height(12.dp))

        // Secondary actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigOutlinedButton(
                stringResource(R.string.nav_books),
                Modifier.weight(1f),
                icon = Icons.Filled.Book,
            ) { onNavigate("books") }
            BigOutlinedButton(
                stringResource(R.string.nav_members),
                Modifier.weight(1f),
                icon = Icons.Filled.People,
            ) { onNavigate("members") }
            BigOutlinedButton(
                stringResource(R.string.nav_statistics),
                Modifier.weight(1f),
                icon = Icons.Filled.BarChart,
            ) { onNavigate("statistics") }
            BigOutlinedButton(
                stringResource(R.string.nav_settings),
                Modifier.weight(1f),
                icon = Icons.Filled.Settings,
            ) { onNavigate("settings") }
        }
        Spacer(Modifier.height(24.dp))

        // Stat tiles
        stats?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatTile(
                    Modifier.weight(1f), Icons.Filled.Book,
                    LibraryAccents.books, LibraryAccents.booksBg,
                    s.totalBooks, stringResource(R.string.stat_total_books),
                )
                StatTile(
                    Modifier.weight(1f), Icons.Filled.People,
                    LibraryAccents.members, LibraryAccents.membersBg,
                    s.totalMembers, stringResource(R.string.stat_total_members),
                )
                StatTile(
                    Modifier.weight(1f), Icons.Filled.LocalLibrary,
                    LibraryAccents.loans, LibraryAccents.loansBg,
                    s.activeLoans, stringResource(R.string.stat_active_loans),
                )
                StatTile(
                    Modifier.weight(1f), Icons.Filled.Schedule,
                    LibraryAccents.overdue, LibraryAccents.overdueBg,
                    s.overdueCount, stringResource(R.string.stat_overdue),
                    valueColor = if (s.overdueCount > 0) LibraryAccents.overdue else null,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Backup status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (lastBackupAt != null) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
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
        }
        Spacer(Modifier.height(24.dp))

        // Due soon section
        if (dueSoon.isNotEmpty()) {
            Text(
                stringResource(R.string.due_soon_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            dueSoon.forEach { loan ->
                DueSoonCard(loan, locale) {
                    scope.launch {
                        repo.renewLoan(loan.loan.id)
                        Toast.makeText(context, R.string.renew_done, Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        // Overdue section
        Text(
            stringResource(R.string.overdue_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        if (overdue.isEmpty()) {
            Text(
                stringResource(R.string.no_overdue),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun StatTile(
    modifier: Modifier,
    icon: ImageVector,
    accent: Color,
    accentBg: Color,
    value: Int,
    label: String,
    valueColor: Color? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "$value",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverdueCard(item: LoanWithDetails, locale: Locale, onRenew: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${item.bookTitle} — ${item.copyCode}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "${item.memberName} (${item.memberCode})",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "${stringResource(R.string.due_date)}: " +
                    DualCalendarFormatter.format(item.loan.dueAt, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRenew) {
                Text(stringResource(R.string.renew))
            }
        }
    }
}

@Composable
private fun DueSoonCard(item: LoanWithDetails, locale: Locale, onRenew: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${item.bookTitle} — ${item.copyCode}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "${item.memberName} (${item.memberCode})",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "${stringResource(R.string.due_date)}: " +
                    DualCalendarFormatter.format(item.loan.dueAt, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onRenew) {
                Text(stringResource(R.string.renew))
            }
        }
    }
}
