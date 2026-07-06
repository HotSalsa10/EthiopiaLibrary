package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.CurrentlyOutViewModel
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.RenewConfirmDialog
import java.util.Locale

/** Every book on loan right now, soonest-due first, with per-row renew and return. */
@Composable
fun CurrentlyOutScreen(vm: CurrentlyOutViewModel, onBack: () -> Unit) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    var renewTarget by remember { mutableStateOf<LoanWithDetails?>(null) }

    renewTarget?.let { target ->
        val preview by produceState<Long?>(null, target) { value = vm.renewPreviewDueAt() }
        RenewConfirmDialog(
            bookTitle = target.bookTitle,
            memberName = target.memberName,
            newDueAt = preview,
            locale = locale,
            onConfirm = {
                vm.renew(target.loan.id) {
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
            .padding(20.dp),
    ) {
        AppTopBar(stringResource(R.string.currently_out), onBack)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))

        if (loans.isEmpty()) {
            Text(
                stringResource(
                    if (query.isBlank()) R.string.no_books_out else R.string.no_matches,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(loans, key = { it.loan.id }) { loan ->
                    OutLoanCard(
                        item = loan,
                        locale = locale,
                        onRenew = { renewTarget = loan },
                        onReturn = {
                            vm.returnBook(loan.copyCode) {
                                Toast.makeText(context, R.string.return_success, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OutLoanCard(
    item: LoanWithDetails,
    locale: Locale,
    onRenew: () -> Unit,
    onReturn: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRenew) { Text(stringResource(R.string.renew)) }
            TextButton(onClick = onReturn) { Text(stringResource(R.string.nav_return)) }
        }
    }
}
