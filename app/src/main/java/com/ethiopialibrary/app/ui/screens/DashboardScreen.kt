package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.BuildConfig
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.ActivityType
import com.ethiopialibrary.app.data.ActivityWithDetails
import com.ethiopialibrary.app.data.DashboardStats
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.sync.SyncWorker
import com.ethiopialibrary.app.sync.announcementText
import com.ethiopialibrary.app.sync.connectivityFlow
import com.ethiopialibrary.app.sync.updateRequired
import com.ethiopialibrary.app.update.PackageInstallerUpdateInstaller
import com.ethiopialibrary.app.update.updateAvailable
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppSearchField
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.DashboardViewModel
import com.ethiopialibrary.app.ui.LoanStatus
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.PageColumn
import com.ethiopialibrary.app.ui.RenewConfirmDialog
import com.ethiopialibrary.app.ui.SectionHeader
import com.ethiopialibrary.app.ui.StatusBadge
import com.ethiopialibrary.app.ui.StatusEdgeCard
import com.ethiopialibrary.app.ui.TwoPaneRow
import com.ethiopialibrary.app.ui.pressScale
import com.ethiopialibrary.app.ui.renewResultMessageRes
import com.ethiopialibrary.app.ui.safeLaunch
import com.ethiopialibrary.app.ui.theme.LibraryAccents
import com.ethiopialibrary.app.ui.theme.LibraryStatus
import java.util.Locale

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    repo: LibraryRepository,
    onNavigate: (String) -> Unit,
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val clockWrong by vm.clockWrong.collectAsStateWithLifecycle()
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
    val onUndo: (String) -> Unit = { id ->
        vm.undoActivity(id) { ok ->
            if (!ok) Toast.makeText(context, R.string.undo_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    renewTarget?.let { target ->
        val defaultDays by produceState(LibraryRepository.DEFAULT_LOAN_PERIOD_DAYS) { value = repo.loanPeriodDays() }
        RenewConfirmDialog(
            bookTitle = target.bookTitle,
            memberName = target.memberName,
            initialDays = defaultDays,
            previewDueAt = { d -> repo.renewalPreviewDueAt(target.loan.id, d) },
            locale = locale,
            onConfirm = { d ->
                scope.safeLaunch {
                    val result = repo.renewLoan(target.loan.id, d)
                    Toast.makeText(context, renewResultMessageRes(result), Toast.LENGTH_SHORT).show()
                }
                renewTarget = null
            },
            onDismiss = { renewTarget = null },
        )
    }
    val onRenewRequest: (LoanWithDetails) -> Unit = { renewTarget = it }

    // Gentle, dismissible once-per-day suggestion when internet is available
    // and changes are waiting - never a blocking dialog. State and callbacks
    // live here, once, same as renewTarget/RenewConfirmDialog above; only the
    // visual layout below branches on orientation.
    val nudgeWanted by vm.backupNudgeWanted.collectAsStateWithLifecycle()
    val online by remember { connectivityFlow(context) }.collectAsStateWithLifecycle(false)
    val onBackupNow: () -> Unit = {
        SyncWorker.backupNow(context)
        Toast.makeText(context, R.string.backup_started, Toast.LENGTH_SHORT).show()
        vm.dismissBackupNudge()
    }
    val onLater: () -> Unit = { vm.dismissBackupNudge() }

    // Config-from-cloud: a Console-authored announcement (dismissible, keyed
    // by its id so a new announcement isn't hidden by an old dismissal) and a
    // persistent, non-blocking "please update" banner when this build is
    // older than the Console's configured minimum.
    val remoteDirectives by vm.remoteDirectives.collectAsStateWithLifecycle()
    val dismissedAnnouncementId by vm.dismissedAnnouncementId.collectAsStateWithLifecycle()
    val announcementId = remoteDirectives.announcementId
    val announcement = if (announcementId != null && announcementId != dismissedAnnouncementId) {
        announcementText(remoteDirectives, locale.language)?.let { text -> announcementId to text }
    } else {
        null
    }
    val updateBannerNeeded = updateRequired(remoteDirectives, BuildConfig.VERSION_CODE)

    // Self-update (Wave 4): a downloaded-and-verified build waiting to
    // install. "Later" only hides it for this session (not persisted) -
    // unlike the announcement/backup nudge, there's no reason to make an
    // available update easy to silence long-term.
    val updateReadyInfo by repo.updateReadyInfo().collectAsStateWithLifecycle(null)
    val selfUpdateAvailable = updateAvailable(updateReadyInfo, BuildConfig.VERSION_CODE)
    var selfUpdateDismissed by remember { mutableStateOf(false) }
    val onInstallSelfUpdate: () -> Unit = {
        updateReadyInfo?.let { info ->
            val installer = PackageInstallerUpdateInstaller(context)
            if (installer.canInstall()) installer.install(info.apkPath) else installer.requestInstallPermission()
        }
    }

    // BoxWithConstraints is the density-independent way to tell a wider-than-tall
    // tablet orientation from a taller-than-wide one, without a WindowSizeClass
    // dependency: landscape gets a two-pane layout, portrait a single column.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            TwoPaneRow(
                left = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        BrandHeader(lastBackupAt, pendingSync, backupStaleSince, locale)
                        if (clockWrong) {
                            ClockWrongBanner()
                        }
                        if (updateBannerNeeded) {
                            UpdateRequiredBanner()
                        }
                        announcement?.let { (id, text) ->
                            AnnouncementCard(text = text, onDismiss = { vm.dismissAnnouncement(id) })
                        }
                        if (selfUpdateAvailable && !selfUpdateDismissed && updateReadyInfo != null) {
                            UpdateReadyCard(
                                versionName = updateReadyInfo!!.versionName,
                                onInstallNow = onInstallSelfUpdate,
                                onLater = { selfUpdateDismissed = true },
                            )
                        }
                        if (nudgeWanted && online) {
                            BackupNudgeCard(onBackupNow = onBackupNow, onLater = onLater)
                        }
                        PrimaryActionsRow(onNavigate)
                        DashboardSecondaryNav(onNavigate)
                        stats?.let { s -> StatTilesGrid(s, onNavigate) }
                    }
                },
                right = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        DueSoonSection(dueSoon, locale, onRenewRequest)
                        OverdueSection(overdue, overdueQuery, vm::setOverdueQuery, locale, onRenewRequest)
                        ActivityFeedSection(recentActivity, onUndo)
                    }
                },
            )
        } else {
            PageColumn {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    BrandHeader(lastBackupAt, pendingSync, backupStaleSince, locale)
                    if (clockWrong) {
                        ClockWrongBanner()
                    }
                    if (updateBannerNeeded) {
                        UpdateRequiredBanner()
                    }
                    announcement?.let { (id, text) ->
                        AnnouncementCard(text = text, onDismiss = { vm.dismissAnnouncement(id) })
                    }
                    if (selfUpdateAvailable && !selfUpdateDismissed && updateReadyInfo != null) {
                        UpdateReadyCard(
                            versionName = updateReadyInfo!!.versionName,
                            onInstallNow = onInstallSelfUpdate,
                            onLater = { selfUpdateDismissed = true },
                        )
                    }
                    if (nudgeWanted && online) {
                        BackupNudgeCard(onBackupNow = onBackupNow, onLater = onLater)
                    }
                    PrimaryActionsRow(onNavigate)
                    DashboardSecondaryNav(onNavigate)
                    stats?.let { s -> StatTilesRow(s, onNavigate) }
                    DueSoonSection(dueSoon, locale, onRenewRequest)
                    OverdueSection(overdue, overdueQuery, vm::setOverdueQuery, locale, onRenewRequest)
                    ActivityFeedSection(recentActivity, vm::undoActivity)
                }
            }
        }
    }
}

