package com.ethiopialibrary.app.maintenance

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.TestClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/** Daily local snapshots: last line of defense, independent of cloud sync. */
@RunWith(RobolectricTestRunner::class)
class MaintenanceTest {

    private lateinit var db: LibraryDatabase
    private lateinit var snapshotDir: File
    private val clock = TestClock() // fixed at 2026-06-10T09:00:00Z

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = LibraryDatabase.create(context, "maintenance-test.db")
        runBlocking {
            LibraryRepository(db, clock).addBook(
                title = "Oromay", author = "Bealu Girma", categoryCode = "Fiction", language = "am",
            )
        }
        snapshotDir = File(context.cacheDir, "snapshots-test-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        MaintenanceLocator.managerFactory = null
        db.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("maintenance-test.db")
        snapshotDir.deleteRecursively()
    }

    private fun manager(keep: Int = 7) = MaintenanceManager(db, snapshotDir, clock, keepCount = keep)

    @Test
    fun `creates a dated snapshot that is a valid database copy`() {
        val result = manager().runDailyMaintenance()

        assertTrue(result.databaseHealthy)
        assertEquals("library-20260610-090000.db", result.snapshot.name)
        assertTrue(result.snapshot.exists() && result.snapshot.length() > 0)
        SQLiteDatabase.openDatabase(
            result.snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
        ).use { raw ->
            raw.rawQuery("SELECT COUNT(*) FROM books", null).use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun `prunes old snapshots keeping only the newest`() {
        snapshotDir.mkdirs()
        // Eight older snapshots, named so the dates sort lexically.
        (1..8).forEach { day ->
            File(snapshotDir, "library-2026060%d-090000.db".format(day)).writeText("old")
        }

        val result = manager(keep = 7).runDailyMaintenance()

        assertEquals(2, result.prunedCount) // 8 old + 1 new = 9 -> keep 7
        val remaining = snapshotDir.listFiles()!!.map { it.name }.sorted()
        assertEquals(7, remaining.size)
        assertTrue(result.snapshot.name in remaining)
        assertFalse("library-20260601-090000.db" in remaining)
        assertFalse("library-20260602-090000.db" in remaining)
    }

    @Test
    fun `worker runs maintenance via locator`() {
        MaintenanceLocator.managerFactory = { manager() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<MaintenanceWorker>(context).build()

        val result = runBlocking { worker.doWork() }

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(snapshotDir.listFiles()!!.isNotEmpty())
    }

    @Test
    fun `worker succeeds quietly when not configured`() {
        MaintenanceLocator.managerFactory = null
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<MaintenanceWorker>(context).build()
        assertTrue(runBlocking { worker.doWork() } is ListenableWorker.Result.Success)
    }

    @Test
    fun `daily maintenance reports the number of overdue loans`() {
        runBlocking {
            val repo = LibraryRepository(db, clock)
            val book = repo.addBook(title = "Fiqh", author = "Author", categoryCode = "C", language = "am")
            val copy = repo.addCopy(book.id)
            val member = repo.registerMember(fullName = "Abebe")
            repo.checkout(copy.copyCode, member.memberCode)
            clock.advanceDays(60) // push the due date well into the past
        }

        val result = manager().runDailyMaintenance()

        assertEquals(1, result.overdueCount)
    }

    @Test
    fun `returned loans are not counted as overdue`() {
        runBlocking {
            val repo = LibraryRepository(db, clock)
            val book = repo.addBook(title = "Fiqh", author = "Author", categoryCode = "C", language = "am")
            val copy = repo.addCopy(book.id)
            val member = repo.registerMember(fullName = "Abebe")
            repo.checkout(copy.copyCode, member.memberCode)
            repo.returnBook(copy.copyCode)
            clock.advanceDays(60)
        }

        assertEquals(0, manager().runDailyMaintenance().overdueCount)
    }

    @Test
    fun `worker posts an overdue notification when loans are overdue`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            val repo = LibraryRepository(db, clock)
            val book = repo.addBook(title = "Fiqh", author = "Author", categoryCode = "C", language = "am")
            val copy = repo.addCopy(book.id)
            val member = repo.registerMember(fullName = "Abebe")
            repo.checkout(copy.copyCode, member.memberCode)
            clock.advanceDays(60)
        }
        MaintenanceLocator.managerFactory = { manager() }
        val worker = TestListenableWorkerBuilder<MaintenanceWorker>(context).build()

        val result = runBlocking { worker.doWork() }

        assertTrue(result is ListenableWorker.Result.Success)
        val nm = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(nm).size())
    }
}
