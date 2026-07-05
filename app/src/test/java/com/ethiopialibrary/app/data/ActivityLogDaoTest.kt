package com.ethiopialibrary.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityLogDaoTest {

    private lateinit var db: LibraryDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recent returns newest first, windowed and limited`() = runBlocking {
        val dao = db.activityLogDao()
        dao.insert(ActivityLogEntity(id = "a1", type = "CHECKOUT", loanId = "l1", at = 10))
        dao.insert(ActivityLogEntity(id = "a2", type = "RETURN", loanId = "l1", at = 30))
        dao.insert(ActivityLogEntity(id = "a3", type = "RENEW", loanId = "l2", at = 20, prevDueAt = 99))
        dao.insert(ActivityLogEntity(id = "old", type = "CHECKOUT", loanId = "l3", at = 1))

        // Window excludes "old"; order is newest first.
        assertEquals(
            listOf("a2", "a3", "a1"),
            dao.recent(since = 5, limit = 10).first().map { it.id },
        )
        // Limit trims from the oldest end.
        assertEquals(
            listOf("a2", "a3"),
            dao.recent(since = 5, limit = 2).first().map { it.id },
        )
    }
}
