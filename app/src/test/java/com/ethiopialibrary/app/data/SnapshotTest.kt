package com.ethiopialibrary.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Local snapshot = last line of defense against data loss,
 * independent of cloud sync. Must work on every API level (no VACUUM INTO).
 */
@RunWith(RobolectricTestRunner::class)
class SnapshotTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `snapshot file is a complete copy of the database`() {
        val db = LibraryDatabase.create(context(), "snapshot-src.db")
        val repo = LibraryRepository(db, TestClock())
        runBlocking {
            val book = repo.addBook(title = "Title", author = "Author", categoryCode = "Fiction", language = "am")
            repo.addCopy(book.id)
        }
        val dest = File(context().cacheDir, "snapshot-copy.db")
        try {
            SnapshotManager(db).createSnapshot(dest)

            assertTrue(dest.exists() && dest.length() > 0)
            SQLiteDatabase.openDatabase(dest.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
                raw.rawQuery("SELECT COUNT(*) FROM books", null).use { c ->
                    c.moveToFirst()
                    assertEquals(1, c.getInt(0))
                }
                raw.rawQuery("SELECT COUNT(*) FROM book_copies", null).use { c ->
                    c.moveToFirst()
                    assertEquals(1, c.getInt(0))
                }
            }
        } finally {
            db.close()
            dest.delete()
            context().deleteDatabase("snapshot-src.db")
        }
    }

    @Test
    fun `quick check passes on a healthy database`() {
        val db = LibraryDatabase.create(context(), "integrity-test.db")
        try {
            assertTrue(IntegrityChecker.quickCheck(db))
        } finally {
            db.close()
            context().deleteDatabase("integrity-test.db")
        }
    }
}
