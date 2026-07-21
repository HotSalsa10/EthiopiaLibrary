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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CheckoutViewModelTest {

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
        runBlocking { withTimeout(5_000) { flow.first(predicate) } }

    private fun addCopyAndMember(): Pair<String, String> = runBlocking {
        repo.addBookWithCopies(title = "Oromay", author = "A", categoryCode = "C", language = "am", copies = 1)
        val copyCode = repo.copyLabelRows().single().code
        val member = repo.registerMember(fullName = "Abebe")
        copyCode to member.memberCode
    }

    @Test
    fun `a second confirm call while the first is in flight is a no-op`() {
        val (copyCode, memberCode) = addCopyAndMember()
        val vm = CheckoutViewModel(repo)
        vm.submitCopyCode(copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(memberCode)
        awaitValue(vm.state) { it.member != null }

        vm.confirm() // begins writing, sets inFlight = true
        assertTrue(vm.state.value.inFlight)
        vm.confirm() // must be rejected instead of re-checking-out the same copy

        val state = awaitValue(vm.state) { it.completedLoan != null }
        assertNull(state.error)
        assertFalse(state.inFlight)
        // The copy was checked out exactly once, not twice.
        val activeLoans = runBlocking { repo.activeLoansForMember(repo.memberByCode(memberCode)!!.id).first() }
        assertEquals(1, activeLoans.size)
    }

    @Test
    fun `a second confirmBatch call while the first is in flight is a no-op`() {
        val (copyCode, memberCode) = addCopyAndMember()
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(memberCode)
        awaitValue(vm.batchState) { it.member != null }
        vm.addBatchCopyCode(copyCode)
        awaitValue(vm.batchState) { it.items.isNotEmpty() }

        vm.confirmBatch() // begins the loop, sets inFlight = true
        assertTrue(vm.batchState.value.inFlight)
        vm.confirmBatch() // must be rejected - re-running would double-checkout

        val state = awaitValue(vm.batchState) { it.results != null }
        assertFalse(state.inFlight)
        assertEquals(1, state.results!!.size)
    }

    @Test
    fun `adding the same copy to the basket twice reports already-in-basket`() {
        val (copyCode, memberCode) = addCopyAndMember()
        val vm = CheckoutViewModel(repo)
        vm.submitBatchMemberCode(memberCode)
        awaitValue(vm.batchState) { it.member != null }

        vm.addBatchCopyCode(copyCode)
        awaitValue(vm.batchState) { it.items.isNotEmpty() }
        vm.addBatchCopyCode(copyCode)

        val state = awaitValue(vm.batchState) { it.copyError != null }
        assertEquals(CheckoutViewModel.CheckoutUiError.ALREADY_IN_BASKET, state.copyError)
        assertEquals(1, state.items.size)
    }

    @Test
    fun `member search lists matches and picking one adopts the member`() {
        runBlocking {
            repo.registerMember(fullName = "Abebe Kebede")
            repo.registerMember(fullName = "Sara Ali")
        }
        val vm = CheckoutViewModel(repo)

        vm.setMemberQuery("Abe")
        val results = awaitValue(vm.memberResults) { it.size == 1 }
        val picked = results.single().member

        vm.submitMemberCode(picked.memberCode)
        val state = awaitValue(vm.state) { it.member != null }
        assertEquals(picked, state.member)
    }

    @Test
    fun `blank member query returns no results`() {
        runBlocking { repo.registerMember(fullName = "Abebe Kebede") }
        val vm = CheckoutViewModel(repo)

        vm.setMemberQuery("x")
        awaitValue(vm.memberResults) { true } // let the search settle before clearing it
        vm.setMemberQuery("")

        assertTrue(vm.memberResults.value.isEmpty())
    }

    @Test
    fun `reset and startAnotherForSameMember clear the member search query`() {
        val vm = CheckoutViewModel(repo)

        vm.setMemberQuery("x")
        vm.reset()
        assertEquals("", vm.memberQuery.value)

        vm.setMemberQuery("x")
        vm.startAnotherForSameMember()
        assertEquals("", vm.memberQuery.value)
    }

    @Test
    fun `confirm surfaces the clock-wrong error and creates no loan`() {
        val (copyCode, memberCode) = addCopyAndMember()
        val wrongClockRepo = LibraryRepository(db, clock, buildTimeMillis = clock.instant().toEpochMilli() + 1)
        val vm = CheckoutViewModel(wrongClockRepo)
        vm.submitCopyCode(copyCode)
        awaitValue(vm.state) { it.copy != null }
        vm.submitMemberCode(memberCode)
        awaitValue(vm.state) { it.member != null }

        vm.confirm()

        val state = awaitValue(vm.state) { it.error != null }
        assertEquals(CheckoutViewModel.CheckoutUiError.CLOCK_WRONG, state.error)
        assertNull(state.completedLoan)
        val activeLoans = runBlocking { repo.activeLoansForMember(repo.memberByCode(memberCode)!!.id).first() }
        assertEquals(0, activeLoans.size)
    }
}
