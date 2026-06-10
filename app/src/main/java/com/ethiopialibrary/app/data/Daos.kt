package com.ethiopialibrary.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BookDao {
    @Insert
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: String): BookEntity?
}

@Dao
interface BookCopyDao {
    @Insert
    suspend fun insert(copy: BookCopyEntity)

    @Update
    suspend fun update(copy: BookCopyEntity)

    @Query("SELECT * FROM book_copies WHERE id = :id")
    suspend fun byId(id: String): BookCopyEntity?

    @Query("SELECT * FROM book_copies WHERE copyCode = :code AND isDeleted = 0")
    suspend fun byCode(code: String): BookCopyEntity?

    @Query(
        """
        SELECT COUNT(*) FROM book_copies c
        WHERE c.bookId = :bookId
          AND c.status = 'IN_SERVICE'
          AND c.isDeleted = 0
          AND NOT EXISTS (
              SELECT 1 FROM loans l
              WHERE l.copyId = c.id AND l.returnedAt IS NULL AND l.isDeleted = 0
          )
        """,
    )
    suspend fun availableCount(bookId: String): Int
}

@Dao
interface MemberDao {
    @Insert
    suspend fun insert(member: MemberEntity)

    @Update
    suspend fun update(member: MemberEntity)

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun byId(id: String): MemberEntity?

    @Query("SELECT * FROM members WHERE memberCode = :code AND isDeleted = 0")
    suspend fun byCode(code: String): MemberEntity?
}

@Dao
interface LoanDao {
    @Insert
    suspend fun insert(loan: LoanEntity)

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("SELECT * FROM loans WHERE copyId = :copyId AND returnedAt IS NULL AND isDeleted = 0 LIMIT 1")
    suspend fun activeLoanForCopy(copyId: String): LoanEntity?

    @Query("SELECT * FROM loans WHERE returnedAt IS NULL AND dueAt < :now AND isDeleted = 0 ORDER BY dueAt")
    suspend fun overdue(now: Long): List<LoanEntity>
}

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE syncedAt IS NULL ORDER BY localId")
    suspend fun pending(): List<SyncQueueEntity>
}

@Dao
interface SettingsDao {
    @Query("SELECT `value` FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}
