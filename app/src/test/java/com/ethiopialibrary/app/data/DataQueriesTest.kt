package com.ethiopialibrary.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ethiopialibrary.app.dates.CalendarMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reactive queries and projections that feed the Phase 2 UI. */
@RunWith(RobolectricTestRunner::class)
class DataQueriesTest {

    private lateinit var db: LibraryDatabase
    private lateinit var clock: TestClock
    private lateinit var repo: LibraryRepository

    @Before
    fun setUp() {
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
    }

    @Test
    fun `book search matches title or author`() = runBlocking {
        repo.addBook(title = "Fiqir Eske Meqabir", author = "Haddis Alemayehu", categoryCode = "Fiction", language = "am")
        repo.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am")

        assertEquals(2, repo.booksWithCounts("").first().size)
        assertEquals("Oromay", repo.booksWithCounts("oromay").first().single().book.title)
        assertEquals("Fiqir Eske Meqabir", repo.booksWithCounts("Haddis").first().single().book.title)
    }

    @Test
    fun `book counts exclude lost copies and copies on loan`() = runBlocking {
        val book = repo.addBook(title = "T", author = "A", categoryCode = "C", language = "am")
        repo.addCopy(book.id)
        val lost = repo.addCopy(book.id)
        val loaned = repo.addCopy(book.id)
        repo.setCopyStatus(lost.id, CopyStatus.LOST)
        val member = repo.registerMember(fullName = "Abebe")
        repo.checkout(loaned.copyCode, member.memberCode)

        val row = repo.booksWithCounts("").first().single()

        assertEquals(2, row.totalCopies)
        assertEquals(1, row.availableCopies)
    }

    @Test
    fun `copy rows expose loan state`() = runBlocking {
        val book = repo.addBook(title = "T", author = "A", categoryCode = "C", language = "am")
        val c1 = repo.addCopy(book.id)
        val c2 = repo.addCopy(book.id)
        val member = repo.registerMember(fullName = "Abebe")
        repo.checkout(c1.copyCode, member.memberCode)

        val rows = repo.copiesForBook(book.id).first().associateBy { it.copy.copyCode }

        assertTrue(rows.getValue(c1.copyCode).onLoan)
        assertFalse(rows.getValue(c2.copyCode).onLoan)
    }

    @Test
    fun `member search includes active loan count`() = runBlocking {
        val book = repo.addBook(title = "T", author = "A", categoryCode = "C", language = "am")
        val c1 = repo.addCopy(book.id)
        val c2 = repo.addCopy(book.id)
        val c3 = repo.addCopy(book.id)
        val abebe = repo.registerMember(fullName = "Abebe Kebede")
        repo.registerMember(fullName = "Sara Tesfaye")
        repo.checkout(c1.copyCode, abebe.memberCode)
        repo.checkout(c2.copyCode, abebe.memberCode)
        repo.checkout(c3.copyCode, abebe.memberCode)
        repo.returnBook(c3.copyCode)

        val all = repo.membersWithLoanCounts("").first()
        assertEquals(2, all.size)

        val found = repo.membersWithLoanCounts("Abebe").first().single()
        assertEquals(abebe.id, found.member.id)
        assertEquals(2, found.activeLoans)
    }

    @Test
    fun `overdue details include book title and member name`() = runBlocking {
        val book = repo.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am")
        val copy = repo.addCopy(book.id)
        val member = repo.registerMember(fullName = "Abebe Kebede")
        repo.checkout(copy.copyCode, member.memberCode)
        clock.advanceDays(20)

        val overdue = repo.overdueLoansDetailed().first().single()

        assertEquals("Oromay", overdue.bookTitle)
        assertEquals(copy.copyCode, overdue.copyCode)
        assertEquals("Abebe Kebede", overdue.memberName)
        assertEquals(member.memberCode, overdue.memberCode)
    }

    @Test
    fun `active loans for member are detailed`() = runBlocking {
        val book = repo.addBook(title = "T", author = "A", categoryCode = "C", language = "am")
        val c1 = repo.addCopy(book.id)
        val c2 = repo.addCopy(book.id)
        val member = repo.registerMember(fullName = "Abebe")
        repo.checkout(c1.copyCode, member.memberCode)
        repo.checkout(c2.copyCode, member.memberCode)
        repo.returnBook(c1.copyCode)

        val active = repo.activeLoansForMember(member.id).first()

        assertEquals(1, active.size)
        assertEquals(c2.copyCode, active.single().copyCode)
    }

