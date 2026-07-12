package com.ethiopialibrary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiopialibrary.app.data.CopyWithBook
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanEntity
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.data.ReturnResult
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

/** Return desk flow: search/scan copy -> confirm return -> rate member. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnViewModel(private val repo: LibraryRepository) : ViewModel() {

    enum class ReturnUiError { NO_ACTIVE_LOAN }

    data class UiState(
        val loan: LoanWithDetails? = null,
        val returned: LoanEntity? = null,
        val wasOverdue: Boolean? = null,
        // True after a return while staff are being prompted to rate the member.
        // The step is skippable, so this can clear without a rating being stored.
        val awaitingRating: Boolean = false,
        val error: ReturnUiError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Copy-finder search (first step): type a book name/author/code, pick a copy
    // that is currently on loan to return it.
    private val _copyQuery = MutableStateFlow("")
    val copyQuery: StateFlow<String> = _copyQuery.asStateFlow()

    val copyResults: StateFlow<List<CopyWithBook>> = _copyQuery
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.searchOnLoanCopies(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setCopyQuery(value: String) {
        _copyQuery.value = value
    }

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
        viewModelScope.safeLaunch {
            when (val result = repo.returnBook(loan.copyCode)) {
                is ReturnResult.Success ->
                    _state.update {
                        it.copy(
                            returned = result.loan,
                            wasOverdue = result.wasOverdue,
                            awaitingRating = true,
                        )
                    }
                else ->
                    _state.update { it.copy(error = ReturnUiError.NO_ACTIVE_LOAN) }
            }
        }
    }

    /** Records a 1–5 rating for the just-returned loan, then dismisses the prompt. */
    fun rateMember(stars: Int) {
        val loan = _state.value.returned ?: return
        viewModelScope.safeLaunch {
            repo.rateLoan(loan.id, stars)
            _state.update { it.copy(awaitingRating = false) }
        }
    }

    /** Dismisses the rating prompt without storing a rating. */
    fun skipRating() {
        _state.update { it.copy(awaitingRating = false) }
    }

    fun reset() {
        _state.value = UiState()
        _copyQuery.value = ""
    }
}
