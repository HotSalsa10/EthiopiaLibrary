package com.ethiopialibrary.app.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.TestClock
import kotlinx.coroutines.runBlocking
import org.junit.After
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
                title = "T", author = "A", category = "C", language = "am",
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
}
