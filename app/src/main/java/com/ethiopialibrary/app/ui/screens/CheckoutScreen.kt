package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import com.ethiopialibrary.app.ui.ScannerView

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
                )
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
                LoanPeriodField(state.loanPeriodDays) { vm.setLoanPeriod(it) }
                Spacer(Modifier.height(16.dp))
                BigButton(stringResource(R.string.confirm_checkout)) { vm.confirm() }
            }
        }
    }
}

/**
 * First checkout step: scan a QR, or search by book title/author/code and pick the
 * matching copy. All matches are listed with their status; only available copies
 * (in service and not already out) are tappable.
 */
@Composable
private fun CopyPickerStep(
    query: String,
    results: List<CopyWithBook>,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    var scanning by remember { mutableStateOf(false) }

    if (scanning) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            ScannerView(onCode = { code ->
                scanning = false
                onPick(code)
            })
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.point_camera), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        BigOutlinedButton(stringResource(R.string.stop_scan)) { scanning = false }
    } else {
        BigButton(stringResource(R.string.scan)) { scanning = true }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.search_copy_hint)) },
        singleLine = true,
    )
    Spacer(Modifier.height(12.dp))
    if (query.isNotBlank() && results.isEmpty()) {
        Text(
            stringResource(R.string.no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    results.forEach { row ->
        CopyResultCard(row, onPick)
        Spacer(Modifier.height(8.dp))
    }
}

/** One search result: book title, copy code (+ shelf), and loan/condition status. */
@Composable
private fun CopyResultCard(row: CopyWithBook, onPick: (String) -> Unit) {
    val available = row.copy.status == CopyStatus.IN_SERVICE && !row.onLoan
    val statusRes = when {
        row.onLoan -> R.string.on_loan
        row.copy.status == CopyStatus.IN_SERVICE -> R.string.available
        row.copy.status == CopyStatus.LOST -> R.string.copy_status_lost
        row.copy.status == CopyStatus.DAMAGED -> R.string.copy_status_damaged
        else -> R.string.copy_status_retired
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (available) {
            { onPick(row.copy.copyCode) }
        } else {
            null
        },
    ) {
        Text(
            row.bookTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (available) MaterialTheme.colorScheme.onSurface else muted,
        )
        Text(
            row.copy.copyCode + (row.copy.shelfLocation?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
            color = muted,
        )
        Text(
            stringResource(statusRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (available) MaterialTheme.colorScheme.primary else muted,
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
