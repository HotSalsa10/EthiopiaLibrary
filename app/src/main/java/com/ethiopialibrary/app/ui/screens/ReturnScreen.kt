package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.PageColumn
import com.ethiopialibrary.app.ui.ReturnViewModel
import com.ethiopialibrary.app.ui.StarRatingInput

@Composable
fun ReturnScreen(vm: ReturnViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val copyQuery by vm.copyQuery.collectAsStateWithLifecycle()
    val copyResults by vm.copyResults.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    // Not named among the three screens this redesign gives a landscape two-pane
    // layout (dashboard/checkout/currently-out) - Return just gets the max-width
    // column treatment, in every orientation.
    PageColumn {
        AppTopBar(stringResource(R.string.return_title), onBack)

        state.error?.let {
            ErrorCard(stringResource(R.string.error_no_active_loan))
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.returned != null && state.awaitingRating -> {
                ReturnSuccessCard(wasOverdue = state.wasOverdue == true) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.rate_member_prompt),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    StarRatingInput(onRate = { vm.rateMember(it) })
                    Spacer(Modifier.height(16.dp))
                    BigOutlinedButton(stringResource(R.string.skip)) { vm.skipRating() }
                }
            }

            state.returned != null -> {
                ReturnSuccessCard(wasOverdue = state.wasOverdue == true)
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.new_return)) { vm.reset() }
            }

            state.loan == null -> {
                CopyPickerStep(
                    query = copyQuery,
                    results = copyResults,
                    onQueryChange = vm::setCopyQuery,
                    onPick = vm::submitCopyCode,
                    // Return: only copies currently on loan are shown and selectable.
                    selectable = { it.onLoan },
                )
            }

            else -> {
                val loan = state.loan!!
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${loan.bookTitle} — ${loan.copyCode}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.borrowed_by, loan.memberName, loan.memberCode),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${stringResource(R.string.due_date)}: " +
                            DualCalendarFormatter.format(loan.loan.dueAt, locale, LocalCalendarMode.current),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.confirm_return), enabled = !state.inFlight) { vm.confirmReturn() }
            }
        }
    }
}

/**
 * [content], when given, renders inside this same card - e.g. the rating prompt
 * right below the returned-book heading, so the whole moment reads as one card
 * rather than a card followed by loose page content.
 */
@Composable
private fun ReturnSuccessCard(
    wasOverdue: Boolean,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = 20.dp,
    ) {
        Text(
            stringResource(R.string.return_success),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (wasOverdue) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.return_was_overdue),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        content?.invoke(this)
    }
}
