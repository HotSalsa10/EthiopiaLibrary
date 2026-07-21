package com.ethiopialibrary.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListViewModelsTest {

    private lateinit var db: LibraryDatabase
    private lateinit var clock: TestClock
    private lateinit var repo: LibraryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = TestClock()
        repo = LibraryRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(5_000) { flow.first { predicate(it) } } }

    @Test
    fun `books list filters by search query`() {
        runBlocking {
            repo.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am")
            repo.addBook(title = "Fiqir Eske Meqabir", author = "Haddis Alemayehu", categoryCode = "Fiction", language = "am")
        }
        val vm = BooksViewModel(repo)

        awaitValue(vm.books) { it.size == 2 }
        vm.setQuery("Oromay")

        val filtered = awaitValue(vm.books) { it.size == 1 }
        assertEquals("Oromay", filtered.single().book.title)
    }

    @Test
    fun `addBook creates book with requested number of copies`() {
        val vm = BooksViewModel(repo)

        vm.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am", isbn = null, copies = 3, volumes = 1)

        val row = awaitValue(vm.books) { it.size == 1 }.single()
        assertEquals(3, row.totalCopies)
        assertEquals(3, row.availableCopies)
    }

    @Test
    fun `addCategory reports success for a fresh code`() {
        val vm = BooksViewModel(repo)
        val result = MutableStateFlow<Boolean?>(null)

        vm.addCategory("Poetry", "PO") { duplicate -> result.value = duplicate }

        assertEquals(false, awaitValue(result) { it != null })
    }

    @Test
    fun `addCategory reports a duplicate instead of crashing on a repeated code`() {
        val vm = BooksViewModel(repo)
        val first = MutableStateFlow<Boolean?>(null)
        vm.addCategory("Poetry", "PO") { first.value = it }
        awaitValue(first) { it != null }
        // Room's Flow invalidation lags slightly behind the write completing,
        // so wait for the category to actually reach the StateFlow before
        // asserting the second (duplicate) attempt didn't add a second row.
        awaitValue(vm.categories) { cats -> cats.any { it.code == "PO" } }

        val second = MutableStateFlow<Boolean?>(null)
        vm.addCategory("Poetry Two", "PO") { second.value = it }

        assertEquals(true, awaitValue(second) { it != null })
        assertEquals(1, awaitValue(vm.categories) { true }.count { it.code == "PO" })
    }

    @Test
    fun `members list shows loan counts and new members`() {
        val vm = MembersViewModel(repo)

        vm.addMember(fullName = "Abebe Kebede", phone = "0911000000", nationalId = null, address = null)

        val members = awaitValue(vm.members) { it.size == 1 }
        assertEquals("Abebe Kebede", members.single().member.fullName)
        assertEquals(0, members.single().activeLoans)
    }

    @Test
    fun `dashboard exposes stats and overdue list`() {
        runBlocking {
            val book = repo.addBook(title = "Oromay", author = "A", categoryCode = "C", language = "am")
            val copy = repo.addCopy(book.id)
            val member = repo.registerMember(fullName = "Abebe")
            repo.checkout(copy.copyCode, member.memberCode)
            clock.advanceDays(20)
        }
        val vm = DashboardViewModel(repo)

        val stats = awaitValue(vm.stats) { it != null }
        assertEquals(1, stats?.totalBooks)
        assertEquals(1, stats?.overdueCount)

        val overdue = awaitValue(vm.overdue) { it.size == 1 }
        assertEquals("Oromay", overdue.single().bookTitle)
    }

    @Test
    fun `dashboard overdue filter narrows the list by book or member`() {
        runBlocking {
            val oromay = repo.addBook(title = "Oromay", author = "Bealu", categoryCode = "C", language = "am")
            val fiqir = repo.addBook(title = "Fiqir", author = "Haddis", categoryCode = "C", language = "am")
            val c1 = repo.addCopy(oromay.id)
            val c2 = repo.addCopy(fiqir.id)
            val abebe = repo.registerMember(fullName = "Abebe")
            val sara = repo.registerMember(fullName = "Sara")
            repo.checkout(c1.copyCode, abebe.memberCode)
            repo.checkout(c2.copyCode, sara.memberCode)
            clock.advanceDays(20)
        }
        val vm = DashboardViewModel(repo)
        awaitValue(vm.overdue) { it.size == 2 }

        vm.setOverdueQuery("Sara")

        val filtered = awaitValue(vm.overdue) { it.size == 1 }
        assertEquals("Fiqir", filtered.single().bookTitle)
    }

    @Test
    fun `dashboard exposes recent activity`() {
        runBlocking {
            val book = repo.addBook(title = "Oromay", author = "A", categoryCode = "C", language = "am")
            val copy = repo.addCopy(book.id)
            val member = repo.registerMember(fullName = "Abebe")
            repo.checkout(copy.copyCode, member.memberCode)
        }
        val vm = DashboardViewModel(repo)

        val activity = awaitValue(vm.recentActivity) { it.size == 1 }
        assertEquals("Oromay", activity.single().bookTitle)
    }

    @Test
    fun `dashboard undo reverts an activity entry`() {
        runBlocking {
            val book = repo.addBook(title = "Oromay", author = "A", categoryCode = "C", language = "am")
            val copy = repo.addCopy(book.id)
            val member = repo.registerMember(fullName = "Abebe")
            repo.checkout(copy.copyCode, member.memberCode)
        }
        val vm = DashboardViewModel(repo)
        val entryId = awaitValue(vm.recentActivity) { it.size == 1 }.single().entry.id

        vm.undoActivity(entryId)

        val activity = awaitValue(vm.recentActivity) { it.size == 2 }
        assertTrue(activity.any { it.entry.type == "UNDO" })
    }

    @Test
    fun `dashboard exposes backup status`() {
        runBlocking {
            repo.addBook(title = "Oromay", author = "A", categoryCode = "C", language = "am")
        }
        val vm = DashboardViewModel(repo)

        awaitValue(vm.pendingSync) { it > 0 }
        assertEquals(null, vm.lastBackupAt.value)

        runBlocking {
            com.ethiopialibrary.app.sync.SyncEngine(
                db,
                com.ethiopialibrary.app.sync.FakeCloudStore(),
                clock,
            ).drainOutbox()
        }

        awaitValue(vm.pendingSync) { it == 0 }
        awaitValue(vm.lastBackupAt) { it != null }
    }

    @Test
    fun `settings loads and updates loan period`() {
        val vm = SettingsViewModel(repo)

        awaitValue(vm.loanPeriodDays) { it == LibraryRepository.DEFAULT_LOAN_PERIOD_DAYS }
        vm.setLoanPeriodDays(30)

        awaitValue(vm.loanPeriodDays) { it == 30 }
        runBlocking { assertEquals(30, repo.loanPeriodDays()) }
    }

    // ---------- CurrentlyOutViewModel renew/return results (fix for C5) ----------

    private fun checkoutOneCopy(): Pair<String, String> = runBlocking {
        repo.addBookWithCopies(title = "Oromay", author = "A", categoryCode = "C", language = "am", copies = 1)
        val copyCode = repo.copyLabelRows().single().code
        val member = repo.registerMember(fullName = "Abebe")
        val loan = (repo.checkout(copyCode, member.memberCode) as com.ethiopialibrary.app.data.CheckoutResult.Success).loan
        copyCode to loan.id
    }

    @Test
    fun `renew reports success for an active loan`() {
        val (_, loanId) = checkoutOneCopy()
        val vm = CurrentlyOutViewModel(repo)
        val result = MutableStateFlow<com.ethiopialibrary.app.data.RenewResult?>(null)

        vm.renew(loanId) { result.value = it }

        assertTrue(awaitValue(result) { it != null } is com.ethiopialibrary.app.data.RenewResult.Success)
    }

    @Test
    fun `renew reports failure instead of a false success for a loan that is no longer active`() {
        val (copyCode, loanId) = checkoutOneCopy()
        runBlocking { repo.returnBook(copyCode) }
        val vm = CurrentlyOutViewModel(repo)
        val result = MutableStateFlow<com.ethiopialibrary.app.data.RenewResult?>(null)

        vm.renew(loanId) { result.value = it }

        assertTrue(awaitValue(result) { it != null } is com.ethiopialibrary.app.data.RenewResult.NotActive)
    }

    @Test
    fun `returnBook reports success for an active loan`() {
        val (copyCode, _) = checkoutOneCopy()
        val vm = CurrentlyOutViewModel(repo)
        val result = MutableStateFlow<Boolean?>(null)

        vm.returnBook(copyCode) { result.value = it }

        assertEquals(true, awaitValue(result) { it != null })
    }

    @Test
    fun `returnBook reports failure instead of a false success when there is no active loan`() {
        val (copyCode, _) = checkoutOneCopy()
        runBlocking { repo.returnBook(copyCode) } // already returned once
        val vm = CurrentlyOutViewModel(repo)
        val result = MutableStateFlow<Boolean?>(null)

        vm.returnBook(copyCode) { result.value = it }

        assertEquals(false, awaitValue(result) { it != null })
    }
}
