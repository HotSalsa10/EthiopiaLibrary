package com.ethiopialibrary.app.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.TestClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private lateinit var db: LibraryDatabase
    private lateinit var cloud: FakeCloudStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cloud = FakeCloudStore()
        val clock = TestClock()
        runBlocking {
            LibraryRepository(db, clock).addBook(
                title = "T", author = "A", categoryCode = "C", language = "am",
            )
        }
        SyncLocator.engineFactory = { SyncEngine(db, cloud, clock) }
    }

    @After
    fun tearDown() {
        SyncLocator.engineFactory = null
        db.close()
    }

    private fun runWorker(): ListenableWorker.Result {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        return runBlocking { worker.doWork() }
    }

    @Test
    fun `worker succeeds when drain succeeds`() {
        assertTrue(runWorker() is ListenableWorker.Result.Success)
        assertTrue(cloud.collections.getValue("books").isNotEmpty())
    }

    @Test
    fun `worker retries when an upload fails`() {
        cloud.failOn = { _, _ -> true }
        assertTrue(runWorker() is ListenableWorker.Result.Retry)
    }

    @Test
    fun `worker succeeds quietly when cloud is not configured`() {
        SyncLocator.engineFactory = null
        assertTrue(runWorker() is ListenableWorker.Result.Success)
    }

    // ---------- debounced backup on data change ----------

    @Test
    fun `debounced backup enqueues exactly one unique work across repeated triggers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        // Simulates several data changes arriving before the debounce fires -
        // KEEP must coalesce them into a single scheduled upload, not queue one per change.
        SyncWorker.debouncedBackup(context)
        SyncWorker.debouncedBackup(context)
        SyncWorker.debouncedBackup(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("cloud-backup-debounced")
            .get()
        assertEquals(1, infos.size)
        assertTrue(infos.single().state == WorkInfo.State.ENQUEUED)
    }
}
