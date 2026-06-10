package com.ethiopialibrary.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The database must survive sudden power loss on cheap hardware:
 * WAL journal mode + synchronous=NORMAL is the corruption-safe configuration.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseConfigTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `journal mode is WAL`() {
        val db = LibraryDatabase.create(context(), "config-test-wal.db")
        try {
            db.openHelper.readableDatabase.query("PRAGMA journal_mode").use { c ->
                c.moveToFirst()
                assertEquals("wal", c.getString(0).lowercase())
            }
        } finally {
            db.close()
            context().deleteDatabase("config-test-wal.db")
        }
    }

    @Test
    fun `synchronous level is NORMAL`() {
        val db = LibraryDatabase.create(context(), "config-test-sync.db")
        try {
            db.openHelper.readableDatabase.query("PRAGMA synchronous").use { c ->
                c.moveToFirst()
                // 0=OFF, 1=NORMAL, 2=FULL
                assertEquals(1, c.getInt(0))
            }
        } finally {
            db.close()
            context().deleteDatabase("config-test-sync.db")
        }
    }
}
