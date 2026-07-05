package com.ethiopialibrary.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema version bump MUST ship a migration here plus a MigrationTest
 * case proving old data survives. Real data lives on the production tablet;
 * there is no destructive fallback any more.
 */
object Migrations {

    /** v4: index books.categoryCode + the activity_log feed/undo table. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // SQL mirrors the exported 4.json schema exactly (validated by
            // MigrationTest against app/schemas).
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_books_categoryCode` ON `books` (`categoryCode`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `activity_log` (" +
                    "`id` TEXT NOT NULL, `type` TEXT NOT NULL, `loanId` TEXT NOT NULL, " +
                    "`at` INTEGER NOT NULL, `prevDueAt` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_activity_log_at` ON `activity_log` (`at`)",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_3_4)
}
