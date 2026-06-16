package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.SectionHeader
import java.util.Locale

/**
 * Returned-loan history shared by the book and member detail screens. Both show
 * the same thing — past loans, newest first — and differ only in which party
 * names the row, so [primaryLine] supplies that text (book + copy when viewing a
 * member; member when viewing a book). The list is already filtered to returned
 * loans and ordered by return date in the repository query.
 */
@Composable
fun BorrowingHistorySection(
    history: List<LoanWithDetails>,
    locale: Locale,
    primaryLine: (LoanWithDetails) -> String,
) {
    SectionHeader(stringResource(R.string.history_title))
    Spacer(Modifier.height(8.dp))
    if (history.isEmpty()) {
        Text(
            stringResource(R.string.no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        history.forEach { item ->
            AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Text(
                    primaryLine(item),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                item.loan.returnedAt?.let { returnedAt ->
                    Text(
                        stringResource(
                            R.string.returned_on,
                            DualCalendarFormatter.format(returnedAt, locale),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
