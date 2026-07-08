package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.CopyStatus
import com.ethiopialibrary.app.data.CopyWithBook
import com.ethiopialibrary.app.data.MemberEntity
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.CheckoutViewModel
import com.ethiopialibrary.app.ui.CodeEntry
import com.ethiopialibrary.app.ui.PinOverrideDialog

@Composable
fun CheckoutScreen(vm: CheckoutViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val copyQuery by vm.copyQuery.collectAsStateWithLifecycle()
    val copyResults by vm.copyResults.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        AppTopBar(stringResource(R.string.checkout_title), onBack)

        state.error?.let { error ->
            ErrorCard(checkoutErrorText(error))
            Spacer(Modifier.height(12.dp))
        }

        val loan = state.completedLoan
        when {
            loan != null -> {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentPadding = 20.dp,
                ) {
                    Text(
                        stringResource(R.string.checkout_success),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.copy?.let {
                        Text(
                            "${it.bookTitle} — ${it.copy.copyCode}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "${stringResource(R.string.due_date)}: " +
                            DualCalendarFormatter.format(loan.dueAt, locale, LocalCalendarMode.current),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.borrow_another)) {
                    vm.startAnotherForSameMember()
                }
                Spacer(Modifier.height(8.dp))
                BigOutlinedButton(stringResource(R.string.new_checkout)) { vm.reset() }
            }

            state.copy == null -> {
                CopyPickerStep(
                    query = copyQuery,
                    results = copyResults,
                    onQueryChange = vm::setCopyQuery,
                    onPick = vm::submitCopyCode,
                    // Checkout: only copies available to loan are selectable.
                    selectable = { it.copy.status == CopyStatus.IN_SERVICE && !it.onLoan },
                )
            }

            state.member == null -> {
                FoundCopyCard(state.copy!!)
                Spacer(Modifier.height(16.dp))
                CodeEntry(stringResource(R.string.enter_member_code), vm::submitMemberCode)
            }

            state.memberOverdueCount > 0 && !state.overdueWarningAcknowledged -> {
                FoundCopyCard(state.copy!!)
                Spacer(Modifier.height(8.dp))
                FoundMemberCard(state.member!!)
                Spacer(Modifier.height(16.dp))
                OverdueWarningCard(state.memberOverdueCount) { vm.acknowledgeOverdueWarning() }
            }

            else -> {
                FoundCopyCard(state.copy!!)
                Spacer(Modifier.height(8.dp))
                FoundMemberCard(state.member!!)
                Spacer(Modifier.height(16.dp))
                LoanPeriodField(state.loanPeriodDays) { vm.setLoanPeriod(it) }
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.confirm_checkout)) { vm.confirm() }
            }
        }
    }

    if (state.awaitingPinOverride) {
        PinOverrideDialog(
            wrongPin = state.pinOverrideError,
            onConfirm = vm::confirmWithPinOverride,
            onPinChanged = vm::clearPinOverrideError,
            onDismiss = vm::dismissPinOverride,
        )
    }
}

/**
 * Editable loan length for this checkout, prefilled from the configured default.
 * Digits-only; an empty field is treated as the minimum so the loan is never zero.
 */
@Composable
private fun LoanPeriodField(initialDays: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(initialDays.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(4)
            text = digits
            onChange(digits.toIntOrNull() ?: 1)
        },
        label = { Text(stringResource(R.string.settings_loan_period)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun FoundCopyCard(copy: CopyWithBook) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(copy.bookTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(copy.bookAuthor, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${stringResource(R.string.copy_code)}: ${copy.copy.copyCode}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun FoundMemberCard(member: MemberEntity) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(member.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${stringResource(R.string.member_code)}: ${member.memberCode}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// No amber/warning tone exists in the theme yet (overdue is styled as an error, i.e.
// red) - defined locally the same way StarGold is, rather than widening the palette.
private val WarningContainer = Color(0xFFFFF3CD)
private val OnWarningContainer = Color(0xFF7A5B00)

/** Acknowledge-to-continue warning (Koha pattern): informs, never blocks by itself. */
@Composable
internal fun OverdueWarningCard(overdueCount: Int, onContinue: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = WarningContainer) {
        Text(
            stringResource(R.string.overdue_warning, overdueCount),
            style = MaterialTheme.typography.titleMedium,
            color = OnWarningContainer,
        )
    }
    Spacer(Modifier.height(16.dp))
    BigButton(stringResource(R.string.overdue_warning_continue)) { onContinue() }
}

@Composable
internal fun ErrorCard(message: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
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
        CheckoutViewModel.CheckoutUiError.LIMIT_REACHED -> R.string.error_limit_reached
    },
)
