package com.ethiopialibrary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.ActivityWithDetails
import com.ethiopialibrary.app.data.AddCategoryResult
import com.ethiopialibrary.app.data.BookWithCounts
import com.ethiopialibrary.app.data.CategoryEntity
import com.ethiopialibrary.app.data.DashboardStats
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LibraryStatistics
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.data.MemberWithLoanCount
import com.ethiopialibrary.app.data.RenewResult
import com.ethiopialibrary.app.data.ReturnResult
import com.ethiopialibrary.app.dates.CalendarMode
import com.ethiopialibrary.app.sync.RemoteDirectives
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
        volumes: Int,
    ) {
        viewModelScope.safeLaunch {
            repo.addBookWithCopies(
                title = title,
                author = author,
                categoryCode = categoryCode,
                language = language,
                isbn = isbn?.takeIf { it.isNotBlank() },
                copies = copies,
                volumes = volumes,
            )
        }
    }

    /** [onResult] gets true when [code] is already taken, so the dialog can show an
     * inline error and stay open instead of crashing on the unique-index violation. */
    fun addCategory(name: String, code: String, onResult: (duplicate: Boolean) -> Unit = {}) {
        viewModelScope.safeLaunch {
            onResult(repo.addCategory(name, code) is AddCategoryResult.DuplicateCode)
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

    fun addMember(fullName: String, phone: String?, nationalId: String?, address: String?) {
        viewModelScope.safeLaunch {
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

    /** Live clock-sanity gate (R3): true blocks new checkouts/renews and shows a Dashboard banner. */
    val clockWrong: StateFlow<Boolean> = repo.clockWrong()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
    val dueSoon: StateFlow<List<LoanWithDetails>> = repo.dueSoonLoans()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Today's desk activity feed, newest first. */
    val recentActivity: StateFlow<List<ActivityWithDetails>> = repo.recentActivity()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** [onResult] gets false when the entry is stale (shadowed by a later action on
     * the same loan, already undone, or the loan's state no longer matches) - the
     * caller shows a toast instead of pretending the undo silently succeeded. */
    fun undoActivity(activityId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.safeLaunch { onResult(repo.undoActivity(activityId)) }
    }

    /** Changes waiting to reach the cloud mirror. */
    val pendingSync: StateFlow<Int> = repo.pendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Epoch millis of the last successful backup, null if never. */
    val lastBackupAt: StateFlow<Long?> = repo.lastSyncAt()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** When the oldest un-backed-up change was made; null when fully synced. */
    val backupStaleSince: StateFlow<Long?> = repo.oldestPendingSince()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Data-side backup suggestion; the screen adds the connectivity condition. */
    val backupNudgeWanted: StateFlow<Boolean> = repo.backupNudgeWanted()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun dismissBackupNudge() {
        viewModelScope.safeLaunch { repo.dismissBackupNudgeForToday() }
    }

    /** Config-from-cloud cache; refreshed after every successful backup drain. */
    val remoteDirectives: StateFlow<RemoteDirectives> = repo.remoteDirectives()
        .stateIn(viewModelScope, SharingStarted.Eagerly, RemoteDirectives())

    val dismissedAnnouncementId: StateFlow<String?> = repo.dismissedAnnouncementId()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun dismissAnnouncement(id: String) {
        viewModelScope.safeLaunch { repo.dismissAnnouncement(id) }
    }
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
        viewModelScope.safeLaunch {
            repo.setLoanPeriodDays(days)
            _loanPeriodDays.value = days
        }
    }

    fun setMaxBooks(value: Int) {
        viewModelScope.safeLaunch {
            repo.setMaxBooksPerMember(value)
            _maxBooks.value = value
        }
    }

    fun setDueSoonDays(value: Int) {
        viewModelScope.safeLaunch {
            repo.setDueSoonDays(value)
            _dueSoonDays.value = value
        }
    }

    fun setCalendarMode(mode: CalendarMode) {
        viewModelScope.safeLaunch {
            repo.setCalendarMode(mode)
            _calendarMode.value = mode
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentlyOutViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Every book on loan right now, soonest-due first, filtered by [query]. */
    val loans: StateFlow<List<LoanWithDetails>> = _query
        .flatMapLatest { repo.currentlyOutLoans(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Configured due-soon window, so the screen can classify each loan into
     * due-soon/overdue/plain-on-loan without inventing a second, inconsistent
     * definition of "due soon". Same repo call [SettingsViewModel] already makes.
     */
    private val _dueSoonDays = MutableStateFlow<Int?>(null)
    val dueSoonDays: StateFlow<Int?> = _dueSoonDays.asStateFlow()

    /** Configured loan period, used to prefill the renew dialog's editable days field. */
    private val _loanPeriodDays = MutableStateFlow<Int?>(null)
    val loanPeriodDays: StateFlow<Int?> = _loanPeriodDays.asStateFlow()

    init {
        viewModelScope.launch {
            _dueSoonDays.value = repo.dueSoonDays()
            _loanPeriodDays.value = repo.loanPeriodDays()
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Due date renewing with [periodDays] would set, for the confirm dialog's preview. */
    suspend fun renewPreviewDueAt(loanId: String, periodDays: Int): Long = repo.renewalPreviewDueAt(loanId, periodDays)

    /** [onDone] gets the outcome so the caller can show the specific reason a
     * renewal failed (already returned/undone/deleted, or the clock gate). */
    fun renew(loanId: String, periodDays: Int, onDone: (RenewResult) -> Unit) {
        viewModelScope.safeLaunch {
            onDone(repo.renewLoan(loanId, periodDays))
        }
    }

    /** [onDone] gets false when there was no active loan on this copy - the
     * caller must not report a false success. */
    fun returnBook(copyCode: String, onDone: (Boolean) -> Unit) {
        viewModelScope.safeLaunch {
            onDone(repo.returnBook(copyCode) is ReturnResult.Success)
        }
    }
}

/** Toast message for a renew attempt; shared by every renew call site (Dashboard, member detail, currently-out). */
fun renewResultMessageRes(result: RenewResult): Int = when (result) {
    is RenewResult.Success -> R.string.renew_done
    RenewResult.NotActive -> R.string.error_renew_not_active
    RenewResult.ClockWrong -> R.string.error_clock_wrong
}

class StatisticsViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _stats = MutableStateFlow<LibraryStatistics?>(null)
    val stats: StateFlow<LibraryStatistics?> = _stats.asStateFlow()

    init {
        viewModelScope.launch { _stats.value = repo.computeStatistics() }
    }
}
