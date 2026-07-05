package com.ethiopialibrary.app.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ethiopialibrary.app.data.CheckoutResult
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var db: LibraryDatabase
    private lateinit var clock: TestClock
    private lateinit var repo: LibraryRepository
    private lateinit var cloud: FakeCloudStore
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = TestClock()
        repo = LibraryRepository(db, clock)
        cloud = FakeCloudStore()
        engine = SyncEngine(db, cloud, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedLibrary(): Triple<String, String, String> = runBlocking {
        val book = repo.addBookWithCopies(
            title = "Oromay",
            author = "Bealu Girma",
            categoryCode = "Fiction",
            language = "am",
            copies = 1,
        )
        val copyCode = repo.copyLabelRows().single().code
        val member = repo.registerMember(fullName = "Abebe Kebede")
        repo.checkout(copyCode, member.memberCode)
        Triple(book.id, copyCode, member.id)
    }

    // ---------- drain ----------

    @Test
    fun `drain uploads pending entries and marks them synced`() = runBlocking {
        val (bookId, _, memberId) = seedLibrary()

        val result = engine.drainOutbox()

        assertTrue(result is SyncResult.Success)
        assertEquals(4, (result as SyncResult.Success).uploaded) // book, copy, member, loan
        assertEquals("Oromay", cloud.collections.getValue("books").getValue(bookId)["title"])
        assertEquals("Abebe Kebede", cloud.collections.getValue("members").getValue(memberId)["fullName"])
        assertEquals(1, cloud.collections.getValue("book_copies").size)
        assertEquals(1, cloud.collections.getValue("loans").size)

        assertEquals(0, repo.pendingSyncEntries().size)
        assertEquals(0, (engine.drainOutbox() as SyncResult.Success).uploaded)
    }

    @Test
    fun `drain records last sync time and clears pending count`() = runBlocking {
        seedLibrary()
        assertNull(repo.lastSyncAt().first())
        assertTrue(repo.pendingSyncCount().first() > 0)

        engine.drainOutbox()

        assertEquals(clock.instant().toEpochMilli(), repo.lastSyncAt().first())
        assertEquals(0, repo.pendingSyncCount().first())
    }

    @Test
    fun `failed upload keeps entry pending and records the attempt`() = runBlocking {
        seedLibrary()
        cloud.failOn = { collection, _ -> collection == "books" }

        val result = engine.drainOutbox()

        assertTrue(result is SyncResult.Failure)
        assertEquals(0, (result as SyncResult.Failure).uploaded) // book is first in the queue
        val pending = repo.pendingSyncEntries()
        assertEquals(4, pending.size)
        assertEquals(1, pending.first { it.entityType == "book" }.attemptCount)
        assertNull(repo.lastSyncAt().first())

        cloud.failOn = null
        assertEquals(4, (engine.drainOutbox() as SyncResult.Success).uploaded)
    }

    @Test
    fun `loan payload carries due date and return state`() = runBlocking {
        val (_, copyCode, _) = seedLibrary()
        clock.advanceDays(20)
        repo.returnBook(copyCode)

        engine.drainOutbox()

        val loanDoc = cloud.collections.getValue("loans").values.single()
        assertEquals(clock.instant().toEpochMilli(), loanDoc["returnedAt"])
        assertNotNull(loanDoc["dueAt"])
    }

    // ---------- batched uploads ----------

    @Test
    fun `changes upload as one atomic batch not per document`() = runBlocking {
        seedLibrary() // book, copy, member, loan = 4 pending entries

        engine.drainOutbox()

        assertEquals(listOf(4), cloud.batchSizes)
    }

    @Test
    fun `large drains are chunked and a failing chunk stays fully pending`() = runBlocking {
        seedLibrary() // queue order: book, copy, member, loan
        val chunkedEngine = SyncEngine(db, cloud, clock, batchSize = 2)
        cloud.failOn = { collection, _ -> collection == "loans" } // second chunk

        val result = chunkedEngine.drainOutbox()

        // First chunk (book, copy) committed; second chunk (member, loan) aborted whole.
        assertTrue(result is SyncResult.Failure)
        assertEquals(2, (result as SyncResult.Failure).uploaded)
        assertEquals(listOf(2), cloud.batchSizes)
        assertEquals(2, repo.pendingSyncEntries().size)

        cloud.failOn = null
        assertEquals(2, (chunkedEngine.drainOutbox() as SyncResult.Success).uploaded)
        assertEquals(listOf(2, 2), cloud.batchSizes)
    }

    // ---------- honest status reporting ----------

    @Test
    fun `drain success records an ok result`() = runBlocking {
        seedLibrary()
        assertNull(repo.lastSyncResult().first())

        engine.drainOutbox()

        assertEquals("ok", repo.lastSyncResult().first())
    }

    @Test
    fun `drain failure records an error result without advancing the timestamp`() = runBlocking {
        seedLibrary()
        cloud.failOn = { collection, _ -> collection == "books" }

        engine.drainOutbox()

        val result = repo.lastSyncResult().first()
        assertTrue("expected error result, was $result", result != null && result.startsWith("error"))
        assertNull(repo.lastSyncAt().first())

        cloud.failOn = null
        engine.drainOutbox()
        assertEquals("ok", repo.lastSyncResult().first())
    }

    @Test
    fun `restore stamps the backup status so a fresh tablet shows backed up`() = runBlocking {
        seedLibrary()
        engine.drainOutbox()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db2 = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo2 = LibraryRepository(db2, clock)
        try {
            SyncEngine(db2, cloud, clock).restore()

            assertEquals(clock.instant().toEpochMilli(), repo2.lastSyncAt().first())
            assertEquals("ok", repo2.lastSyncResult().first())
        } finally {
            db2.close()
        }
    }

    @Test
    fun `oldest pending change time is exposed for the backup warning`() = runBlocking {
        assertNull(repo.oldestPendingSince().first())
        val createdAt = clock.instant().toEpochMilli()
        seedLibrary()
        clock.advanceDays(5)

        assertEquals(createdAt, repo.oldestPendingSince().first())

        engine.drainOutbox()
        assertNull(repo.oldestPendingSince().first())
    }

    // ---------- restore ----------

    @Test
    fun `restore rebuilds a fresh database from the cloud`() = runBlocking {
        // Source tablet: catalog, members, one active loan, custom loan period.
        repo.setLoanPeriodDays(21)
        repo.addBookWithCopies(title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am", copies = 2)
        repo.addBookWithCopies(title = "Fiqir Eske Meqabir", author = "Haddis Alemayehu", categoryCode = "Fiction", language = "am", copies = 1)
        val member = repo.registerMember(fullName = "Abebe Kebede")
        repo.registerMember(fullName = "Sara Tesfaye")
        val copyCode = repo.copyLabelRows().first().code
        repo.checkout(copyCode, member.memberCode)
        engine.drainOutbox()

        // Replacement tablet: empty database, same cloud.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db2 = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo2 = LibraryRepository(db2, clock)
        try {
            SyncEngine(db2, cloud, clock).restore()

            val books = repo2.booksWithCounts("").first()
            assertEquals(2, books.size)
            assertEquals(3, books.sumOf { it.totalCopies })
            assertEquals(2, repo2.membersWithLoanCounts("").first().size)
            assertNotNull(repo2.activeLoanDetailedForCopy(copyCode))
            assertEquals(21, repo2.loanPeriodDays())
        } finally {
            db2.close()
        }
    }

    @Test
    fun `member id, address and loan rating survive backup and restore`() = runBlocking {
        repo.addBookWithCopies(title = "Oromay", author = "A", categoryCode = "Fiction", language = "am", copies = 1)
        val copyCode = repo.copyLabelRows().single().code
        val member = repo.registerMember(
            fullName = "Abebe Kebede",
            phone = null,
            nationalId = "ID-7",
            address = "Bole, Addis Ababa",
        )
        repo.checkout(copyCode, member.memberCode)
        val loanId = repo.activeLoanDetailedForCopy(copyCode)!!.loan.id
        repo.returnBook(copyCode)
        repo.rateLoan(loanId, 5)
        engine.drainOutbox()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db2 = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo2 = LibraryRepository(db2, clock)
        try {
            SyncEngine(db2, cloud, clock).restore()

            val restored = repo2.memberByCode(member.memberCode)!!
            assertEquals("ID-7", restored.nationalId)
            assertEquals("Bole, Addis Ababa", restored.address)
            assertEquals(5.0, repo2.memberAverageRating(restored.id)!!, 0.001)
        } finally {
            db2.close()
        }
    }

    @Test
    fun `restore recomputes code sequences so new codes never collide`() = runBlocking {
        repo.addBookWithCopies(title = "Oromay", author = "A", categoryCode = "C", language = "am", copies = 3)
        repo.registerMember(fullName = "Abebe")
        repo.registerMember(fullName = "Sara")
        engine.drainOutbox()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db2 = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo2 = LibraryRepository(db2, clock)
        try {
            SyncEngine(db2, cloud, clock).restore()

            val book = repo2.booksWithCounts("").first().single().book
            // Copy number derives from restored data (3 copies -> next is 4),
            // so codes never collide even without a copy counter.
            assertEquals("C-001-4-00", repo2.addCopy(book.id).copyCode)
            assertEquals("M-0003", repo2.registerMember(fullName = "Third").memberCode)
        } finally {
            db2.close()
        }
    }

    // ---------- restore robustness (regressions found in the device drill) ----------

    @Test
    fun `restore onto a non-empty tablet does not violate foreign keys`() = runBlocking {
        // Backup a book with a copy, then restore onto a tablet that already
        // holds that same book+copy locally. A REPLACE-based upsert would
        // delete the parent book (orphaning the existing copy) and fail the
        // foreign key; @Upsert updates in place instead.
        repo.addBookWithCopies(title = "Oromay", author = "A", categoryCode = "C", language = "am", copies = 1)
        engine.drainOutbox()

        val restored = engine.restore() // same db, already populated

        assertTrue(restored > 0)
        assertEquals(1, repo.booksWithCounts("").first().size)
        assertEquals(1, repo.copyLabelRows().size)
    }

    @Test
    fun `restore skips a malformed cloud document instead of aborting`() = runBlocking {
        repo.addBookWithCopies(title = "Good Book", author = "A", categoryCode = "C", language = "am", copies = 1)
        engine.drainOutbox()
        // A partial write from an older broken backup: a book row missing its
        // required title.
        cloud.collections.getValue("books")["broken-id"] = mapOf(
            "author" to "X", "categoryCode" to "C", "bookNumber" to 9L,
            "language" to "am", "createdAt" to 1L, "updatedAt" to 1L, "isDeleted" to false,
        )

        val db2 = freshDb()
        val repo2 = LibraryRepository(db2, clock)
        try {
            val restored = SyncEngine(db2, cloud, clock).restore()

            assertTrue(restored > 0)
            // Only the well-formed book made it in; the malformed one was skipped.
            assertEquals(1, repo2.booksWithCounts("").first().size)
        } finally {
            db2.close()
        }
    }

    @Test
    fun `restore drops a loan whose copy never made it to the cloud`() = runBlocking {
        repo.addBookWithCopies(title = "Oromay", author = "A", categoryCode = "C", language = "am", copies = 1)
        val member = repo.registerMember(fullName = "Abebe")
        val copyCode = repo.copyLabelRows().single().code
        repo.checkout(copyCode, member.memberCode)
        engine.drainOutbox()
        // Simulate an inconsistent cloud: the copy the loan points at is gone.
        cloud.collections.getValue("book_copies").clear()

        val db2 = freshDb()
        val repo2 = LibraryRepository(db2, clock)
        try {
            // Must not throw a foreign-key error; the orphaned loan is dropped.
            SyncEngine(db2, cloud, clock).restore()

            assertEquals(1, repo2.membersWithLoanCounts("").first().size)
            assertNull(repo2.activeLoanDetailedForCopy(copyCode))
        } finally {
            db2.close()
        }
    }

    private fun freshDb(): LibraryDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
