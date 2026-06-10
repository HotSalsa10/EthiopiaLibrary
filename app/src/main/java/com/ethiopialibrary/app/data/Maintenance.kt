package com.ethiopialibrary.app.data

import java.io.File

object IntegrityChecker {
    /** PRAGMA quick_check: milliseconds at this DB size; run on app start. */
    fun quickCheck(db: LibraryDatabase): Boolean =
        db.openHelper.readableDatabase.query("PRAGMA quick_check").use { c ->
            c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
        }
}

/**
 * Daily on-device snapshot: last line of defense, independent of cloud sync.
 * Checkpoint-then-copy works on every API level, unlike VACUUM INTO (API 30+).
 */
class SnapshotManager(private val db: LibraryDatabase) {

    fun createSnapshot(dest: File) {
        val database = db.openHelper.writableDatabase
        // Fold the entire WAL into the main DB file so the file copy is complete.
        database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val source = File(requireNotNull(database.path) { "snapshot requires a file-backed database" })
        dest.parentFile?.mkdirs()
        source.copyTo(dest, overwrite = true)
    }
}
