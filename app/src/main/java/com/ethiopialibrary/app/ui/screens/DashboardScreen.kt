package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.ethiopialibrary.app.data.ActivityType
import com.ethiopialibrary.app.data.ActivityWithDetails
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.sync.SyncWorker
import com.ethiopialibrary.app.sync.connectivityFlow
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.DashboardViewModel
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.RenewConfirmDialog
import com.ethiopialibrary.app.ui.SectionHeader
import com.ethiopialibrary.app.ui.pressScale
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
    val overdueQuery by vm.overdueQuery.collectAsStateWithLifecycle()
    val dueSoon by vm.dueSoon.collectAsStateWithLifecycle()
    val recentActivity by vm.recentActivity.collectAsStateWithLifecycle()
    val pendingSync by vm.pendingSync.collectAsStateWithLifecycle()
    val lastBackupAt by vm.lastBackupAt.collectAsStateWithLifecycle()
    val backupStaleSince by vm.backupStaleSince.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var renewTarget by remember { mutableStateOf<LoanWithDetails?>(null) }

    renewTarget?.let { target ->
        val preview by produceState<Long?>(null, target) { value = repo.renewalPreviewDueAt() }
        RenewConfirmDialog(
            bookTitle = target.bookTitle,
            memberName = target.memberName,
            newDueAt = preview,
            locale = locale,
            onConfirm = {
                scope.launch {
                    repo.renewLoan(target.loan.id)
                    Toast.makeText(context, R.string.renew_done, Toast.LENGTH_SHORT).show()
                }
                renewTarget = null
            },
            onDismiss = { renewTarget = null },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        // Brand header: logo, localized name, and a discreet backup indicator.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.library_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(88.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            BackupChip(lastBackupAt, pendingSync, backupStaleSince, locale)
        }

        // Gentle, dismissible once-per-day suggestion when internet is
        // available and changes are waiting - never a blocking dialog.
        val nudgeWanted by vm.backupNudgeWanted.collectAsStateWithLifecycle()
        val online by remember { connectivityFlow(context) }.collectAsStateWithLifecycle(false)
        if (nudgeWanted && online) {
            Spacer(Modifier.height(20.dp))
            BackupNudgeCard(
                onBackupNow = {
                    SyncWorker.backupNow(context)
                    Toast.makeText(context, R.string.backup_started, Toast.LENGTH_SHORT).show()
                    vm.dismissBackupNudge()
                },
                onLater = { vm.dismissBackupNudge() },
            )
        }
        Spacer(Modifier.height(28.dp))

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
        Spacer(Modifier.height(16.dp))

        // Secondary navigation as a single segmented row
        SegmentedNavRow(
            listOf(
                NavSegment(Icons.Filled.Book, stringResource(R.string.nav_books)) { onNavigate("books") },
                NavSegment(Icons.Filled.People, stringResource(R.string.nav_members)) { onNavigate("members") },
                NavSegment(Icons.Filled.BarChart, stringResource(R.string.nav_statistics)) { onNavigate("statistics") },
                NavSegment(Icons.Filled.Settings, stringResource(R.string.nav_settings)) { onNavigate("settings") },
            ),
        )
        Spacer(Modifier.height(28.dp))

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
                    onClick = { onNavigate("loans") },
                )
                StatTile(
                    Modifier.weight(1f), Icons.Filled.Schedule,
                    LibraryAccents.overdue, LibraryAccents.overdueBg,
                    s.overdueCount, stringResource(R.string.stat_overdue),
                    valueColor = if (s.overdueCount > 0) LibraryAccents.overdue else null,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        // Due soon section
        if (dueSoon.isNotEmpty()) {
            SectionHeader(stringResource(R.string.due_soon_title))
            Spacer(Modifier.height(12.dp))
            dueSoon.forEach { loan ->
                LoanAlertCard(loan, locale, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) { renewTarget = loan }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        // Overdue section. The filter appears once there are overdue loans (or a
        // query is active); an empty result then means "no match" rather than none.
        SectionHeader(stringResource(R.string.overdue_title))
        Spacer(Modifier.height(12.dp))
        val showOverdueFilter = overdue.isNotEmpty() || overdueQuery.isNotBlank()
        if (showOverdueFilter) {
            OutlinedTextField(
                value = overdueQuery,
                onValueChange = vm::setOverdueQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (overdue.isEmpty()) {
            Text(
                stringResource(if (showOverdueFilter) R.string.no_data else R.string.no_overdue),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            overdue.forEach { loan ->
                LoanAlertCard(loan, locale, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) { renewTarget = loan }
                Spacer(Modifier.height(10.dp))
            }
        }

        // Recent activity: today only, last ~10, each row undoable same-day.
        if (recentActivity.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.recent_activity))
            Spacer(Modifier.height(12.dp))
            recentActivity.forEach { item ->
                ActivityRow(item) { vm.undoActivity(item.entry.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** A small, subtle pill that surfaces backup state without competing for attention. */
@Composable
private fun BackupChip(
    lastBackupAt: Long?,
    pendingSync: Int,
    staleSince: Long?,
    locale: Locale,
) {
    val calendarMode = LocalCalendarMode.current
    // Changes sitting un-backed-up for days deserve a visible warning,
    // not just a pending count the operator has learned to ignore.
    val daysWaiting = staleSince
        ?.let { ((System.currentTimeMillis() - it) / MILLIS_PER_DAY).toInt() }
        ?: 0
    val needsBackup = pendingSync > 0 && daysWaiting >= STALE_BACKUP_WARNING_DAYS
    val text = if (needsBackup) {
        stringResource(R.string.backup_needed_warning, daysWaiting)
    } else {
        buildString {
            append(stringResource(R.string.last_backup))
            append(": ")
            append(
                lastBackupAt?.let { DualCalendarFormatter.format(it, locale, calendarMode) }
                    ?: stringResource(R.string.never_backed_up),
            )
            if (pendingSync > 0) {
                append(" • ")
                append(stringResource(R.string.pending_changes, pendingSync))
            }
        }
    }
    val background = if (needsBackup) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val contentColor = if (needsBackup) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (lastBackupAt != null && !needsBackup) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
        )
    }
}

private const val STALE_BACKUP_WARNING_DAYS = 3
private const val MILLIS_PER_DAY = 86_400_000L

/** Suggests a backup while internet is around; both actions quiet it for the day. */
@Composable
private fun BackupNudgeCard(onBackupNow: () -> Unit, onLater: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.backup_nudge_text),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onLater) { Text(stringResource(R.string.backup_nudge_later)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onBackupNow) { Text(stringResource(R.string.backup_now)) }
        }
    }
}

private data class NavSegment(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Secondary destinations grouped into one clean control with hairline dividers. */
@Composable
private fun SegmentedNavRow(items: List<NavSegment>) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, spotColor = LibraryAccents.shadowSoft, ambientColor = LibraryAccents.shadowSoft)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = item.onClick,
                    )
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .height(132.dp)
            .pressScale(interaction)
            .shadow(3.dp, shape, spotColor = LibraryAccents.shadowCard, ambientColor = LibraryAccents.shadowCard)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = interaction, indication = ripple()) { onClick() },
    ) {
        // Faint metric watermark, bleeding off the bottom-end corner for depth.
        Icon(
            icon,
            contentDescription = null,
            tint = accent.copy(alpha = 0.06f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(132.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    "$value",
                    style = MaterialTheme.typography.headlineLarge,
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
}

@Composable
private fun LoanAlertCard(
    item: LoanWithDetails,
    locale: Locale,
    container: Color,
    onContainer: Color,
    onRenew: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = container) {
        Text(
            "${item.bookTitle} — ${item.copyCode}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onContainer,
        )
        Text(
            "${item.memberName} (${item.memberCode})",
            style = MaterialTheme.typography.bodyLarge,
            color = onContainer,
        )
        Text(
            "${stringResource(R.string.due_date)}: " +
                DualCalendarFormatter.format(item.loan.dueAt, locale, LocalCalendarMode.current),
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
        )
        TextButton(onClick = onRenew) {
            Text(stringResource(R.string.renew))
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityWithDetails, onUndo: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    activityLabel(item.entry.type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("${item.bookTitle} — ${item.memberName}", style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onUndo) { Text(stringResource(R.string.undo)) }
        }
    }
}

@Composable
private fun activityLabel(type: String): String = when (type) {
    ActivityType.CHECKOUT.name -> stringResource(R.string.activity_checkout)
    ActivityType.RETURN.name -> stringResource(R.string.activity_return)
    ActivityType.RENEW.name -> stringResource(R.string.activity_renew)
    ActivityType.UNDO.name -> stringResource(R.string.activity_undo)
    else -> type // unreachable in practice; every writer uses ActivityType
}
