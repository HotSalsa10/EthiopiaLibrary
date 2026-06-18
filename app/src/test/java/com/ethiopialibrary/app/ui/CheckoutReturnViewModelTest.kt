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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
