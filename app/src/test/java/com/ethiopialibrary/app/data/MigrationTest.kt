package com.ethiopialibrary.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves real tablet data survives every schema bump. createDatabase() builds
 * the OLD schema from app/schemas/<version>.json; runMigrationsAndValidate()
 * runs the migration and validates the result against the NEW exported schema.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LibraryDatabase::class.java,
    )

    @Test
    fun `v3 data survives the migration to v4`() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, code, name, sortOrder, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('cat-TF','TF','التفسير',0,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO books (id, title, author, categoryCode, bookNumber, language, isbn, notes, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('b1','Oromay','Bealu Girma','TF',1,'am',NULL,NULL,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO members (id, memberCode, fullName, phone, nationalId, address, joinedAt, status, notes, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('m1','M-0001','Abebe Kebede',NULL,'ID-7','Bole',1,'ACTIVE',NULL,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO book_copies (id, bookId, copyCode, copyNumber, volumeNumber, shelfLocation, status, acquiredAt, notes, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('c1','b1','TF-001-1-00',1,0,NULL,'IN_SERVICE',1,NULL,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO loans (id, copyId, memberId, borrowedAt, dueAt, returnedAt, rating, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('l1','c1','m1',1,100,NULL,NULL,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO settings (`key`, `value`) VALUES ('loan_period_days','21')",
            )
        }

        helper.runMigrationsAndValidate(DB, 4, true, Migrations.MIGRATION_3_4).use { db ->
            assertEquals(1, db.count("categories"))
            assertEquals(1, db.count("books"))
            assertEquals(1, db.count("members"))
            assertEquals(1, db.count("book_copies"))
            assertEquals(1, db.count("loans"))
            assertEquals(0, db.count("activity_log"))
            // The active loan is untouched.
            db.query("SELECT dueAt, returnedAt FROM loans WHERE id = 'l1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(100L, c.getLong(0))
                assertTrue(c.isNull(1))
            }
            // The custom setting is untouched.
            db.query("SELECT `value` FROM settings WHERE `key` = 'loan_period_days'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("21", c.getString(0))
            }
        }
    }

    @Test
    fun `v4 data survives the migration to v5`() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, code, name, sortOrder, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('cat-TF','TF','التفسير',0,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO books (id, title, author, categoryCode, bookNumber, language, isbn, notes, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('b1','Oromay','Bealu Girma','TF',1,'am',NULL,NULL,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO members (id, memberCode, fullName, phone, nationalId, address, joinedAt, status, notes, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('m1','M-0001','Abebe Kebede',NULL,'ID-7','Bole',1,'ACTIVE',NULL,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO book_copies (id, bookId, copyCode, copyNumber, volumeNumber, shelfLocation, status, acquiredAt, notes, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('c1','b1','TF-001-1-00',1,0,NULL,'IN_SERVICE',1,NULL,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO loans (id, copyId, memberId, borrowedAt, dueAt, returnedAt, rating, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('l1','c1','m1',1,100,NULL,NULL,1,1,0)",
            )
            db.execSQL(
                "INSERT INTO settings (`key`, `value`) VALUES ('loan_period_days','21')",
            )
            // v4 shape: no undoneAt column exists yet.
            db.execSQL(
                "INSERT INTO activity_log (id, type, loanId, at, prevDueAt) " +
                    "VALUES ('a1','CHECKOUT','l1',5,NULL)",
            )
        }

        helper.runMigrationsAndValidate(DB, 5, true, Migrations.MIGRATION_4_5).use { db ->
            assertEquals(1, db.count("activity_log"))
            // The pre-existing activity_log row survived, untouched, with the
            // new undoneAt column reading NULL.
            db.query("SELECT type, loanId, at, prevDueAt, undoneAt FROM activity_log WHERE id = 'a1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("CHECKOUT", c.getString(0))
                assertEquals("l1", c.getString(1))
                assertEquals(5L, c.getLong(2))
                assertTrue(c.isNull(3))
                assertTrue(c.isNull(4))
            }
        }
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private companion object {
        const val DB = "migration-test.db"
    }
}
