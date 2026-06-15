package com.ethiopialibrary.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LimitsHistoryTest {

    private lateinit var db: LibraryDatabase
    private lateinit var clock: TestClock
    private lateinit var repo: LibraryRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LibraryDatabase::class.java)
            .allowMainThreadQueries().build()
        clock = TestClock()
        repo = LibraryRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun seedCopies(n: Int): List<String> = runBlocking {
        repo.addBookWithCopies(title = "Oromay", author = "A", category = "Fiction", language = "am", copies = n)
        repo.copyLabelRows().map { it.code }
    }

    private fun member(name: String = "Abebe") = runBlocking { repo.registerMember(fullName = name) }

    // ---------- borrowing limit ----------

    @Test
    fun `default borrowing limit is 3`() = runBlocking {
        assertEquals(3, repo.maxBooksPerMember())
    }

    @Test
    fun `borrowing limit is configurable`() = runBlocking {
        repo.setMaxBooksPerMember(5)
        assertEquals(5, repo.maxBooksPerMember())
        repo.setMaxBooksPerMember(0)
        assertEquals(0, repo.maxBooksPerMember())
    }

    @Test
    fun `checkout blocked when member at limit`() = runBlocking {
        repo.setMaxBooksPerMember(2)
        val codes = seedCopies(3)
        val m = member()
        assertTrue(repo.checkout(codes[0], m.memberCode) is CheckoutResult.Success)
        assertTrue(repo.checkout(codes[1], m.memberCode) is CheckoutResult.Success)
        assertTrue(repo.checkout(codes[2], m.memberCode) is CheckoutResult.LimitReached)
    }

    @Test
    fun `limit zero means unlimited`() = runBlocking {
        repo.setMaxBooksPerMember(0)
        val codes = seedCopies(4)
        val m = member()
        codes.forEach { assertTrue(repo.checkout(it, m.memberCode) is CheckoutResult.Success) }
    }

    // ---------- due-soon ----------

    @Test
    fun `default due soon window is 3`() = runBlocking {
        assertEquals(3, repo.dueSoonDays())
    }

    @Test
    fun `due soon window is configurable`() = runBlocking {
        repo.setDueSoonDays(7)
        assertEquals(7, repo.dueSoonDays())
    }

    @Test
    fun `due soon lists active loans within window and excludes overdue`() = runBlocking {
        val codes = seedCopies(2)
        val m = member()
        repo.checkout(codes[0], m.memberCode) // due day 14
        clock.advanceDays(13) // now day 13: copy0 due in 1 day -> within window
        repo.checkout(codes[1], m.memberCode) // due day 27 -> far away

        var due = repo.dueSoonLoans().first()
        assertEquals(1, due.size)
        assertEquals(codes[0], due[0].copyCode)

        clock.advanceDays(7) // now day 20: copy0 overdue, copy1 due day27 (7 days away)
        due = repo.dueSoonLoans().first()
        assertTrue(due.isEmpty())
    }

    // ---------- history ----------

    @Test
    fun `member history shows only returned loans`() = runBlocking {
        val codes = seedCopies(2)
        val m = member()
        repo.checkout(codes[0], m.memberCode)
        repo.checkout(codes[1], m.memberCode)
        repo.returnBook(codes[0])

        val hist = repo.memberHistory(m.id).first()
        assertEquals(1, hist.size)
        assertEquals(codes[0], hist[0].copyCode)
    }

    @Test
    fun `book history shows returned loans across its copies`() = runBlocking {
        val book = repo.addBookWithCopies(title = "Oromay", author = "A", category = "C", language = "am", copies = 2)
        val codes = repo.copyLabelRows().map { it.code }
        val m = member()
        repo.checkout(codes[0], m.memberCode)
        repo.returnBook(codes[0])
        repo.checkout(codes[1], m.memberCode) // active -> not in history

        val hist = repo.bookHistory(book.id).first()
        assertEquals(1, hist.size)
        assertEquals(codes[0], hist[0].copyCode)
    }
}
