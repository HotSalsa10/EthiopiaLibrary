package com.ethiopialibrary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiopialibrary.app.data.CheckoutResult
import com.ethiopialibrary.app.data.CopyStatus
import com.ethiopialibrary.app.data.CopyWithBook
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanEntity
import com.ethiopialibrary.app.data.MemberEntity
import com.ethiopialibrary.app.data.MemberStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Checkout desk flow: search/scan copy -> scan/type member -> confirm. */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModel(private val repo: LibraryRepository) : ViewModel() {

    enum class CheckoutUiError {
        COPY_NOT_FOUND, COPY_NOT_AVAILABLE, MEMBER_NOT_FOUND, MEMBER_NOT_ACTIVE, LIMIT_REACHED,
    }

    data class UiState(
        val copy: CopyWithBook? = null,
        val member: MemberEntity? = null,
        val completedLoan: LoanEntity? = null,
        // Loan length for this checkout; prefilled from the setting, editable per loan.
        val loanPeriodDays: Int = LibraryRepository.DEFAULT_LOAN_PERIOD_DAYS,
        val error: CheckoutUiError? = null,
        // Overdue books this member already has; set when they're found, reset per member.
        // Staff acknowledge the warning to proceed - it never blocks by itself.
        val memberOverdueCount: Int = 0,
        val overdueWarningAcknowledged: Boolean = false,
        // True after confirm() hits LimitReached, prompting a staff-PIN override dialog.
        val awaitingPinOverride: Boolean = false,
        val pinOverrideError: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Copy-finder search (first step): type a book name/author/code, pick a copy.
    private val _copyQuery = MutableStateFlow("")
    val copyQuery: StateFlow<String> = _copyQuery.asStateFlow()

    val copyResults: StateFlow<List<CopyWithBook>> = _copyQuery
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.searchCopies(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val configured = repo.loanPeriodDays()
            _state.update { it.copy(loanPeriodDays = configured) }
        }
    }

    fun setCopyQuery(value: String) {
        _copyQuery.value = value
    }

    /** Staff override of the loan length for the current checkout. */
    fun setLoanPeriod(days: Int) {
        _state.update { it.copy(loanPeriodDays = days.coerceAtLeast(1)) }
    }

    fun submitCopyCode(code: String) {
        viewModelScope.launch {
            val found = repo.copyWithBook(code)
            _state.update { current ->
                when {
                    found == null ->
                        current.copy(error = CheckoutUiError.COPY_NOT_FOUND)
                    found.onLoan || found.copy.status != CopyStatus.IN_SERVICE ->
                        current.copy(error = CheckoutUiError.COPY_NOT_AVAILABLE)
                    else ->
                        current.copy(copy = found, error = null)
                }
            }
        }
    }

    fun submitMemberCode(code: String) {
        viewModelScope.launch {
            val found = repo.memberByCode(code)
            val overdueCount = if (found != null && found.status == MemberStatus.ACTIVE) {
                repo.overdueCountForMember(found.id)
            } else {
                0
            }
            _state.update { current ->
                when {
                    found == null ->
                        current.copy(error = CheckoutUiError.MEMBER_NOT_FOUND)
                    found.status != MemberStatus.ACTIVE ->
                        current.copy(error = CheckoutUiError.MEMBER_NOT_ACTIVE)
                    else ->
                        current.copy(
                            member = found,
                            error = null,
                            memberOverdueCount = overdueCount,
                            overdueWarningAcknowledged = false,
                        )
                }
            }
        }
    }

    /** Staff clicked through the "member has overdue books" warning. */
    fun acknowledgeOverdueWarning() {
        _state.update { it.copy(overdueWarningAcknowledged = true) }
    }

    fun confirm() {
        val snapshot = _state.value
        val copy = snapshot.copy ?: return
        val member = snapshot.member ?: return
        viewModelScope.launch {
            val period = snapshot.loanPeriodDays
            val error = when (val result = repo.checkout(copy.copy.copyCode, member.memberCode, period)) {
                is CheckoutResult.Success -> {
                    _state.update { it.copy(completedLoan = result.loan, error = null) }
                    return@launch
                }
                CheckoutResult.CopyNotFound -> CheckoutUiError.COPY_NOT_FOUND
                CheckoutResult.CopyNotAvailable -> CheckoutUiError.COPY_NOT_AVAILABLE
                CheckoutResult.MemberNotFound -> CheckoutUiError.MEMBER_NOT_FOUND
                CheckoutResult.MemberNotActive -> CheckoutUiError.MEMBER_NOT_ACTIVE
                CheckoutResult.LimitReached -> {
                    // Hard block becomes a staff-PIN-gated override instead of a dead end.
                    _state.update { it.copy(error = null, awaitingPinOverride = true, pinOverrideError = false) }
                    return@launch
                }
            }
            _state.update { it.copy(error = error) }
        }
    }

    /** Staff entered a PIN to check out this member over the borrowing limit. */
    fun confirmWithPinOverride(pin: String) {
        val snapshot = _state.value
        val copy = snapshot.copy ?: return
        val member = snapshot.member ?: return
        viewModelScope.launch {
            if (!repo.verifyStaffPin(pin)) {
                _state.update { it.copy(pinOverrideError = true) }
                return@launch
            }
            val period = snapshot.loanPeriodDays
            val result = repo.checkout(copy.copy.copyCode, member.memberCode, period, allowOverLimit = true)
            if (result is CheckoutResult.Success) {
                _state.update {
                    it.copy(
                        completedLoan = result.loan,
                        error = null,
                        awaitingPinOverride = false,
                        pinOverrideError = false,
                    )
                }
                return@launch
            }
            val error = when (result) {
                CheckoutResult.CopyNotFound -> CheckoutUiError.COPY_NOT_FOUND
                CheckoutResult.CopyNotAvailable -> CheckoutUiError.COPY_NOT_AVAILABLE
                CheckoutResult.MemberNotFound -> CheckoutUiError.MEMBER_NOT_FOUND
                CheckoutResult.MemberNotActive -> CheckoutUiError.MEMBER_NOT_ACTIVE
                // Unreachable: allowOverLimit=true bypasses this in repo.checkout().
                CheckoutResult.LimitReached -> CheckoutUiError.LIMIT_REACHED
                is CheckoutResult.Success -> return@launch
            }
            _state.update { it.copy(awaitingPinOverride = false, error = error) }
        }
    }

    /** Clears the stale wrong-PIN message as soon as staff starts a new attempt. */
    fun clearPinOverrideError() {
        _state.update { it.copy(pinOverrideError = false) }
    }

    /** Staff backed out of the PIN prompt; the original block still applies. */
    fun dismissPinOverride() {
        _state.update {
            it.copy(awaitingPinOverride = false, pinOverrideError = false, error = CheckoutUiError.LIMIT_REACHED)
        }
    }

    fun reset() {
        // Keep the loaded loan-period default so staff don't re-discover it.
        _state.update { UiState(loanPeriodDays = it.loanPeriodDays) }
        _copyQuery.value = ""
    }

    /**
     * Desk speed: keep the member locked in and just scan the next book. This flow never
     * calls submitMemberCode() again, so the overdue-warning state has to be carried over
     * explicitly or it would silently look like the member has no overdue books.
     */
    fun startAnotherForSameMember() {
        _state.update {
            UiState(
                member = it.member,
                loanPeriodDays = it.loanPeriodDays,
                memberOverdueCount = it.memberOverdueCount,
                overdueWarningAcknowledged = it.overdueWarningAcknowledged,
            )
        }
        _copyQuery.value = ""
    }
}
