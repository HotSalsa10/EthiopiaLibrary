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
import com.ethiopialibrary.app.data.MemberWithLoanCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Checkout desk flow: search/scan copy -> scan/type member -> confirm. */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModel(private val repo: LibraryRepository) : ViewModel() {

    enum class CheckoutUiError {
        COPY_NOT_FOUND, COPY_NOT_AVAILABLE, MEMBER_NOT_FOUND, MEMBER_NOT_ACTIVE, LIMIT_REACHED, CLOCK_WRONG,
    }

    enum class BatchLineOutcome { SUCCESS, FAILED }

    data class BatchLine(val copy: CopyWithBook)

    data class BatchResultLine(val bookTitle: String, val copyCode: String, val outcome: BatchLineOutcome)

    /** Member-first basket mode: one member, several books, one Confirm All. */
    data class BatchUiState(
        val member: MemberEntity? = null,
        val memberError: CheckoutUiError? = null,
        val items: List<BatchLine> = emptyList(),
        val copyError: CheckoutUiError? = null,
        // Null while still building the basket; set once Confirm All has run.
        val results: List<BatchResultLine>? = null,
        // True while confirmBatch()'s loop is in flight - blocks a double-tap
        // from re-running the whole basket a second time.
        val inFlight: Boolean = false,
    )

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
        // True while confirm()/confirmWithPinOverride()'s write is in flight - blocks
        // a double-tap from re-submitting the same checkout a second time.
        val inFlight: Boolean = false,
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

    // Member-finder search (second step): type a name or code, pick the member.
    private val _memberQuery = MutableStateFlow("")
    val memberQuery: StateFlow<String> = _memberQuery.asStateFlow()

    val memberResults: StateFlow<List<MemberWithLoanCount>> = _memberQuery
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.membersWithLoanCounts(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setMemberQuery(value: String) {
        _memberQuery.value = value
    }

    /** Staff override of the loan length for the current checkout. */
    fun setLoanPeriod(days: Int) {
        _state.update { it.copy(loanPeriodDays = days.coerceIn(1, LibraryRepository.MAX_LOAN_PERIOD_DAYS)) }
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

    /**
     * Registers a brand-new member on the spot (MEMBER_NOT_FOUND during checkout) and
     * adopts them as the current member, same as a successful submitMemberCode(). A
     * freshly created member can't have overdue loans, so there's nothing to look up.
     */
    fun quickAddMember(fullName: String, phone: String?, nationalId: String?, address: String?) {
        viewModelScope.safeLaunch {
            val created = repo.registerMember(fullName, phone, nationalId, address)
            _state.update {
                it.copy(
                    member = created,
                    error = null,
                    memberOverdueCount = 0,
                    overdueWarningAcknowledged = false,
                )
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
        if (snapshot.inFlight) return // a fast double-tap must not re-submit while the first is still writing
        _state.update { it.copy(inFlight = true) }
        viewModelScope.safeLaunch {
            val period = snapshot.loanPeriodDays
            val error = when (val result = repo.checkout(copy.copy.copyCode, member.memberCode, period)) {
                is CheckoutResult.Success -> {
                    _state.update { it.copy(completedLoan = result.loan, error = null, inFlight = false) }
                    return@safeLaunch
                }
                CheckoutResult.CopyNotFound -> CheckoutUiError.COPY_NOT_FOUND
                CheckoutResult.CopyNotAvailable -> CheckoutUiError.COPY_NOT_AVAILABLE
                CheckoutResult.MemberNotFound -> CheckoutUiError.MEMBER_NOT_FOUND
                CheckoutResult.MemberNotActive -> CheckoutUiError.MEMBER_NOT_ACTIVE
                CheckoutResult.ClockWrong -> CheckoutUiError.CLOCK_WRONG
                CheckoutResult.LimitReached -> {
                    // Hard block becomes a staff-PIN-gated override instead of a dead end.
                    _state.update {
                        it.copy(error = null, awaitingPinOverride = true, pinOverrideError = false, inFlight = false)
                    }
                    return@safeLaunch
                }
            }
            _state.update { it.copy(error = error, inFlight = false) }
        }
    }

    /** Staff entered a PIN to check out this member over the borrowing limit. */
    fun confirmWithPinOverride(pin: String) {
        val snapshot = _state.value
        val copy = snapshot.copy ?: return
        val member = snapshot.member ?: return
        if (snapshot.inFlight) return
        _state.update { it.copy(inFlight = true) }
        viewModelScope.safeLaunch {
            if (!repo.verifyStaffPin(pin)) {
                _state.update { it.copy(pinOverrideError = true, inFlight = false) }
                return@safeLaunch
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
                        inFlight = false,
                    )
                }
                return@safeLaunch
            }
            val error = when (result) {
                CheckoutResult.CopyNotFound -> CheckoutUiError.COPY_NOT_FOUND
                CheckoutResult.CopyNotAvailable -> CheckoutUiError.COPY_NOT_AVAILABLE
                CheckoutResult.MemberNotFound -> CheckoutUiError.MEMBER_NOT_FOUND
                CheckoutResult.MemberNotActive -> CheckoutUiError.MEMBER_NOT_ACTIVE
                CheckoutResult.ClockWrong -> CheckoutUiError.CLOCK_WRONG
                // Unreachable: allowOverLimit=true bypasses this in repo.checkout().
                CheckoutResult.LimitReached -> CheckoutUiError.LIMIT_REACHED
                is CheckoutResult.Success -> return@safeLaunch
            }
            _state.update { it.copy(awaitingPinOverride = false, error = error, inFlight = false) }
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
        _memberQuery.value = ""
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
        _memberQuery.value = ""
    }

    // ---------- batch checkout: member-first basket (Wave 5 item 4) ----------
    // Entirely separate state from the copy-first flow above, which stays untouched.

    private val _batchState = MutableStateFlow(BatchUiState())
    val batchState: StateFlow<BatchUiState> = _batchState.asStateFlow()

    fun submitBatchMemberCode(code: String) {
        viewModelScope.launch {
            val found = repo.memberByCode(code)
            _batchState.update { current ->
                when {
                    found == null -> current.copy(memberError = CheckoutUiError.MEMBER_NOT_FOUND)
                    found.status != MemberStatus.ACTIVE -> current.copy(memberError = CheckoutUiError.MEMBER_NOT_ACTIVE)
                    else -> current.copy(member = found, memberError = null)
                }
            }
        }
    }

    /** Batch-mode counterpart of quickAddMember(): registers and adopts the basket member. */
    fun quickAddBatchMember(fullName: String, phone: String?, nationalId: String?, address: String?) {
        viewModelScope.safeLaunch {
            val created = repo.registerMember(fullName, phone, nationalId, address)
            _batchState.update { it.copy(member = created, memberError = null) }
        }
    }

    /** Validates and lists one book; the limit is checked against DB loans plus this basket so far. */
    fun addBatchCopyCode(code: String) {
        val member = _batchState.value.member ?: return
        viewModelScope.launch {
            val found = repo.copyWithBook(code)
            if (found == null) {
                _batchState.update { it.copy(copyError = CheckoutUiError.COPY_NOT_FOUND) }
                return@launch
            }
            if (found.onLoan || found.copy.status != CopyStatus.IN_SERVICE) {
                _batchState.update { it.copy(copyError = CheckoutUiError.COPY_NOT_AVAILABLE) }
                return@launch
            }
            val basketSoFar = _batchState.value.items
            if (basketSoFar.any { it.copy.copy.copyCode == found.copy.copyCode }) {
                _batchState.update { it.copy(copyError = CheckoutUiError.COPY_NOT_AVAILABLE) }
                return@launch
            }
            val limit = repo.maxBooksPerMember()
            val activeCount = repo.activeLoansForMember(member.id).first().size
            if (limit > 0 && activeCount + basketSoFar.size >= limit) {
                _batchState.update { it.copy(copyError = CheckoutUiError.LIMIT_REACHED) }
                return@launch
            }
            _batchState.update { it.copy(items = it.items + BatchLine(found), copyError = null) }
        }
    }

    /** Checks every basket line out in order, collecting a per-line result instead of stopping at the first failure. */
    fun confirmBatch() {
        val snapshot = _batchState.value
        val member = snapshot.member ?: return
        if (snapshot.items.isEmpty() || snapshot.inFlight || snapshot.results != null) return
        _batchState.update { it.copy(inFlight = true) }
        viewModelScope.safeLaunch {
            val results = snapshot.items.map { line ->
                val result = repo.checkout(line.copy.copy.copyCode, member.memberCode)
                BatchResultLine(
                    bookTitle = line.copy.bookTitle,
                    copyCode = line.copy.copy.copyCode,
                    outcome = if (result is CheckoutResult.Success) BatchLineOutcome.SUCCESS else BatchLineOutcome.FAILED,
                )
            }
            _batchState.update { it.copy(results = results, inFlight = false) }
        }
    }

    fun resetBatch() {
        _batchState.value = BatchUiState()
        _memberQuery.value = ""
    }
}
