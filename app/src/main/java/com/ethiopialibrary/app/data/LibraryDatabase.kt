package com.ethiopialibrary.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        BookEntity::class,
        BookCopyEntity::class,
        MemberEntity::class,
        LoanEntity::class,
        SyncQueueEntity::class,
        SettingEntity::class,
        ActivityLogEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun bookDao(): BookDao
    abstract fun bookCopyDao(): BookCopyDao
    abstract fun memberDao(): MemberDao
    abstract fun loanDao(): LoanDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun settingsDao(): SettingsDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        /**
         * Power-loss-safe configuration for cheap hardware:
         * WAL keeps the main DB file consistent through sudden power cuts;
         * synchronous=NORMAL is the documented integrity-safe level with WAL.
         */
        fun create(context: Context, name: String = "library.db"): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, name)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // Real data lives on the production tablet: every schema bump
                // must ship a tested migration (see Migrations.kt). There is
                // deliberately NO destructive fallback.
                .addMigrations(*Migrations.ALL)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Seed categories with deterministic ids (cat-<code>) so a
                        // cloud restore upserts the same rows instead of colliding
                        // on the unique code index.
                        val t = System.currentTimeMillis()
                        CategorySeed.entries.forEachIndexed { i, (name, code) ->
                            db.execSQL(
                                "INSERT INTO categories (id, code, name, sortOrder, createdAt, updatedAt, isDeleted) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, 0)",
                                arrayOf<Any?>("cat-$code", code, name, i, t, t),
                            )
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA synchronous = NORMAL")
                    }
                })
                .build()
    }
}