    @Test
    fun `copy lookup for checkout preview returns book info and loan state`() = runBlocking {
        val book = repo.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am")
        val copy = repo.addCopy(book.id)
        val member = repo.registerMember(fullName = "Abebe")

        val before = repo.copyWithBook(copy.copyCode)
        assertEquals("Oromay", before?.bookTitle)
        assertEquals("Bealu Girma", before?.bookAuthor)
        assertEquals(false, before?.onLoan)

        repo.checkout(copy.copyCode, member.memberCode)
        assertEquals(true, repo.copyWithBook(copy.copyCode)?.onLoan)

        assertNull(repo.copyWithBook("B-9999"))
    }

    @Test
    fun `dashboard stats count books members loans and overdue`() = runBlocking {
        val b1 = repo.addBook(title = "T1", author = "A", categoryCode = "C", language = "am")
        val b2 = repo.addBook(title = "T2", author = "A", categoryCode = "C", language = "am")
        val c1 = repo.addCopy(b1.id)
        val c2 = repo.addCopy(b2.id)
        repo.addCopy(b2.id)
        val m1 = repo.registerMember(fullName = "M1")
        val m2 = repo.registerMember(fullName = "M2")
        repo.checkout(c1.copyCode, m1.memberCode)
        clock.advanceDays(20) // first loan is now overdue
        repo.checkout(c2.copyCode, m2.memberCode)

        val stats = repo.dashboardStats().first()

        assertEquals(2, stats.totalBooks)
        assertEquals(2, stats.totalMembers)
        assertEquals(2, stats.activeLoans)
        assertEquals(1, stats.overdueCount)
    }

    @Test
    fun `updateBook persists changes and writes outbox`() = runBlocking {
        val book = repo.addBook(title = "Old Title", author = "A", categoryCode = "C", language = "am")
        val entriesBefore = repo.pendingSyncEntries().count { it.entityType == "book" }

        repo.updateBook(book.copy(title = "New Title"))

        val updated = repo.booksWithCounts("New").first().single()
        assertEquals("New Title", updated.book.title)
        assertEquals(entriesBefore + 1, repo.pendingSyncEntries().count { it.entityType == "book" })
    }

    @Test
    fun `member lookup by code`() = runBlocking {
        val member = repo.registerMember(fullName = "Abebe")
        assertEquals(member.id, repo.memberByCode(member.memberCode)?.id)
        assertNull(repo.memberByCode("M-9999"))
    }

    @Test
    fun `active loan detail lookup by copy code`() = runBlocking {
        val book = repo.addBook(title = "Oromay", author = "A", categoryCode = "C", language = "am")
        val copy = repo.addCopy(book.id)
        val member = repo.registerMember(fullName = "Abebe")

        assertNull(repo.activeLoanDetailedForCopy(copy.copyCode))
        repo.checkout(copy.copyCode, member.memberCode)

        val detail = repo.activeLoanDetailedForCopy(copy.copyCode)
        assertEquals("Oromay", detail?.bookTitle)
        assertEquals("Abebe", detail?.memberName)
    }

    @Test
    fun `loan period round trips through settings`() = runBlocking {
        assertEquals(LibraryRepository.DEFAULT_LOAN_PERIOD_DAYS, repo.loanPeriodDays())
        repo.setLoanPeriodDays(21)
        assertEquals(21, repo.loanPeriodDays())
    }

    @Test
    fun `calendar mode round trips through settings and defaults to dual`() = runBlocking {
        assertEquals(CalendarMode.DUAL, repo.calendarMode())
        repo.setCalendarMode(CalendarMode.ETHIOPIAN)
        assertEquals(CalendarMode.ETHIOPIAN, repo.calendarMode())
    }

    @Test
    fun `label rows list in-service copies and all members`() = runBlocking {
        val book = repo.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am")
        val inService = repo.addCopy(book.id)
        val lost = repo.addCopy(book.id)
        repo.setCopyStatus(lost.id, CopyStatus.LOST)
        val member = repo.registerMember(fullName = "Abebe Kebede")

        val copyRows = repo.copyLabelRows()
        assertEquals(listOf(inService.copyCode), copyRows.map { it.code })
        assertEquals("Oromay", copyRows.single().title)

        val memberRows = repo.memberLabelRows()
        assertEquals(member.memberCode, memberRows.single().code)
        assertEquals("Abebe Kebede", memberRows.single().title)
    }

    @Test
    fun `entity lookups by id`() = runBlocking {
        val book = repo.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am")
        val member = repo.registerMember(fullName = "Abebe Kebede")

        assertEquals("Oromay", repo.bookById(book.id)?.title)
        assertEquals("Abebe Kebede", repo.memberById(member.id)?.fullName)
        assertNull(repo.bookById("no-such-id"))
        assertNull(repo.memberById("no-such-id"))
    }
}