/** Compact brand header: small logo, app name (ellipsized if long), and a discreet backup indicator. */
@Composable
private fun BrandHeader(
    lastBackupAt: Long?,
    pendingSync: Int,
    backupStaleSince: Long?,
    locale: Locale,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.library_logo),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(44.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BackupChip(lastBackupAt, pendingSync, backupStaleSince, locale)
    }
}

/** The two most-used desk actions, each hinting its keyboard shortcut for mouse+keyboard use. */
@Composable
private fun PrimaryActionsRow(onNavigate: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BigButton(
            stringResource(R.string.nav_checkout),
            Modifier.weight(1f),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            shortcutHint = "Ctrl+O",
        ) { onNavigate("checkout") }
        BigButton(
            stringResource(R.string.nav_return),
            Modifier.weight(1f),
            icon = Icons.AutoMirrored.Filled.AssignmentReturn,
            shortcutHint = "Ctrl+R",
        ) { onNavigate("return") }
    }
}

/** Secondary navigation as a single segmented row; factored out so both layout branches share one list. */
@Composable
private fun DashboardSecondaryNav(onNavigate: (String) -> Unit) {
    SegmentedNavRow(
        listOf(
            NavSegment(Icons.Filled.Book, stringResource(R.string.nav_books)) { onNavigate("books") },
            NavSegment(Icons.Filled.People, stringResource(R.string.nav_members)) { onNavigate("members") },
            NavSegment(Icons.Filled.BarChart, stringResource(R.string.nav_statistics)) { onNavigate("statistics") },
            NavSegment(Icons.Filled.Settings, stringResource(R.string.nav_settings)) { onNavigate("settings") },
        ),
    )
}

