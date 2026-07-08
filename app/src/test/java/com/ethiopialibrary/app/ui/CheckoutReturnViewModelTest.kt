package com.ethiopialibrary.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ethiopialibrary.app.data.BookCopyEntity
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.MemberEntity
import com.ethiopialibrary.app.data.MemberStatus
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CheckoutReturnViewModelTest {

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

    private fun seedCopy(title: String = "Oromay"): BookCopyEntity = runBlocking {
        val book = repo.addBook(title = title, author = "Bealu Girma", categoryCode = "Fiction", language = "am")
        repo.addCopy(book.id)
    }

    private fun seedMember(name: String = "Abebe Kebede"): MemberEntity = runBlocking {
        repo.registerMember(fullName = name)
    }

    // ---------- checkout ----------

    @Test
    fun `copy lookup populates book info`() {
        val copy = seedCopy()
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)

        val state = awaitValue(vm.state) { it.copy != null }
        assertEquals("Oromay", state.copy?.bookTitle)
    }

    @Test
    fun `unknown copy code sets error`() {
        val vm = CheckoutViewModel(repo)
        vm.submitCopyCode("B-9999")
        val state = awaitValue(vm.state) { it.error != null }
        assertEquals(CheckoutViewModel.CheckoutUiError.COPY_NOT_FOUND, state.error)
    }

    @Test
    fun `copy already on loan reports not available at submit`() {
        val copy = seedCopy()
        val member = seedMember()
        runBlocking { repo.checkout(copy.copyCode, member.memberCode) }
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)

        val state = awaitValue(vm.state) { it.error != null }
        assertEquals(CheckoutViewModel.CheckoutUiError.COPY_NOT_AVAILABLE, state.error)
    }

    @Test
    fun `member lookup populates member`() {
        val copy = seedCopy()
        val member = seedMember()
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)

        val state = awaitValue(vm.state) { it.member != null }
        assertEquals(member.id, state.member?.id)
    }

    @Test
    fun `suspended member reports not active at submit`() {
        val copy = seedCopy()
        val member = seedMember()
        runBlocking { repo.setMemberStatus(member.id, MemberStatus.SUSPENDED) }
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)

        val state = awaitValue(vm.state) { it.error != null }
        assertEquals(CheckoutViewModel.CheckoutUiError.MEMBER_NOT_ACTIVE, state.error)
    }

    @Test
    fun `confirm creates loan and reports completion`() {
        val copy = seedCopy()
        val member = seedMember()
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.member != null }
        vm.confirm()

        val state = awaitValue(vm.state) { it.completedLoan != null }
        assertNotNull(state.completedLoan)
        runBlocking {
            assertNotNull(repo.activeLoanDetailedForCopy(copy.copyCode))
        }
    }

    @Test
    fun `same member can borrow several books in sequence`() {
        val book = runBlocking {
            repo.addBookWithCopies(title = "Oromay", author = "A", categoryCode = "C", language = "am", copies = 2)
        }
        val codes = runBlocking { repo.copyLabelRows().map { it.code } }
        val member = seedMember()
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(codes[0])
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.member != null }
        vm.confirm()
        awaitValue(vm.state) { it.completedLoan != null }

        vm.startAnotherForSameMember()

        val ready = awaitValue(vm.state) { it.completedLoan == null }
        assertEquals(member.id, ready.member?.id)
        assertEquals(null, ready.copy)
        assertEquals(null, ready.error)

        vm.submitCopyCode(codes[1])
        awaitValue(vm.state) { it.copy != null }
        vm.confirm()
        awaitValue(vm.state) { it.completedLoan != null }

        runBlocking {
            assertEquals(2, repo.activeLoansForMember(member.id).first().size)
        }
        assertEquals("Oromay", book.title)
    }

    // ---------- warn-and-override checkout (Wave 5 item 3) ----------

    private fun seedOverdueLoanFor(member: MemberEntity) {
        val overdueCopy = runBlocking {
            val book = repo.addBook(title = "Overdue Book", author = "A", categoryCode = "Fiction", language = "am")
            repo.addCopy(book.id)
        }
        runBlocking { repo.checkout(overdueCopy.copyCode, member.memberCode) }
        clock.advanceDays(20) // past the 14-day default due date
    }

    @Test
    fun `member with no overdue loans shows no warning`() {
        val copy = seedCopy()
        val member = seedMember()
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)

        val state = awaitValue(vm.state) { it.member != null }
        assertEquals(0, state.memberOverdueCount)
    }

    @Test
    fun `member with overdue loans surfaces the count unacknowledged`() {
        val copy = seedCopy()
        val member = seedMember()
        seedOverdueLoanFor(member)
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)

        val state = awaitValue(vm.state) { it.member != null }
        assertEquals(1, state.memberOverdueCount)
        assertEquals(false, state.overdueWarningAcknowledged)
    }

    @Test
    fun `acknowledging the overdue warning clears it`() {
        val copy = seedCopy()
        val member = seedMember()
        seedOverdueLoanFor(member)
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.memberOverdueCount == 1 }

        vm.acknowledgeOverdueWarning()

        val state = awaitValue(vm.state) { it.overdueWarningAcknowledged }
        assertEquals(true, state.overdueWarningAcknowledged)
    }

    @Test
    fun `starting another checkout for the same member keeps the overdue warning state`() {
        val copy = seedCopy()
        val member = seedMember()
        seedOverdueLoanFor(member)
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.memberOverdueCount == 1 }
        vm.acknowledgeOverdueWarning()
        awaitValue(vm.state) { it.overdueWarningAcknowledged }
        vm.confirm()
        awaitValue(vm.state) { it.completedLoan != null }

        vm.startAnotherForSameMember()

        val state = awaitValue(vm.state) { it.member != null }
        assertEquals(1, state.memberOverdueCount)
        assertEquals(true, state.overdueWarningAcknowledged)
    }

    @Test
    fun `confirm at the limit prompts for PIN override instead of blocking`() {
        runBlocking { repo.setMaxBooksPerMember(1) }
        val member = seedMember()
        val firstCopy = seedCopy()
        runBlocking { repo.checkout(firstCopy.copyCode, member.memberCode) }
        val secondCopy = seedCopy(title = "Second Book")
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(secondCopy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.member != null }
        vm.confirm()

        val state = awaitValue(vm.state) { it.awaitingPinOverride }
        assertEquals(null, state.error)
        assertEquals(null, state.completedLoan)
    }

    @Test
    fun `wrong PIN on override is rejected and does not check out`() {
        runBlocking { repo.setStaffPin("1234") }
        runBlocking { repo.setMaxBooksPerMember(1) }
        val member = seedMember()
        val firstCopy = seedCopy()
        runBlocking { repo.checkout(firstCopy.copyCode, member.memberCode) }
        val secondCopy = seedCopy(title = "Second Book")
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(secondCopy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.member != null }
        vm.confirm()
        awaitValue(vm.state) { it.awaitingPinOverride }

        vm.confirmWithPinOverride("0000")

        val state = awaitValue(vm.state) { it.pinOverrideError }
        assertEquals(true, state.awaitingPinOverride)
        assertEquals(null, state.completedLoan)
    }

    @Test
    fun `correct PIN on override checks out beyond the limit`() {
        runBlocking { repo.setStaffPin("1234") }
        runBlocking { repo.setMaxBooksPerMember(1) }
        val member = seedMember()
        val firstCopy = seedCopy()
        runBlocking { repo.checkout(firstCopy.copyCode, member.memberCode) }
        val secondCopy = seedCopy(title = "Second Book")
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(secondCopy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.member != null }
        vm.confirm()
        awaitValue(vm.state) { it.awaitingPinOverride }

        vm.confirmWithPinOverride("1234")

        val state = awaitValue(vm.state) { it.completedLoan != null }
        assertEquals(false, state.awaitingPinOverride)
        runBlocking {
            assertEquals(2, repo.activeLoansForMember(member.id).first().size)
        }
    }

    @Test
    fun `entering a new PIN clears the wrong-PIN flag`() {
        runBlocking { repo.setStaffPin("1234") }
        runBlocking { repo.setMaxBooksPerMember(1) }
        val member = seedMember()
        val firstCopy = seedCopy()
        runBlocking { repo.checkout(firstCopy.copyCode, member.memberCode) }
        val secondCopy = seedCopy(title = "Second Book")
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(secondCopy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.member != null }
        vm.confirm()
        awaitValue(vm.state) { it.awaitingPinOverride }
        vm.confirmWithPinOverride("0000")
        awaitValue(vm.state) { it.pinOverrideError }

        vm.clearPinOverrideError()

        val state = awaitValue(vm.state) { !it.pinOverrideError }
        assertEquals(false, state.pinOverrideError)
    }

    @Test
    fun `dismissing PIN override restores the limit-reached error`() {
        runBlocking { repo.setMaxBooksPerMember(1) }
        val member = seedMember()
        val firstCopy = seedCopy()
        runBlocking { repo.checkout(firstCopy.copyCode, member.memberCode) }
        val secondCopy = seedCopy(title = "Second Book")
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(secondCopy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.member != null }
        vm.confirm()
        awaitValue(vm.state) { it.awaitingPinOverride }

        vm.dismissPinOverride()

        val state = awaitValue(vm.state) { it.error == CheckoutViewModel.CheckoutUiError.LIMIT_REACHED }
        assertEquals(false, state.awaitingPinOverride)
    }

    // ---------- batch checkout (Wave 5 item 4) ----------

    @Test
    fun `batch member lookup populates member`() {
        val member = seedMember()
        val vm = CheckoutViewModel(repo)

        vm.submitBatchMemberCode(member.memberCode)

        val state = awaitValue(vm.batchState) { it.member != null }
        assertEquals(member.id, state.member?.id)
    }

    @Test
    fun `batch member lookup with unknown code sets member error`() {
        val vm = CheckoutViewModel(repo)

        vm.submitBatchMemberCode("M-9999")

        val state = awaitValue(vm.batchState) { it.memberError != null }
        assertEquals(CheckoutViewModel.CheckoutUiError.MEMBER_NOT_FOUND, state.memberError)
    }

    @Test
    fun `adding a copy to the batch lists it`() {
        val member = seedMember()
        val copy = seedCopy()
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(member.memberCode)
        awaitValue(vm.batchState) { it.member != null }

        vm.addBatchCopyCode(copy.copyCode)

        val state = awaitValue(vm.batchState) { it.items.isNotEmpty() }
        assertEquals(1, state.items.size)
        assertEquals(copy.copyCode, state.items[0].copy.copy.copyCode)
    }

    @Test
    fun `adding the same copy twice is rejected`() {
        val member = seedMember()
        val copy = seedCopy()
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(member.memberCode)
        awaitValue(vm.batchState) { it.member != null }
        vm.addBatchCopyCode(copy.copyCode)
        awaitValue(vm.batchState) { it.items.isNotEmpty() }

        vm.addBatchCopyCode(copy.copyCode)

        val state = awaitValue(vm.batchState) { it.copyError != null }
        assertEquals(1, state.items.size)
        assertEquals(CheckoutViewModel.CheckoutUiError.COPY_NOT_AVAILABLE, state.copyError)
    }

    @Test
    fun `adding an unavailable copy is rejected`() {
        val member = seedMember()
        val onLoanCopy = seedCopy()
        val otherMember = seedMember("Someone Else")
        runBlocking { repo.checkout(onLoanCopy.copyCode, otherMember.memberCode) }
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(member.memberCode)
        awaitValue(vm.batchState) { it.member != null }

        vm.addBatchCopyCode(onLoanCopy.copyCode)

        val state = awaitValue(vm.batchState) { it.copyError != null }
        assertEquals(0, state.items.size)
        assertEquals(CheckoutViewModel.CheckoutUiError.COPY_NOT_AVAILABLE, state.copyError)
    }

    @Test
    fun `adding a copy beyond the limit is rejected`() {
        runBlocking { repo.setMaxBooksPerMember(1) }
        val member = seedMember()
        val alreadyBorrowed = seedCopy()
        runBlocking { repo.checkout(alreadyBorrowed.copyCode, member.memberCode) }
        val secondCopy = seedCopy(title = "Second Book")
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(member.memberCode)
        awaitValue(vm.batchState) { it.member != null }

        vm.addBatchCopyCode(secondCopy.copyCode)

        val state = awaitValue(vm.batchState) { it.copyError != null }
        assertEquals(0, state.items.size)
        assertEquals(CheckoutViewModel.CheckoutUiError.LIMIT_REACHED, state.copyError)
    }

    @Test
    fun `limit counts items already added to the basket`() {
        runBlocking { repo.setMaxBooksPerMember(2) }
        val member = seedMember()
        val first = seedCopy(title = "First")
        val second = seedCopy(title = "Second")
        val third = seedCopy(title = "Third")
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(member.memberCode)
        awaitValue(vm.batchState) { it.member != null }
        vm.addBatchCopyCode(first.copyCode)
        awaitValue(vm.batchState) { it.items.size == 1 }
        vm.addBatchCopyCode(second.copyCode)
        awaitValue(vm.batchState) { it.items.size == 2 }

        vm.addBatchCopyCode(third.copyCode)

        val state = awaitValue(vm.batchState) { it.copyError != null }
        assertEquals(2, state.items.size)
        assertEquals(CheckoutViewModel.CheckoutUiError.LIMIT_REACHED, state.copyError)
    }

    @Test
    fun `confirming the batch checks out every item and reports success`() {
        val member = seedMember()
        val first = seedCopy(title = "First")
        val second = seedCopy(title = "Second")
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(member.memberCode)
        awaitValue(vm.batchState) { it.member != null }
        vm.addBatchCopyCode(first.copyCode)
        awaitValue(vm.batchState) { it.items.size == 1 }
        vm.addBatchCopyCode(second.copyCode)
        awaitValue(vm.batchState) { it.items.size == 2 }

        vm.confirmBatch()

        val state = awaitValue(vm.batchState) { it.results != null }
        assertEquals(2, state.results?.size)
        assertTrue(state.results!!.all { it.outcome == CheckoutViewModel.BatchLineOutcome.SUCCESS })
        runBlocking {
            assertEquals(2, repo.activeLoansForMember(member.id).first().size)
        }
    }

    @Test
    fun `resetting the batch clears all state`() {
        val member = seedMember()
        val copy = seedCopy()
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(member.memberCode)
        awaitValue(vm.batchState) { it.member != null }
        vm.addBatchCopyCode(copy.copyCode)
        awaitValue(vm.batchState) { it.items.isNotEmpty() }

        vm.resetBatch()

        val state = awaitValue(vm.batchState) { it.member == null }
        assertTrue(state.items.isEmpty())
        assertEquals(null, state.results)
    }

    // ---------- quick-add member mid-checkout (Wave 5 item 5) ----------

    @Test
    fun `quick-adding a member during checkout populates member and clears the error`() {
        val copy = seedCopy()
        val vm = CheckoutViewModel(repo)
        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode("M-9999")
        awaitValue(vm.state) { it.error != null }

        vm.quickAddMember("Kebede Alemu", null, null, null)

        val state = awaitValue(vm.state) { it.member != null }
        assertEquals("Kebede Alemu", state.member?.fullName)
        assertEquals(null, state.error)
        assertEquals(0, state.memberOverdueCount)
    }

    @Test
    fun `quick-adding a member during batch checkout populates the batch member`() {
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode("M-9999")
        awaitValue(vm.batchState) { it.memberError != null }

        vm.quickAddBatchMember("Kebede Alemu", null, null, null)

        val state = awaitValue(vm.batchState) { it.member != null }
        assertEquals("Kebede Alemu", state.member?.fullName)
        assertEquals(null, state.memberError)
    }

    // ---------- copy search on checkout (name search) ----------

    @Test
    fun `copy search lists matching copies and selecting one advances to member step`() {
        runBlocking {
            repo.addBookWithCopies(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am", copies = 2)
        }
        val codes = runBlocking { repo.copyLabelRows().map { it.code } }
        val vm = CheckoutViewModel(repo)

        vm.setCopyQuery("Oromay")
        val results = awaitValue(vm.copyResults) { it.size == 2 }
        assertEquals(2, results.size)

        vm.submitCopyCode(codes[0])

        val state = awaitValue(vm.state) { it.copy != null }
        assertEquals(codes[0], state.copy?.copy?.copyCode)
    }

    // ---------- per-checkout loan period (feature 5) ----------

    @Test
    fun `loan period defaults to the configured period`() {
        runBlocking { repo.setLoanPeriodDays(21) }
        val vm = CheckoutViewModel(repo)

        val state = awaitValue(vm.state) { it.loanPeriodDays == 21 }
        assertEquals(21, state.loanPeriodDays)
    }

    @Test
    fun `checkout uses the chosen loan period`() {
        val copy = seedCopy()
        val member = seedMember()
        val vm = CheckoutViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(member.memberCode)
        awaitValue(vm.state) { it.member != null }
        vm.setLoanPeriod(30)
        vm.confirm()

        val state = awaitValue(vm.state) { it.completedLoan != null }
        assertEquals(
            clock.instant().plus(30, ChronoUnit.DAYS).toEpochMilli(),
            state.completedLoan?.dueAt,
        )
    }

    // ---------- return ----------

    @Test
    fun `submitting borrowed copy shows loan details`() {
        val copy = seedCopy(title = "Fiqir Eske Meqabir")
        val member = seedMember()
        runBlocking { repo.checkout(copy.copyCode, member.memberCode) }
        val vm = ReturnViewModel(repo)

        vm.submitCopyCode(copy.copyCode)

        val state = awaitValue(vm.state) { it.loan != null }
        assertEquals("Fiqir Eske Meqabir", state.loan?.bookTitle)
        assertEquals("Abebe Kebede", state.loan?.memberName)
    }

    @Test
    fun `return search lists on-loan copies and selecting one shows the loan`() {
        val copy = seedCopy(title = "Fiqir Eske Meqabir")
        val member = seedMember()
        runBlocking { repo.checkout(copy.copyCode, member.memberCode) }
        val vm = ReturnViewModel(repo)

        vm.setCopyQuery("Fiqir")
        val results = awaitValue(vm.copyResults) { it.isNotEmpty() }
        assertEquals(copy.copyCode, results.single().copy.copyCode)

        vm.submitCopyCode(copy.copyCode)

        val state = awaitValue(vm.state) { it.loan != null }
        assertEquals("Fiqir Eske Meqabir", state.loan?.bookTitle)
    }

    @Test
    fun `copy without active loan sets error`() {
        val copy = seedCopy()
        val vm = ReturnViewModel(repo)
        vm.submitCopyCode(copy.copyCode)
        val state = awaitValue(vm.state) { it.error != null }
        assertEquals(ReturnViewModel.ReturnUiError.NO_ACTIVE_LOAN, state.error)
    }

    @Test
    fun `confirm return completes and reports overdue`() {
        val copy = seedCopy()
        val member = seedMember()
        runBlocking { repo.checkout(copy.copyCode, member.memberCode) }
        clock.advanceDays(20)
        val vm = ReturnViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.loan != null }
        vm.confirmReturn()

        val state = awaitValue(vm.state) { it.returned != null }
        assertEquals(true, state.wasOverdue)
        runBlocking {
            assertEquals(null, repo.activeLoanDetailedForCopy(copy.copyCode))
        }
    }

    @Test
    fun `return prompts for a rating then records the chosen stars`() {
        val copy = seedCopy()
        val member = seedMember()
        runBlocking { repo.checkout(copy.copyCode, member.memberCode) }
        val vm = ReturnViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.loan != null }
        vm.confirmReturn()
        val prompting = awaitValue(vm.state) { it.awaitingRating }
        assertNotNull(prompting.returned)

        vm.rateMember(4)

        val done = awaitValue(vm.state) { !it.awaitingRating && it.returned != null }
        runBlocking {
            assertEquals(4, db.loanDao().byId(done.returned!!.id)?.rating)
        }
    }

    @Test
    fun `rating step can be skipped`() {
        val copy = seedCopy()
        val member = seedMember()
        runBlocking { repo.checkout(copy.copyCode, member.memberCode) }
        val vm = ReturnViewModel(repo)

        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.loan != null }
        vm.confirmReturn()
        awaitValue(vm.state) { it.awaitingRating }

        vm.skipRating()

        val done = awaitValue(vm.state) { !it.awaitingRating && it.returned != null }
        runBlocking {
            assertNull(db.loanDao().byId(done.returned!!.id)?.rating)
        }
    }

    @Test
    fun `reset clears checkout state`() {
        val copy = seedCopy()
        val vm = CheckoutViewModel(repo)
        vm.submitCopyCode(copy.copyCode)
        awaitValue(vm.state) { it.copy != null }

        vm.reset()

        val state = awaitValue(vm.state) { it.copy == null }
        assertTrue(state.member == null && state.error == null && state.completedLoan == null)
    }
}
