package com.ethiopialibrary.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        BookCopyEntity::class,
        MemberEntity::class,
        LoanEntity::class,
        SyncQueueEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun bookCopyDao(): BookCopyDao
    abstract fun memberDao(): MemberDao
    abstract fun loanDao(): LoanDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        /**
         * Power-loss-safe configuration for cheap hardware:
         * WAL keeps the main DB file consistent through sudden power cuts;
         * synchronous=NORMAL is the documented integrity-safe level with WAL.
         */
        fun create(context: Context, name: String = "library.db"): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, name)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA synchronous = NORMAL")
                    }
                })
                .build()
    }
}
