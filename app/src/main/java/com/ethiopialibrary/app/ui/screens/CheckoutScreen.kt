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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.ethiopialibrary.app.ui.AddMemberDialog
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.CheckoutViewModel
import com.ethiopialibrary.app.ui.CodeEntry
import com.ethiopialibrary.app.ui.PinOverrideDialog
import com.ethiopialibrary.app.ui.theme.LibraryStatus

@Composable
fun CheckoutScreen(vm: CheckoutViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val copyQuery by vm.copyQuery.collectAsStateWithLifecycle()
    val copyResults by vm.copyResults.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var showBatch by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        AppTopBar(
            stringResource(if (showBatch) R.string.batch_checkout_title else R.string.checkout_title),
            onBack = if (showBatch) {
                { vm.resetBatch(); showBatch = false }
            } else {
                onBack
            },
        )

        if (showBatch) {
            BatchCheckoutSection(vm, onExit = { showBatch = false })
            return@Column
        }

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
                Spacer(Modifier.height(16.dp))
                BigOutlinedButton(stringResource(R.string.batch_checkout_start)) { showBatch = true }
            }

            state.member == null -> {
                FoundCopyCard(state.copy!!)
                Spacer(Modifier.height(16.dp))
                CodeEntry(stringResource(R.string.enter_member_code), vm::submitMemberCode)
                if (state.error == CheckoutViewModel.CheckoutUiError.MEMBER_NOT_FOUND) {
                    Spacer(Modifier.height(8.dp))
                    BigOutlinedButton(stringResource(R.string.add_member)) { showAddMember = true }
                }
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

    if (showAddMember) {
        AddMemberDialog(
            onDismiss = { showAddMember = false },
            onSave = { name, phone, nationalId, address ->
                vm.quickAddMember(name, phone, nationalId, address)
                showAddMember = false
            },
        )
    }
}

/** Member-first basket: scan the member once, then scan books repeatedly, then Confirm All. */
@Composable
private fun BatchCheckoutSection(vm: CheckoutViewModel, onExit: () -> Unit) {
    val state by vm.batchState.collectAsStateWithLifecycle()
    val results = state.results
    // CodeEntry doesn't clear its own text after submit (fine for the one-shot steps
    // elsewhere, which unmount on success) - here it's reused for every scan, so force a
    // fresh instance after each attempt by keying on an attempt counter.
    var attempt by remember { mutableStateOf(0) }
    var showAddMember by remember { mutableStateOf(false) }

    when {
        results != null -> {
            Text(stringResource(R.string.batch_results_heading), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            results.forEach { line ->
                val success = line.outcome == CheckoutViewModel.BatchLineOutcome.SUCCESS
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (success) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Text("${line.bookTitle} — " + stringResource(if (success) R.string.batch_line_success else R.string.batch_line_failed))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            BigButton(stringResource(R.string.batch_new)) { vm.resetBatch() }
            Spacer(Modifier.height(8.dp))
            BigOutlinedButton(stringResource(R.string.batch_done)) { vm.resetBatch(); onExit() }
        }

        state.member == null -> {
            state.memberError?.let {
                ErrorCard(checkoutErrorText(it))
                Spacer(Modifier.height(12.dp))
            }
            CodeEntry(stringResource(R.string.enter_member_code), vm::submitBatchMemberCode)
            if (state.memberError == CheckoutViewModel.CheckoutUiError.MEMBER_NOT_FOUND) {
                Spacer(Modifier.height(8.dp))
                BigOutlinedButton(stringResource(R.string.add_member)) { showAddMember = true }
            }
        }

        else -> {
            FoundMemberCard(state.member!!)
            Spacer(Modifier.height(16.dp))

            if (state.items.isNotEmpty()) {
                Text(stringResource(R.string.batch_items_heading), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.items.forEach { line ->
                    FoundCopyCard(line.copy)
                    Spacer(Modifier.height(8.dp))
                }
            }

            state.copyError?.let {
                ErrorCard(checkoutErrorText(it))
                Spacer(Modifier.height(12.dp))
            }

            key(attempt) {
                CodeEntry(stringResource(R.string.enter_copy_code)) { code ->
                    vm.addBatchCopyCode(code)
                    attempt++
                }
            }

            if (state.items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.batch_confirm_all)) { vm.confirmBatch() }
            }
        }
    }

    if (showAddMember) {
        AddMemberDialog(
            onDismiss = { showAddMember = false },
            onSave = { name, phone, nationalId, address ->
                vm.quickAddBatchMember(name, phone, nationalId, address)
                showAddMember = false
            },
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

/** Acknowledge-to-continue warning (Koha pattern): informs, never blocks by itself. */
@Composable
internal fun OverdueWarningCard(overdueCount: Int, onContinue: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = LibraryStatus.dueSoonContainer) {
        Text(
            stringResource(R.string.overdue_warning, overdueCount),
            style = MaterialTheme.typography.titleMedium,
            color = LibraryStatus.dueSoon,
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
