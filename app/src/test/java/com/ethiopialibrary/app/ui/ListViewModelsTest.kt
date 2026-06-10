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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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
            repo.addBook(title = "Oromay", author = "Bealu Girma", category = "Fiction", language = "am")
            repo.addBook(title = "Fiqir Eske Meqabir", author = "Haddis Alemayehu", category = "Fiction", language = "am")
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

        vm.addBook(title = "Oromay", author = "Bealu Girma", category = "Fiction", language = "am", isbn = null, copies = 3)

        val row = awaitValue(vm.books) { it.size == 1 }.single()
        assertEquals(3, row.totalCopies)
        assertEquals(3, row.availableCopies)
    }

    @Test
    fun `members list shows loan counts and new members`() {
        val vm = MembersViewModel(repo)

        vm.addMember(fullName = "Abebe Kebede", phone = "0911000000")

        val members = awaitValue(vm.members) { it.size == 1 }
        assertEquals("Abebe Kebede", members.single().member.fullName)
        assertEquals(0, members.single().activeLoans)
    }

    @Test
    fun `dashboard exposes stats and overdue list`() {
        runBlocking {
            val book = repo.addBook(title = "Oromay", author = "A", category = "C", language = "am")
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
    fun `settings loads and updates loan period`() {
        val vm = SettingsViewModel(repo)

        awaitValue(vm.loanPeriodDays) { it == LibraryRepository.DEFAULT_LOAN_PERIOD_DAYS }
        vm.setLoanPeriodDays(30)

        awaitValue(vm.loanPeriodDays) { it == 30 }
        runBlocking { assertEquals(30, repo.loanPeriodDays()) }
    }
}
