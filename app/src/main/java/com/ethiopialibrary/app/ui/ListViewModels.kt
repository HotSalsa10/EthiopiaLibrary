package com.ethiopialibrary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiopialibrary.app.data.BookWithCounts
import com.ethiopialibrary.app.data.DashboardStats
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.data.MemberWithLoanCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BooksViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val books: StateFlow<List<BookWithCounts>> = _query
        .flatMapLatest { repo.booksWithCounts(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun addBook(
        title: String,
        author: String,
        category: String,
        language: String,
        isbn: String?,
        copies: Int,
    ) {
        viewModelScope.launch {
            repo.addBookWithCopies(
                title = title,
                author = author,
                category = category,
                language = language,
                isbn = isbn?.takeIf { it.isNotBlank() },
                copies = copies,
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val members: StateFlow<List<MemberWithLoanCount>> = _query
        .flatMapLatest { repo.membersWithLoanCounts(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun addMember(fullName: String, phone: String?) {
        viewModelScope.launch {
            repo.registerMember(fullName = fullName, phone = phone?.takeIf { it.isNotBlank() })
        }
    }
}

class DashboardViewModel(repo: LibraryRepository) : ViewModel() {

    val stats: StateFlow<DashboardStats?> = repo.dashboardStats()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val overdue: StateFlow<List<LoanWithDetails>> = repo.overdueLoansDetailed()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Changes waiting to reach the cloud mirror. */
    val pendingSync: StateFlow<Int> = repo.pendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Epoch millis of the last successful backup, null if never. */
    val lastBackupAt: StateFlow<Long?> = repo.lastSyncAt()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

class SettingsViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _loanPeriodDays = MutableStateFlow<Int?>(null)
    val loanPeriodDays: StateFlow<Int?> = _loanPeriodDays.asStateFlow()

    init {
        viewModelScope.launch { _loanPeriodDays.value = repo.loanPeriodDays() }
    }

    fun setLoanPeriodDays(days: Int) {
        viewModelScope.launch {
            repo.setLoanPeriodDays(days)
            _loanPeriodDays.value = days
        }
    }
}
