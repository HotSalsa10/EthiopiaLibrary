package com.ethiopialibrary.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.ui.theme.LibraryStatus

/**
 * Presentation-only loan/copy status. This enum never computes whether
 * something IS due-soon or overdue — that decision already lives in
 * ViewModels/repository. Callers pass in whichever [LoanStatus] already
 * applies, derived from booleans their ViewModel exposes.
 */
enum class LoanStatus { AVAILABLE, ON_LOAN, DUE_SOON, OVERDUE }

/** Pure status -> color lookup, kept separate from the `@Composable` wrappers below so it's plain-JUnit-testable. */
internal fun LoanStatus.statusColor(): Color = when (this) {
    LoanStatus.AVAILABLE -> LibraryStatus.available
    LoanStatus.ON_LOAN -> LibraryStatus.onLoan
    LoanStatus.DUE_SOON -> LibraryStatus.dueSoon
    LoanStatus.OVERDUE -> LibraryStatus.overdue
}

/** Pure status -> container-color lookup (see [statusColor]). */
internal fun LoanStatus.statusContainerColor(): Color = when (this) {
    LoanStatus.AVAILABLE -> LibraryStatus.availableContainer
    LoanStatus.ON_LOAN -> LibraryStatus.onLoanContainer
    LoanStatus.DUE_SOON -> LibraryStatus.dueSoonContainer
    LoanStatus.OVERDUE -> LibraryStatus.overdueContainer
}

@Composable
fun LoanStatus.color(): Color = statusColor()

@Composable
fun LoanStatus.containerColor(): Color = statusContainerColor()

/**
 * Localized label for this status. AVAILABLE/ON_LOAN reuse existing trilingual
 * strings for text that already exists verbatim elsewhere in the app (copy
 * availability on BookDetail/CopyPicker) — genuinely the same concept, so
 * reuse is fine there. DUE_SOON/OVERDUE use their own dedicated `status_*`
 * keys instead of the dashboard's due-soon section header / overdue stat-tile
 * keys: those are owned by a later dashboard-redesign task and may be
 * reworded independently of status-chip text.
 */
@Composable
fun LoanStatus.label(): String = when (this) {
    LoanStatus.AVAILABLE -> stringResource(R.string.available)
    LoanStatus.ON_LOAN -> stringResource(R.string.on_loan)
    LoanStatus.DUE_SOON -> stringResource(R.string.status_due_soon)
    LoanStatus.OVERDUE -> stringResource(R.string.status_overdue)
}

/** Small pill badge: a leading dot plus the status's localized label. */
@Composable
fun StatusBadge(status: LoanStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(status.containerColor())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(status.color()),
        )
        Text(
            status.label(),
            style = MaterialTheme.typography.labelMedium,
            color = status.color(),
        )
    }
}

/**
 * Same card language as [AppCard] (shape, elevation, content padding) plus a
 * 4dp status-colored leading edge flush against the card's start boundary.
 * Delegates to [AppCard] rather than reimplementing its styling: the incoming
 * [modifier] is passed straight to the inner [AppCard] (not the outer wrapping
 * [Box]) so a caller's `Modifier.fillMaxWidth()` reaches the real card surface
 * instead of producing a full-width invisible Box around a content-width card.
 * [AppCard] is measured normally (establishing the real size), and the
 * accent-edge overlay uses `matchParentSize()` plus the same
 * [MaterialTheme.shapes] token so its clip exactly follows AppCard's rounded
 * corners. Start-aligned so it flips to the trailing edge automatically under
 * RTL.
 */
@Composable
fun StatusEdgeCard(
    status: LoanStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box {
        AppCard(modifier = modifier, onClick = onClick, content = content)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.large),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(status.color()),
            )
        }
    }
}