@Composable
private fun BooksStatTile(modifier: Modifier, stats: DashboardStats, onNavigate: (String) -> Unit) {
    StatTile(
        modifier, Icons.Filled.Book,
        LibraryAccents.books, LibraryAccents.booksBg,
        stats.totalBooks, stringResource(R.string.stat_total_books),
        onClick = { onNavigate("books") },
    )
}

@Composable
private fun MembersStatTile(modifier: Modifier, stats: DashboardStats, onNavigate: (String) -> Unit) {
    StatTile(
        modifier, Icons.Filled.People,
        LibraryAccents.members, LibraryAccents.membersBg,
        stats.totalMembers, stringResource(R.string.stat_total_members),
        onClick = { onNavigate("members") },
    )
}

@Composable
private fun ActiveLoansStatTile(modifier: Modifier, stats: DashboardStats, onNavigate: (String) -> Unit) {
    StatTile(
        modifier, Icons.Filled.LocalLibrary,
        LibraryAccents.loans, LibraryAccents.loansBg,
        stats.activeLoans, stringResource(R.string.stat_active_loans),
        onClick = { onNavigate("loans") },
    )
}

@Composable
private fun OverdueStatTile(modifier: Modifier, stats: DashboardStats, onNavigate: (String) -> Unit) {
    StatTile(
        modifier, Icons.Filled.Schedule,
        LibraryAccents.overdue, LibraryAccents.overdueBg,
        stats.overdueCount, stringResource(R.string.stat_overdue),
        valueColor = if (stats.overdueCount > 0) LibraryAccents.overdue else null,
        onClick = { onNavigate("loans?filter=overdue") },
    )
}

/** Portrait arrangement: all four stat tiles in one row. */
@Composable
private fun StatTilesRow(stats: DashboardStats, onNavigate: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BooksStatTile(Modifier.weight(1f), stats, onNavigate)
        MembersStatTile(Modifier.weight(1f), stats, onNavigate)
        ActiveLoansStatTile(Modifier.weight(1f), stats, onNavigate)
        OverdueStatTile(Modifier.weight(1f), stats, onNavigate)
    }
}

/** Landscape left-pane arrangement: a 2x2 grid, since the pane is roughly half the screen's width. */
@Composable
private fun StatTilesGrid(stats: DashboardStats, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BooksStatTile(Modifier.weight(1f), stats, onNavigate)
            MembersStatTile(Modifier.weight(1f), stats, onNavigate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActiveLoansStatTile(Modifier.weight(1f), stats, onNavigate)
            OverdueStatTile(Modifier.weight(1f), stats, onNavigate)
        }
    }
}

@Composable
private fun DueSoonSection(
    dueSoon: List<LoanWithDetails>,
    locale: Locale,
    onRenew: (LoanWithDetails) -> Unit,
) {
    if (dueSoon.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.due_soon_title))
            dueSoon.forEach { loan ->
                LoanAlertCard(loan, locale, LoanStatus.DUE_SOON) { onRenew(loan) }
            }
        }
    }
}

/** The overdue section's search field is always visible now - no layout jump as results arrive. */
@Composable
private fun OverdueSection(
    overdue: List<LoanWithDetails>,
    overdueQuery: String,
    onQueryChange: (String) -> Unit,
    locale: Locale,
    onRenew: (LoanWithDetails) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.overdue_title))
        AppSearchField(
            value = overdueQuery,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier.fillMaxWidth(),
            autoFocus = false,
        )
        if (overdue.isEmpty()) {
            Text(
                stringResource(if (overdueQuery.isNotBlank()) R.string.no_data else R.string.no_overdue),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            overdue.forEach { loan ->
                LoanAlertCard(loan, locale, LoanStatus.OVERDUE) { onRenew(loan) }
            }
        }
    }
}

