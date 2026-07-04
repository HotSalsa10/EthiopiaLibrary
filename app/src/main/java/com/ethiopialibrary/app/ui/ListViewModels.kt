package com.ethiopialibrary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiopialibrary.app.data.BookWithCounts
import com.ethiopialibrary.app.data.CategoryEntity
import com.ethiopialibrary.app.data.DashboardStats
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LibraryStatistics
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.data.MemberWithLoanCount
import com.ethiopialibrary.app.dates.CalendarMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BooksViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Category code to filter by; "" means show all categories. */
    private val _categoryFilter = MutableStateFlow("")
    val categoryFilter: StateFlow<String> = _categoryFilter.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = repo.categories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val books: StateFlow<List<BookWithCounts>> =
        combine(_query, _categoryFilter) { q, c -> q to c }
            .flatMapLatest { (q, c) -> repo.booksWithCounts(q, c) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setCategoryFilter(code: String) {
        _categoryFilter.value = code
    }

    fun addBook(
        title: String,
        author: String,
        categoryCode: String,
        language: String,
        isbn: String?,
        copies: Int,
    ) {
        viewModelScope.launch {
            repo.addBookWithCopies(
                title = title,
                author = author,
                categoryCode = categoryCode,
                language = language,
                isbn = isbn?.takeIf { it.isNotBlank() },
                copies = copies,
            )
        }
    }

    fun addCategory(name: String, code: String) {
        viewModelScope.launch { repo.addCategory(name, code) }
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

    fun addMember(fullName: String, phone: String?, nationalId: String?, address: String?) {
        viewModelScope.launch {
            repo.registerMember(
                fullName = fullName,
                phone = phone?.takeIf { it.isNotBlank() },
                nationalId = nationalId?.takeIf { it.isNotBlank() },
                address = address?.takeIf { it.isNotBlank() },
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val repo: LibraryRepository) : ViewModel() {

    val stats: StateFlow<DashboardStats?> = repo.dashboardStats()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Free-text filter for the overdue list: book title/author, copy code, or member. */
    private val _overdueQuery = MutableStateFlow("")
    val overdueQuery: StateFlow<String> = _overdueQuery.asStateFlow()

    val overdue: StateFlow<List<LoanWithDetails>> = _overdueQuery
        .flatMapLatest { q -> repo.overdueLoansDetailed(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setOverdueQuery(value: String) {
        _overdueQuery.value = value
    }

    /** Loans falling due within the configured window (not yet overdue). */
    val dueSoon: StateFlow<List<LoanWithDetails>> = flow { emitAll(repo.dueSoonLoans()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Changes waiting to reach the cloud mirror. */
    val pendingSync: StateFlow<Int> = repo.pendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Epoch millis of the last successful backup, null if never. */
    val lastBackupAt: StateFlow<Long?> = repo.lastSyncAt()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** When the oldest un-backed-up change was made; null when fully synced. */
    val backupStaleSince: StateFlow<Long?> = repo.oldestPendingSince()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

class SettingsViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _loanPeriodDays = MutableStateFlow<Int?>(null)
    val loanPeriodDays: StateFlow<Int?> = _loanPeriodDays.asStateFlow()

    private val _maxBooks = MutableStateFlow<Int?>(null)
    val maxBooks: StateFlow<Int?> = _maxBooks.asStateFlow()

    private val _dueSoonDays = MutableStateFlow<Int?>(null)
    val dueSoonDays: StateFlow<Int?> = _dueSoonDays.asStateFlow()

    private val _calendarMode = MutableStateFlow<CalendarMode?>(null)
    val calendarMode: StateFlow<CalendarMode?> = _calendarMode.asStateFlow()

    init {
        viewModelScope.launch {
            _loanPeriodDays.value = repo.loanPeriodDays()
            _maxBooks.value = repo.maxBooksPerMember()
            _dueSoonDays.value = repo.dueSoonDays()
            _calendarMode.value = repo.calendarMode()
        }
    }

    fun setLoanPeriodDays(days: Int) {
        viewModelScope.launch {
            repo.setLoanPeriodDays(days)
            _loanPeriodDays.value = days
        }
    }

    fun setMaxBooks(value: Int) {
        viewModelScope.launch {
            repo.setMaxBooksPerMember(value)
            _maxBooks.value = value
        }
    }

    fun setDueSoonDays(value: Int) {
        viewModelScope.launch {
            repo.setDueSoonDays(value)
            _dueSoonDays.value = value
        }
    }

    fun setCalendarMode(mode: CalendarMode) {
        viewModelScope.launch {
            repo.setCalendarMode(mode)
            _calendarMode.value = mode
        }
    }
}

class StatisticsViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _stats = MutableStateFlow<LibraryStatistics?>(null)
    val stats: StateFlow<LibraryStatistics?> = _stats.asStateFlow()

    init {
        viewModelScope.launch { _stats.value = repo.computeStatistics() }
    }
}
