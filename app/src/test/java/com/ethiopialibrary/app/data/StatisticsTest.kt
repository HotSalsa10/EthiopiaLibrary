package com.ethiopialibrary.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatisticsTest {

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

    @Test
    fun `totals are counted`() = runBlocking {
        repo.addBookWithCopies(title = "A", author = "x", categoryCode = "Fiction", language = "am", copies = 2)
        repo.addBookWithCopies(title = "B", author = "y", categoryCode = "History", language = "en", copies = 1)
        val m1 = repo.registerMember(fullName = "M1")
        repo.registerMember(fullName = "M2")
        val codes = repo.copyLabelRows().map { it.code }
        repo.checkout(codes[0], m1.memberCode)
        repo.checkout(codes[1], m1.memberCode)

        val s = repo.computeStatistics()

        assertEquals(2, s.totalTitles)
        assertEquals(3, s.totalCopies)
        assertEquals(2, s.totalMembers)
        assertEquals(2, s.activeLoans)
        assertEquals(0, s.overdue)
    }

    @Test
    fun `top books and members ranked by loan count`() = runBlocking {
        repo.addBookWithCopies(title = "Popular", author = "x", categoryCode = "C", language = "am", copies = 3)
        repo.addBookWithCopies(title = "Rare", author = "y", categoryCode = "C", language = "am", copies = 1)
        val heavy = repo.registerMember(fullName = "Heavy")
        val light = repo.registerMember(fullName = "Light")
        val codes = repo.copyLabelRows().map { it.code } // B-0001..3 = Popular, B-0004 = Rare
        repo.checkout(codes[0], heavy.memberCode)
        repo.checkout(codes[1], heavy.memberCode)
        repo.checkout(codes[2], heavy.memberCode)
        repo.checkout(codes[3], light.memberCode)

        val s = repo.computeStatistics()

        assertEquals("Popular", s.topBooks.first().label)
        assertEquals(3, s.topBooks.first().count)
        assertEquals("Heavy", s.topMembers.first().label)
        assertEquals(3, s.topMembers.first().count)
    }

    @Test
    fun `category and language breakdowns`() = runBlocking {
        repo.addBookWithCopies(title = "A", author = "x", categoryCode = "Fiction", language = "am", copies = 1)
        repo.addBookWithCopies(title = "B", author = "y", categoryCode = "Fiction", language = "en", copies = 1)
        repo.addBookWithCopies(title = "C", author = "z", categoryCode = "History", language = "am", copies = 1)

        val s = repo.computeStatistics()

        assertEquals(2, s.byCategory.first { it.label == "Fiction" }.count)
        assertEquals(1, s.byCategory.first { it.label == "History" }.count)
        assertEquals(2, s.byLanguage.first { it.label == "am" }.count)
    }

    @Test
    fun `recent activity windows split last and previous 30 days`() = runBlocking {
        repo.addBookWithCopies(title = "A", author = "x", categoryCode = "C", language = "am", copies = 2)
        val codes = repo.copyLabelRows().map { it.code }
        val m = repo.registerMember(fullName = "M")
        repo.checkout(codes[0], m.memberCode) // day 0

        var s = repo.computeStatistics()
        assertEquals(1, s.checkoutsLast30)
        assertEquals(0, s.checkoutsPrev30)

        clock.advanceDays(40)
        s = repo.computeStatistics()
        assertEquals(0, s.checkoutsLast30)
        assertEquals(1, s.checkoutsPrev30)
    }

    @Test
    fun `csv contains key figures`() = runBlocking {
        repo.addBookWithCopies(title = "A", author = "x", categoryCode = "C", language = "am", copies = 2)
        val csv = buildStatisticsCsv(repo.computeStatistics())
        assertTrue(csv.contains("Total titles,1"))
        assertTrue(csv.contains("Total copies,2"))
    }
}
