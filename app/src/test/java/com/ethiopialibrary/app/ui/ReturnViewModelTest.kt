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
class ReturnViewModelTest {

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

    private fun checkoutOneCopy(): String = runBlocking {
        repo.addBookWithCopies(title = "Oromay", author = "A", categoryCode = "C", language = "am", copies = 1)
        val copyCode = repo.copyLabelRows().single().code
        val member = repo.registerMember(fullName = "Abebe")
        repo.checkout(copyCode, member.memberCode)
        copyCode
    }

    @Test
    fun `confirming a return once succeeds with no error`() {
        val copyCode = checkoutOneCopy()
        val vm = ReturnViewModel(repo)
        vm.submitCopyCode(copyCode)
        awaitValue(vm.state) { it.loan != null }

        vm.confirmReturn()

        val state = awaitValue(vm.state) { it.returned != null }
        assertNull(state.error)
        assertFalse(state.inFlight)
    }

    @Test
    fun `confirmReturn sets inFlight synchronously before the write completes`() {
        val copyCode = checkoutOneCopy()
        val vm = ReturnViewModel(repo)
        vm.submitCopyCode(copyCode)
        awaitValue(vm.state) { it.loan != null }

        vm.confirmReturn()

        // UnconfinedTestDispatcher runs the launch block eagerly up to its first
        // real suspension (the Room write), so inFlight must already be true here.
        assertTrue(vm.state.value.inFlight)
    }

    @Test
    fun `a second confirmReturn call while the first is in flight is a no-op`() {
        val copyCode = checkoutOneCopy()
        val vm = ReturnViewModel(repo)
        vm.submitCopyCode(copyCode)
        awaitValue(vm.state) { it.loan != null }

        vm.confirmReturn() // begins writing, sets inFlight = true
        vm.confirmReturn() // must be rejected instead of re-submitting the stale loan

        val state = awaitValue(vm.state) { it.returned != null }
        // The regression this guards against: a stale "no active loan" error
        // stacked over the success card from the second, redundant return.
        assertNull(state.error)
        assertFalse(state.inFlight)
    }

    @Test
    fun `rateMember records the rating so the post-return screen can confirm it`() {
        val copyCode = checkoutOneCopy()
        val vm = ReturnViewModel(repo)
        vm.submitCopyCode(copyCode)
        awaitValue(vm.state) { it.loan != null }
        vm.confirmReturn()
        awaitValue(vm.state) { it.awaitingRating }

        vm.rateMember(4)

        val state = awaitValue(vm.state) { !it.awaitingRating }
        assertEquals(4, state.lastRating)
        val persisted = runBlocking { db.loanDao().byId(state.returned!!.id) }
        assertEquals(4, persisted?.rating)
    }

    @Test
    fun `skipRating leaves no rating recorded, distinguishing it from an actual rating`() {
        val copyCode = checkoutOneCopy()
        val vm = ReturnViewModel(repo)
        vm.submitCopyCode(copyCode)
        awaitValue(vm.state) { it.loan != null }
        vm.confirmReturn()
        awaitValue(vm.state) { it.awaitingRating }

        vm.skipRating()

        val state = awaitValue(vm.state) { !it.awaitingRating }
        assertNull(state.lastRating)
    }
}
