package com.ethiopialibrary.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.temporal.ChronoUnit
import java.util.Locale

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
            categoryCode = "Fiction",
            language = "am",
        )
        book to (1..copies).map { repo.addCopy(book.id) }
    }

    private fun addMember(name: String = "Abebe Kebede"): MemberEntity =
        runBlocking { repo.registerMember(fullName = name) }

    // ---------- accession / member codes ----------

    @Test
    fun `copies of a book get incrementing copy numbers in the code`() {
        val (_, copies) = addBookWithCopies(2)
        assertEquals("Fiction-001-1-00", copies[0].copyCode)
        assertEquals("Fiction-001-2-00", copies[1].copyCode)
    }

    @Test
    fun `book numbers increment within a category, independently per category`() = runBlocking {
        val first = repo.addBook(title = "A", author = "x", categoryCode = "TF", language = "am")
        val second = repo.addBook(title = "B", author = "y", categoryCode = "TF", language = "am")
        val other = repo.addBook(title = "C", author = "z", categoryCode = "AQ", language = "am")

        assertEquals(1, first.bookNumber)
        assertEquals(2, second.bookNumber)
        assertEquals(1, other.bookNumber)
        assertEquals("TF-002-1-00", repo.addCopy(second.id).copyCode)
    }

    @Test
    fun `members receive sequential member codes`() {
        assertEquals("M-0001", addMember().memberCode)
        assertEquals("M-0002", addMember("Second Member").memberCode)
    }

    @Test
    fun `member codes stay ascii digits under the arabic default locale`() {
        // A member code is printed on a card, scanned back, and re-parsed as a
        // number by restore's sequence recompute - it must not shift with the
        // UI language or a later restore can mint a duplicate code.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertEquals("M-0001", addMember().memberCode)
        } finally {
            Locale.setDefault(previous)
        }
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

    // ---------- renew ----------

    @Test
    fun `renewing an active loan extends due date from today`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan

        clock.advanceDays(10)
        val result = repo.renewLoan(loan.id)

        assertTrue(result is RenewResult.Success)
        val renewed = (result as RenewResult.Success).loan
        assertEquals(clock.instant().plus(14, ChronoUnit.DAYS).toEpochMilli(), renewed.dueAt)
        assertNull(renewed.returnedAt)
    }

    @Test
    fun `renewing clears overdue status`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan
        clock.advanceDays(20)
        assertEquals(1, repo.overdueLoans().size)

        repo.renewLoan(loan.id)

        assertEquals(0, repo.overdueLoans().size)
    }

    // ---------- activity log + undo (Wave 5 item 6) ----------

    @Test
    fun `checkout logs a CHECKOUT activity entry`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()

        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan

        val entries = repo.recentActivity().first()
        assertEquals(1, entries.size)
        assertEquals(ActivityType.CHECKOUT.name, entries[0].entry.type)
        assertEquals(loan.id, entries[0].entry.loanId)
        assertEquals("Abebe Kebede", entries[0].memberName)
    }

    @Test
    fun `return logs a RETURN activity entry`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)

        repo.returnBook(copies[0].copyCode)

        val entries = repo.recentActivity().first()
        assertEquals(2, entries.size) // checkout + return
        assertEquals(ActivityType.RETURN.name, entries[0].entry.type) // newest first
    }

    @Test
    fun `renew logs a RENEW activity entry with the previous due date`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan
        val originalDueAt = loan.dueAt

        repo.renewLoan(loan.id)

        val entries = repo.recentActivity().first()
        val renewEntry = entries.first { it.entry.type == ActivityType.RENEW.name }
        assertEquals(originalDueAt, renewEntry.entry.prevDueAt)
    }

    @Test
    fun `recent activity only includes today's entries`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)

        clock.advanceDays(1)

        assertTrue(repo.recentActivity().first().isEmpty())
    }

    @Test
    fun `undoing a checkout soft-deletes the loan and frees the copy`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val checkoutEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.CHECKOUT.name }

        val undone = repo.undoActivity(checkoutEntry.entry.id)

        assertTrue(undone)
        assertNull(repo.activeLoanDetailedForCopy(copies[0].copyCode))
        assertTrue(repo.checkout(copies[0].copyCode, member.memberCode) is CheckoutResult.Success)
    }

    @Test
    fun `undoing a return clears returnedAt`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        repo.returnBook(copies[0].copyCode)
        val returnEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.RETURN.name }

        val undone = repo.undoActivity(returnEntry.entry.id)

        assertTrue(undone)
        assertNotNull(repo.activeLoanDetailedForCopy(copies[0].copyCode))
    }

    @Test
    fun `undoing a renew restores the previous due date`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan
        val originalDueAt = loan.dueAt
        repo.renewLoan(loan.id)
        val renewEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.RENEW.name }

        val undone = repo.undoActivity(renewEntry.entry.id)

        assertTrue(undone)
        assertEquals(originalDueAt, repo.activeLoanDetailedForCopy(copies[0].copyCode)?.loan?.dueAt)
    }

    @Test
    fun `undo logs its own UNDO entry`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val checkoutEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.CHECKOUT.name }

        repo.undoActivity(checkoutEntry.entry.id)

        val entries = repo.recentActivity().first()
        assertEquals(2, entries.size) // original CHECKOUT + new UNDO
        assertTrue(entries.any { it.entry.type == ActivityType.UNDO.name })
    }

    @Test
    fun `an UNDO entry itself cannot be undone`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val checkoutEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.CHECKOUT.name }
        repo.undoActivity(checkoutEntry.entry.id)
        val undoEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.UNDO.name }

        assertFalse(repo.undoActivity(undoEntry.entry.id))
    }

    @Test
    fun `undoing an unknown activity id returns false`() = runBlocking {
        assertFalse(repo.undoActivity("does-not-exist"))
    }

    @Test
    fun `a second undo of the same entry returns false`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val checkoutEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.CHECKOUT.name }

        val first = repo.undoActivity(checkoutEntry.entry.id)
        val second = repo.undoActivity(checkoutEntry.entry.id)

        assertTrue(first)
        assertFalse(second)
    }

    @Test
    fun `a repeat undo does not insert a second UNDO log entry`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val checkoutEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.CHECKOUT.name }

        repo.undoActivity(checkoutEntry.entry.id)
        val undoCountAfterFirst = repo.recentActivity().first().count { it.entry.type == ActivityType.UNDO.name }

        repo.undoActivity(checkoutEntry.entry.id)
        val undoCountAfterSecond = repo.recentActivity().first().count { it.entry.type == ActivityType.UNDO.name }

        assertEquals(1, undoCountAfterFirst)
        assertEquals(1, undoCountAfterSecond)
    }

    @Test
    fun `undoing an entry once still succeeds and stamps its undoneAt`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val checkoutEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.CHECKOUT.name }

        val undone = repo.undoActivity(checkoutEntry.entry.id)

        assertTrue(undone)
        // Happy path is unchanged: the copy is freed again.
        assertNull(repo.activeLoanDetailedForCopy(copies[0].copyCode))
        // New behavior: the original entry itself is now marked undone.
        val reloaded = repo.recentActivity().first().first { it.entry.id == checkoutEntry.entry.id }
        assertNotNull(reloaded.entry.undoneAt)
    }

    @Test
    fun `undoing a return is rejected if the copy has since been re-checked-out`() = runBlocking {
        // Regression: un-returning after a different loan already claimed the
        // copy would leave two loans with returnedAt = null on one copy.
        val (_, copies) = addBookWithCopies(1)
        val memberA = addMember("Member A")
        val memberB = addMember("Member B")
        repo.checkout(copies[0].copyCode, memberA.memberCode)
        repo.returnBook(copies[0].copyCode)
        val returnEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.RETURN.name }
        repo.checkout(copies[0].copyCode, memberB.memberCode)

        val undone = repo.undoActivity(returnEntry.entry.id)

        assertFalse(undone)
        val active = repo.activeLoanDetailedForCopy(copies[0].copyCode)
        assertEquals(memberB.id, active?.loan?.memberId) // still only member B's loan is active
    }

    @Test
    fun `undoing a checkout is rejected if the loan was already returned`() = runBlocking {
        // Regression: undoing a stale CHECKOUT row would soft-delete a
        // completed loan out of history.
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan
        val checkoutEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.CHECKOUT.name }
        repo.returnBook(copies[0].copyCode)

        val undone = repo.undoActivity(checkoutEntry.entry.id)

        assertFalse(undone)
        val stored = db.loanDao().byId(loan.id)!!
        assertFalse(stored.isDeleted)
        assertNotNull(stored.returnedAt)
    }

    @Test
    fun `undoing the older of two stacked renews is rejected`() = runBlocking {
        // Regression: undoing an out-of-order renew would restore a due date
        // that's already been superseded by a later renew.
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan
        repo.renewLoan(loan.id)
        val firstRenewEntry = repo.recentActivity().first().first { it.entry.type == ActivityType.RENEW.name }
        clock.advanceDays(1)
        repo.renewLoan(loan.id)
        val afterSecondRenewDueAt = repo.activeLoanDetailedForCopy(copies[0].copyCode)!!.loan.dueAt

        val undone = repo.undoActivity(firstRenewEntry.entry.id)

        assertFalse(undone)
        assertEquals(afterSecondRenewDueAt, repo.activeLoanDetailedForCopy(copies[0].copyCode)!!.loan.dueAt)
    }

    @Test
    fun `renewing a returned loan reports NotActive`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan
        repo.returnBook(copies[0].copyCode)

        assertTrue(repo.renewLoan(loan.id) is RenewResult.NotActive)
        assertTrue(repo.renewLoan("no-such-loan") is RenewResult.NotActive)
    }

    @Test
    fun `renewing writes loan to sync outbox`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan
        val loanEntriesBefore = repo.pendingSyncEntries().count { it.entityType == "loan" }

        repo.renewLoan(loan.id)

        assertEquals(loanEntriesBefore + 1, repo.pendingSyncEntries().count { it.entityType == "loan" })
    }

    // ---------- member id + address (feature 3) ----------

    @Test
    fun `registerMember stores national id and address`() = runBlocking {
        val member = repo.registerMember(
            fullName = "Abebe Kebede",
            phone = "0911121314",
            nationalId = "ID-12345",
            address = "Bole, Addis Ababa",
        )

        val stored = repo.memberById(member.id)!!
        assertEquals("ID-12345", stored.nationalId)
        assertEquals("Bole, Addis Ababa", stored.address)
    }

    @Test
    fun `national id and address are optional`() = runBlocking {
        val member = repo.registerMember(fullName = "No Details")

        val stored = repo.memberById(member.id)!!
        assertNull(stored.nationalId)
        assertNull(stored.address)
    }

    // ---------- per-checkout loan period (feature 5) ----------

    @Test
    fun `checkout with explicit period overrides the default and the setting`() = runBlocking {
        repo.setLoanPeriodDays(7)
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()

        val loan = (repo.checkout(copies[0].copyCode, member.memberCode, periodDays = 30) as CheckoutResult.Success).loan

        assertEquals(clock.instant().plus(30, ChronoUnit.DAYS).toEpochMilli(), loan.dueAt)
    }

    @Test
    fun `checkout without an explicit period falls back to the setting`() = runBlocking {
        repo.setLoanPeriodDays(10)
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()

        val loan = (repo.checkout(copies[0].copyCode, member.memberCode) as CheckoutResult.Success).loan

        assertEquals(clock.instant().plus(10, ChronoUnit.DAYS).toEpochMilli(), loan.dueAt)
    }

    // ---------- member rating (feature 4) ----------

    @Test
    fun `rateLoan stores the rating on the loan`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val returned = (repo.returnBook(copies[0].copyCode) as ReturnResult.Success).loan

        repo.rateLoan(returned.id, 4)

        assertEquals(4, db.loanDao().byId(returned.id)!!.rating)
    }

    @Test
    fun `member average rating averages every rated loan`() = runBlocking {
        val (_, copies) = addBookWithCopies(2)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val l1 = (repo.returnBook(copies[0].copyCode) as ReturnResult.Success).loan
        repo.checkout(copies[1].copyCode, member.memberCode)
        val l2 = (repo.returnBook(copies[1].copyCode) as ReturnResult.Success).loan
        repo.rateLoan(l1.id, 5)
        repo.rateLoan(l2.id, 3)

        assertEquals(4.0, repo.memberAverageRating(member.id)!!, 0.001)
    }

    @Test
    fun `member average rating is null when no loans are rated`() = runBlocking {
        val member = addMember()
        assertNull(repo.memberAverageRating(member.id))
    }

    @Test
    fun `rateLoan rejects ratings outside 1 to 5`(): Unit = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val returned = (repo.returnBook(copies[0].copyCode) as ReturnResult.Success).loan

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.rateLoan(returned.id, 0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.rateLoan(returned.id, 6) }
        }
    }

    @Test
    fun `rateLoan writes the loan to the sync outbox`() = runBlocking {
        val (_, copies) = addBookWithCopies(1)
        val member = addMember()
        repo.checkout(copies[0].copyCode, member.memberCode)
        val returned = (repo.returnBook(copies[0].copyCode) as ReturnResult.Success).loan
        val before = repo.pendingSyncEntries().count { it.entityType == "loan" }

        repo.rateLoan(returned.id, 5)

        assertEquals(before + 1, repo.pendingSyncEntries().count { it.entityType == "loan" })
    }

    // ---------- copy search for checkout (name search) ----------

    @Test
    fun `searchCopies matches by title, author, and code with loan status`() = runBlocking {
        val book = repo.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am")
        val c1 = repo.addCopy(book.id)
        val c2 = repo.addCopy(book.id)
        val member = addMember()
        repo.checkout(c1.copyCode, member.memberCode)

        val byTitle = repo.searchCopies("Oromay").first()
        assertEquals(2, byTitle.size)
        assertTrue(byTitle.first { it.copy.copyCode == c1.copyCode }.onLoan)
        assertFalse(byTitle.first { it.copy.copyCode == c2.copyCode }.onLoan)

        assertEquals(2, repo.searchCopies("Bealu").first().size)

        val byCode = repo.searchCopies(c2.copyCode).first()
        assertEquals(1, byCode.size)
        assertEquals(c2.copyCode, byCode.single().copy.copyCode)
    }

    @Test
    fun `searchCopies returns nothing when nothing matches`() = runBlocking {
        val book = repo.addBook(title = "Oromay", author = "A", categoryCode = "Fiction", language = "am")
        repo.addCopy(book.id)
        assertTrue(repo.searchCopies("zzzzz").first().isEmpty())
    }

    @Test
    fun `searchOnLoanCopies returns only copies currently on loan, matched by name or code`() = runBlocking {
        val book = repo.addBook(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am")
        val c1 = repo.addCopy(book.id)
        val c2 = repo.addCopy(book.id)
        val member = addMember()
        repo.checkout(c1.copyCode, member.memberCode)

        val byTitle = repo.searchOnLoanCopies("Oromay").first()
        assertEquals(1, byTitle.size)
        assertEquals(c1.copyCode, byTitle.single().copy.copyCode)
        assertTrue(byTitle.single().onLoan)

        assertEquals(1, repo.searchOnLoanCopies("Bealu").first().size)
        assertEquals(1, repo.searchOnLoanCopies(c1.copyCode).first().size)
        // c2 is not on loan, so searching its code returns nothing
        assertTrue(repo.searchOnLoanCopies(c2.copyCode).first().isEmpty())
    }

    // ---------- staff PIN ----------

    @Test
    fun `staff pin can be set verified and rejected`() = runBlocking {
        assertFalse(repo.hasStaffPin())

        repo.setStaffPin("1234")

        assertTrue(repo.hasStaffPin())
        assertTrue(repo.verifyStaffPin("1234"))
        assertFalse(repo.verifyStaffPin("9999"))
        assertFalse(repo.verifyStaffPin(""))
    }

    @Test
    fun `staff pin hash is stable across default locale changes`() = runBlocking {
        // The hash is hex ("%02x" per byte) - it must verify the same PIN
        // whether it was set or is being checked under a different locale.
        repo.setStaffPin("1234")
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertTrue(repo.verifyStaffPin("1234"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `staff pin is not stored in plain text`() = runBlocking {
        repo.setStaffPin("1234")
        val stored = db.settingsDao().get("staff_pin_hash")
        assertNotNull(stored)
        assertFalse(stored == "1234")
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

    // ---------- backup nudge ----------

    @Test
    fun `backup nudge appears for waiting changes and stays quiet for today once dismissed`() = runBlocking {
        assertFalse(repo.backupNudgeWanted().first()) // empty library, nothing pending

        addMember() // pending outbox entry, never backed up
        assertTrue(repo.backupNudgeWanted().first())

        repo.dismissBackupNudgeForToday()
        assertFalse(repo.backupNudgeWanted().first())

        clock.advanceDays(1) // a new day with changes still waiting
        assertTrue(repo.backupNudgeWanted().first())
    }
}
