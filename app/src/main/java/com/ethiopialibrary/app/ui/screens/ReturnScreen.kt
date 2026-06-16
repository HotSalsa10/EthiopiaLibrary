package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.CodeEntry
import com.ethiopialibrary.app.ui.ReturnViewModel

@Composable
fun ReturnScreen(vm: ReturnViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        AppTopBar(stringResource(R.string.return_title), onBack)

        state.error?.let {
            ErrorCard(stringResource(R.string.error_no_active_loan))
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.returned != null -> {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentPadding = 20.dp,
                ) {
                    Text(
                        stringResource(R.string.return_success),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (state.wasOverdue == true) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.return_was_overdue),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.new_return)) { vm.reset() }
            }

            state.loan == null -> {
                CodeEntry(stringResource(R.string.enter_copy_code), vm::submitCopyCode)
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
                BigButton(stringResource(R.string.confirm_return)) { vm.confirmReturn() }
            }
        }
    }
}
