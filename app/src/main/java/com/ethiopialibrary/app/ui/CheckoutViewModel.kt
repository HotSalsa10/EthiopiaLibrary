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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Checkout desk flow: scan/type copy -> scan/type member -> confirm. */
class CheckoutViewModel(private val repo: LibraryRepository) : ViewModel() {

    enum class CheckoutUiError {
        COPY_NOT_FOUND, COPY_NOT_AVAILABLE, MEMBER_NOT_FOUND, MEMBER_NOT_ACTIVE,
    }

    data class UiState(
        val copy: CopyWithBook? = null,
        val member: MemberEntity? = null,
        val completedLoan: LoanEntity? = null,
        val error: CheckoutUiError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

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
            _state.update { current ->
                when {
                    found == null ->
                        current.copy(error = CheckoutUiError.MEMBER_NOT_FOUND)
                    found.status != MemberStatus.ACTIVE ->
                        current.copy(error = CheckoutUiError.MEMBER_NOT_ACTIVE)
                    else ->
                        current.copy(member = found, error = null)
                }
            }
        }
    }

    fun confirm() {
        val snapshot = _state.value
        val copy = snapshot.copy ?: return
        val member = snapshot.member ?: return
        viewModelScope.launch {
            val error = when (val result = repo.checkout(copy.copy.copyCode, member.memberCode)) {
                is CheckoutResult.Success -> {
                    _state.update { it.copy(completedLoan = result.loan, error = null) }
                    return@launch
                }
                CheckoutResult.CopyNotFound -> CheckoutUiError.COPY_NOT_FOUND
                CheckoutResult.CopyNotAvailable -> CheckoutUiError.COPY_NOT_AVAILABLE
                CheckoutResult.MemberNotFound -> CheckoutUiError.MEMBER_NOT_FOUND
                CheckoutResult.MemberNotActive -> CheckoutUiError.MEMBER_NOT_ACTIVE
            }
            _state.update { it.copy(error = error) }
        }
    }

    fun reset() {
        _state.value = UiState()
    }

    /** Desk speed: keep the member locked in and just scan the next book. */
    fun startAnotherForSameMember() {
        _state.update { UiState(member = it.member) }
    }
}
