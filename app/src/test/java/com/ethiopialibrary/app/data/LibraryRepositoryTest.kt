package com.ethiopialibrary.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {

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

    // ---------- helpers ----------

    private fun addBookWithCopies(copies: Int): Pair<BookEntity, List<BookCopyEntity>> = runBlocking {
        val book = repo.addBook(
            title = "ፍቅር እስከ መቃብር",
            author = "ሀዲስ ዓለማየሁ",
            category = "Fiction",
            language = "am",
        )
        book to (1..copies).map { repo.addCopy(book.id) }
    }

    private fun addMember(name: String = "Abebe Kebede"): MemberEntity =
        runBlocking { repo.registerMember(fullName = name) }

    // ---------- accession / member codes ----------

    @Test
    fun `copies receive sequential copy codes`() {
        val (_, copies) = addBookWithCopies(2)
        assertEquals("B-0001", copies[0].copyCode)
        assertEquals("B-0002", copies[1].copyCode)
    }

    @Test
    fun `copy code sequence continues across books`() = runBlocking {
        addBookWithCopies(2)
        val book2 = repo.addBook(title = "Second Book", author = "Author", category = "History", language = "en")
        val copy3 = repo.addCopy(book2.id)
        assertEquals("B-0003", copy3.copyCode)
    }

    @Test
    fun `members receive sequential member codes`() {
        assertEquals("M-0001", addMember().memberCode)
        assertEquals("M-0002", addMember("Second Member").memberCode)
    }

    // ---------- checkout ----------

    @Test
    fun `checkout due date uses default 14 day loan period`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()

        val result = repo.checkout(copies[0].copyCode, member.memberCode)

        assertTrue(result is CheckoutResult.Success)
        val loan = (result as CheckoutResult.Success).loan
        assertEquals(clock.instant().toEpochMilli(), loan.borrowedAt)
        assertEquals(clock.instant().plus(14, ChronoUnit.DAYS).toEpochMilli(), loan.dueAt)
        assertNull(loan.returnedAt)
    }

    @Test
    fun `loan period setting changes due date`() = runBlocking {
        repo.setLoanPeriodDays(7)
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()

        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan

        assertEquals(clock.instant().plus(7, ChronoUnit.DAYS).toEpochMilli(), loan.dueAt)
    }

    @Test
    fun `checkout of unknown copy code reports CopyNotFound`() = runBlocking {
        val member = addMember()
        val result = repo.checkout("B-9999", member.memberCode)
        assertTrue(result is CheckoutResult.CopyNotFound)
    }

    @Test
    fun `checkout by unknown member reports MemberNotFound`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val result = repo.checkout(copies[0].copyCode, "M-9999")
        assertTrue(result is CheckoutResult.MemberNotFound)
    }

    @Test
    fun `checkout for suspended member reports MemberNotActive`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.setMemberStatus(member.id, MemberStatus.SUSPENDED)

        val result = repo.checkout(copies[0].copyCode, member.memberCode)

        assertTrue(result is CheckoutResult.MemberNotActive)
    }

    @Test
    fun `checkout of copy already on loan reports CopyNotAvailable`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val first = addMember()
        val second = addMember("Second Member")
        repo.checkout(copies[0].copyCode, first.memberCode)

        val result = repo.checkout(copies[0].copyCode, second.memberCode)

        assertTrue(result is CheckoutResult.CopyNotAvailable)
    }

    @Test
    fun `checkout of lost copy reports CopyNotAvailable`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.setCopyStatus(copies[0].id, CopyStatus.LOST)

        val result = repo.checkout(copies[0].copyCode, member.memberCode)

        assertTrue(result is CheckoutResult.CopyNotAvailable)
    }

    // ---------- return ----------

    @Test
    fun `returned copy can be checked out again`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val first = addMember()
        val second = addMember("Second Member")
        repo.checkout(copies[0].copyCode, first.memberCode)

        val returned = repo.returnBook(copies[0].copyCode)

        assertTrue(returned is ReturnResult.Success)
        assertFalse((returned as ReturnResult.Success).wasOverdue)
        assertTrue(repo.checkout(copies[0].copyCode, second.memberCode) is CheckoutResult.Success)
    }

    @Test
    fun `late return is reported as overdue`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)

        clock.advanceDays(20)
        val returned = repo.returnBook(copies[0].copyCode)

        assertTrue(returned is ReturnResult.Success)
        val success = returned as ReturnResult.Success
        assertTrue(success.wasOverdue)
        assertEquals(clock.instant().toEpochMilli(), success.loan.returnedAt)
    }

    @Test
    fun `return without active loan reports NoActiveLoan`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val result = repo.returnBook(copies[0].copyCode)
        assertTrue(result is ReturnResult.NoActiveLoan)
    }

    // ---------- overdue tracking ----------

    @Test
    fun `overdue list contains only active loans past their due date`() = runBlocking {
        val (_, copies) = addBookWithCopies(3)
        val m1 = addMember("Member One")
        val m2 = addMember("Member Two")
        val m3 = addMember("Member Three")

        repo.checkout(copies[0].copyCode, m1.memberCode) // stays out past due date
        repo.checkout(copies[1].copyCode, m2.memberCode) // returned late
        clock.advanceDays(20)
        repo.returnBook(copies[1].copyCode)
        repo.checkout(copies[2].copyCode, m3.memberCode) // fresh loan, not due yet

        val overdue = repo.overdueLoans()

        assertEquals(1, overdue.size)
        assertEquals(copies[0].id, overdue[0].copyId)
    }

    // ---------- availability ----------

    @Test
    fun `available copy count reflects active loans`() = runBlocking {
        val (book, copies) = addBookWithCopies(2)
        val member = addMember()

        assertEquals(2, repo.availableCopyCount(book.id))
        repo.checkout(copies[0].copyCode, member.memberCode)
        assertEquals(1, repo.availableCopyCount(book.id))
        repo.returnBook(copies[0].copyCode)
        assertEquals(2, repo.availableCopyCount(book.id))
    }

    @Test
    fun `out of service copies are not counted as available`() = runBlocking {
        val (book, copies) = addBookWithCopies(2)
        repo.setCopyStatus(copies[1].id, CopyStatus.LOST)
        assertEquals(1, repo.availableCopyCount(book.id))
    }

    // ---------- sync outbox ----------

    @Test
    fun `successful checkout writes loan to sync outbox`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()

        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan

        val loanEntries = repo.pendingSyncEntries().filter { it.entityType == "loan" }
        assertEquals(1, loanEntries.size)
        assertEquals(loan.id, loanEntries[0].entityId)
    }

    @Test
    fun `rejected checkout writes nothing to sync outbox`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val first = addMember()
        val second = addMember("Second Member")
        repo.checkout(copies[0].copyCode, first.memberCode)
        val entriesBefore = repo.pendingSyncEntries().size

        val result = repo.checkout(copies[0].copyCode, second.memberCode)

        assertTrue(result is CheckoutResult.CopyNotAvailable)
        assertEquals(entriesBefore, repo.pendingSyncEntries().size)
    }
}
