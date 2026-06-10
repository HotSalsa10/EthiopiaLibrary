package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.CopyWithBook
import com.ethiopialibrary.app.data.MemberEntity
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.CheckoutViewModel
import com.ethiopialibrary.app.ui.CodeEntry

@Composable
fun CheckoutScreen(vm: CheckoutViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AppTopBar(stringResource(R.string.checkout_title), onBack)

        state.error?.let { error ->
            ErrorCard(checkoutErrorText(error))
            Spacer(Modifier.height(12.dp))
        }

        val loan = state.completedLoan
        when {
            loan != null -> {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.checkout_success),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        state.copy?.let {
                            Text(
                                "${it.bookTitle} — ${it.copy.copyCode}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            "${stringResource(R.string.due_date)}: " +
                                DualCalendarFormatter.format(loan.dueAt, locale),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.new_checkout)) { vm.reset() }
            }

            state.copy == null -> {
                CodeEntry(stringResource(R.string.enter_copy_code), vm::submitCopyCode)
            }

            state.member == null -> {
                FoundCopyCard(state.copy!!)
                Spacer(Modifier.height(16.dp))
                CodeEntry(stringResource(R.string.enter_member_code), vm::submitMemberCode)
            }

            else -> {
                FoundCopyCard(state.copy!!)
                Spacer(Modifier.height(8.dp))
                FoundMemberCard(state.member!!)
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.confirm_checkout)) { vm.confirm() }
            }
        }
    }
}

@Composable
internal fun FoundCopyCard(copy: CopyWithBook) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(copy.bookTitle, style = MaterialTheme.typography.titleMedium)
            Text(copy.bookAuthor, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${stringResource(R.string.copy_code)}: ${copy.copy.copyCode}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
internal fun FoundMemberCard(member: MemberEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(member.fullName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${stringResource(R.string.member_code)}: ${member.memberCode}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
internal fun ErrorCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun checkoutErrorText(error: CheckoutViewModel.CheckoutUiError): String = stringResource(
    when (error) {
        CheckoutViewModel.CheckoutUiError.COPY_NOT_FOUND -> R.string.error_copy_not_found
        CheckoutViewModel.CheckoutUiError.COPY_NOT_AVAILABLE -> R.string.error_copy_not_available
        CheckoutViewModel.CheckoutUiError.MEMBER_NOT_FOUND -> R.string.error_member_not_found
        CheckoutViewModel.CheckoutUiError.MEMBER_NOT_ACTIVE -> R.string.error_member_not_active
    },
)