@Composable
private fun ActivityFeedSection(
    recentActivity: List<ActivityWithDetails>,
    onUndo: (String) -> Unit,
) {
    if (recentActivity.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(stringResource(R.string.recent_activity))
            recentActivity.forEach { item ->
                ActivityRow(item) { onUndo(item.entry.id) }
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

/** A downloaded-and-verified build is waiting to install; "Later" only hides it for this session. */
@Composable
private fun UpdateReadyCard(versionName: String, onInstallNow: () -> Unit, onLater: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.update_available, versionName),
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
            TextButton(onClick = onInstallNow) { Text(stringResource(R.string.install_update)) }
        }
    }
}

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

/** Persistent, error-styled: the tablet's own clock predates this build, blocking checkouts and renews. */
@Composable
private fun ClockWrongBanner() {
    AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.error_clock_wrong),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Persistent, non-blocking: the running build is older than the Console's configured minimum. */
@Composable
private fun UpdateRequiredBanner() {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.update_required_banner),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A Console-authored announcement; dismissing it is keyed by the announcement's own id. */
@Composable
private fun AnnouncementCard(text: String, onDismiss: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.announcement_dismiss)) }
        }
    }
}

private data class NavSegment(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Secondary destinations grouped into one clean control with hairline dividers. */
@Composable
private fun SegmentedNavRow(items: List<NavSegment>) {
    val shape = MaterialTheme.shapes.large
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
            val hovered by interaction.collectIsHoveredAsState()
            val focused by interaction.collectIsFocusedAsState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (hovered) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
                    .then(if (focused) Modifier.border(2.dp, LibraryStatus.focusRing) else Modifier)
                    .hoverable(interaction)
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
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            // 140dp = the original 132dp plus the exact +8dp that displaySmall's
            // 48sp line height adds over headlineLarge's 40sp (both confirmed
            // against the real M3 token bytecode), so the icon/number/label
            // padding stays the same proportions as before, not tighter.
            .height(140.dp)
            .pressScale(interaction)
            .shadow(3.dp, shape, spotColor = LibraryAccents.shadowCard, ambientColor = LibraryAccents.shadowCard)
            .clip(shape)
            .background(if (hovered) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .then(if (focused) Modifier.border(2.dp, LibraryStatus.focusRing, shape) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = ripple()) { onClick() },
    ) {
        // Faint metric watermark, bleeding off the bottom-end corner for depth.
        Icon(
            icon,
            contentDescription = null,
            tint = accent.copy(alpha = 0.06f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(140.dp),
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
                    style = MaterialTheme.typography.displaySmall,
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
    status: LoanStatus,
    onRenew: () -> Unit,
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
        Text(
            "${stringResource(R.string.due_date)}: " +
                DualCalendarFormatter.format(item.loan.dueAt, locale, LocalCalendarMode.current),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        StatusBadge(status)
        Spacer(Modifier.height(4.dp))
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
                    color = activityColor(item.entry.type),
                )
                Text("${item.bookTitle} — ${item.memberName}", style = MaterialTheme.typography.bodyLarge)
            }
            if (item.entry.undoneAt == null) {
                TextButton(onClick = onUndo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.undo))
                }
            }
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

/**
 * Pure ActivityType -> color lookup, for quicker scanning of the activity feed:
 * a book leaving the shelf, one coming back, a gold renewal, and a muted
 * correction read differently at a glance. The two theme colors that aren't
 * plain [LibraryStatus] constants are passed in (not read via MaterialTheme
 * directly) so this stays plain-JUnit-testable - the same pure/composable
 * split Wave A used for its `LoanStatus` -> color mapping.
 */
internal fun activityTypeColor(type: String, secondary: Color, onSurfaceVariant: Color): Color = when (type) {
    ActivityType.CHECKOUT.name -> LibraryStatus.onLoan
    ActivityType.RETURN.name -> LibraryStatus.available
    ActivityType.RENEW.name -> secondary
    ActivityType.UNDO.name -> onSurfaceVariant
    else -> onSurfaceVariant // unreachable in practice; every writer uses ActivityType
}

@Composable
private fun activityColor(type: String): Color =
    activityTypeColor(type, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSurfaceVariant)
