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
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.DashboardViewModel
import com.ethiopialibrary.app.ui.LocalCalendarMode
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
            BackupChip(lastBackupAt, pendingSync, locale)
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
                LoanAlertCard(loan, locale, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                    scope.launch {
                        repo.renewLoan(loan.loan.id)
                        Toast.makeText(context, R.string.renew_done, Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        // Overdue section
        SectionHeader(stringResource(R.string.overdue_title))
        Spacer(Modifier.height(12.dp))
        if (overdue.isEmpty()) {
            Text(
                stringResource(R.string.no_overdue),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            overdue.forEach { loan ->
                LoanAlertCard(loan, locale, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) {
                    scope.launch {
                        repo.renewLoan(loan.loan.id)
                        Toast.makeText(context, R.string.renew_done, Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** A small, subtle pill that surfaces backup state without competing for attention. */
@Composable
private fun BackupChip(lastBackupAt: Long?, pendingSync: Int, locale: Locale) {
    val calendarMode = LocalCalendarMode.current
    val text = buildString {
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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (lastBackupAt != null) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            .clickable(interactionSource = interaction, indication = ripple()) {},
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
