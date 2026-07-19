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

    /** v5: activity_log gains undoneAt, so a repeat-undo click on the same entry can be rejected. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `activity_log` ADD COLUMN `undoneAt` INTEGER")
        }
    }

    /** v6: books gain volumeCount (volumes per copy), backfilled from existing copies. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `books` ADD COLUMN `volumeCount` INTEGER NOT NULL DEFAULT 1")
            // Backfill: a book whose copies use volume numbers 1..N is an N-volume
            // work; volumeNumber 0 (single-volume convention) and no copies mean 1.
            db.execSQL(
                "UPDATE `books` SET `volumeCount` = COALESCE(" +
                    "(SELECT MAX(c.`volumeNumber`) FROM `book_copies` c " +
                    "WHERE c.`bookId` = `books`.`id` AND c.`volumeNumber` > 0), 1)",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
