package com.ethiopialibrary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanEntity
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.data.ReturnResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Return desk flow: scan/type copy -> confirm return. */
class ReturnViewModel(private val repo: LibraryRepository) : ViewModel() {

    enum class ReturnUiError { NO_ACTIVE_LOAN }

    data class UiState(
        val loan: LoanWithDetails? = null,
        val returned: LoanEntity? = null,
        val wasOverdue: Boolean? = null,
        val error: ReturnUiError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun submitCopyCode(code: String) {
        viewModelScope.launch {
            val detail = repo.activeLoanDetailedForCopy(code)
            _state.update { current ->
                if (detail == null) {
                    current.copy(error = ReturnUiError.NO_ACTIVE_LOAN)
                } else {
                    current.copy(loan = detail, error = null)
                }
            }
        }
    }

    fun confirmReturn() {
        val loan = _state.value.loan ?: return
        viewModelScope.launch {
            when (val result = repo.returnBook(loan.copyCode)) {
                is ReturnResult.Success ->
                    _state.update { it.copy(returned = result.loan, wasOverdue = result.wasOverdue) }
                else ->
                    _state.update { it.copy(error = ReturnUiError.NO_ACTIVE_LOAN) }
            }
        }
    }

    fun reset() {
        _state.value = UiState()
    }
}
