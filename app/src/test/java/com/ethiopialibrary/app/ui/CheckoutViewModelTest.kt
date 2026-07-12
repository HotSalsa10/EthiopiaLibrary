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
}
