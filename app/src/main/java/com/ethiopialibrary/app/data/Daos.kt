package com.ethiopialibrary.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BookEntity>)

    @Query(
        """
        SELECT b.*,
            (SELECT COUNT(*) FROM book_copies c
             WHERE c.bookId = b.id AND c.isDeleted = 0 AND c.status = 'IN_SERVICE') AS totalCopies,
            (SELECT COUNT(*) FROM book_copies c
             WHERE c.bookId = b.id AND c.isDeleted = 0 AND c.status = 'IN_SERVICE'
               AND NOT EXISTS (
                   SELECT 1 FROM loans l
                   WHERE l.copyId = c.id AND l.returnedAt IS NULL AND l.isDeleted = 0
               )) AS availableCopies
        FROM books b
        WHERE b.isDeleted = 0
          AND (:query = '' OR b.title LIKE '%' || :query || '%' OR b.author LIKE '%' || :query || '%')
        ORDER BY b.title
        """,
    )
    fun withCounts(query: String): Flow<List<BookWithCounts>>

    @Query("SELECT COUNT(*) FROM books WHERE isDeleted = 0")
    fun count(): Flow<Int>
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

    @Query(
        """
        SELECT c.*,
            EXISTS(SELECT 1 FROM loans l
                   WHERE l.copyId = c.id AND l.returnedAt IS NULL AND l.isDeleted = 0) AS onLoan
        FROM book_copies c
        WHERE c.bookId = :bookId AND c.isDeleted = 0
        ORDER BY c.copyCode
        """,
    )
    fun copiesForBook(bookId: String): Flow<List<CopyRow>>

    @Query(
        """
        SELECT c.*, b.title AS bookTitle, b.author AS bookAuthor,
            EXISTS(SELECT 1 FROM loans l
                   WHERE l.copyId = c.id AND l.returnedAt IS NULL AND l.isDeleted = 0) AS onLoan
        FROM book_copies c
        JOIN books b ON b.id = c.bookId
        WHERE c.copyCode = :copyCode AND c.isDeleted = 0
        """,
    )
    suspend fun withBook(copyCode: String): CopyWithBook?

    @Query(
        """
        SELECT c.copyCode AS code, b.title AS title
        FROM book_copies c
        JOIN books b ON b.id = c.bookId
        WHERE c.isDeleted = 0 AND c.status = 'IN_SERVICE'
        ORDER BY c.copyCode
        """,
    )
    suspend fun labelRows(): List<LabelRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BookCopyEntity>)

    @Query("SELECT copyCode FROM book_copies")
    suspend fun allCopyCodes(): List<String>
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

    @Query(
        """
        SELECT m.*,
            (SELECT COUNT(*) FROM loans l
             WHERE l.memberId = m.id AND l.returnedAt IS NULL AND l.isDeleted = 0) AS activeLoans
        FROM members m
        WHERE m.isDeleted = 0
          AND (:query = '' OR m.fullName LIKE '%' || :query || '%' OR m.memberCode LIKE '%' || :query || '%')
        ORDER BY m.fullName
        """,
    )
    fun withLoanCounts(query: String): Flow<List<MemberWithLoanCount>>

    @Query("SELECT COUNT(*) FROM members WHERE isDeleted = 0")
    fun count(): Flow<Int>

    @Query("SELECT memberCode AS code, fullName AS title FROM members WHERE isDeleted = 0 ORDER BY memberCode")
    suspend fun labelRows(): List<LabelRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MemberEntity>)

    @Query("SELECT memberCode FROM members")
    suspend fun allMemberCodes(): List<String>
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

    @Query(
        """
        SELECT l.*, b.title AS bookTitle, c.copyCode AS copyCode,
               m.fullName AS memberName, m.memberCode AS memberCode
        FROM loans l
        JOIN book_copies c ON c.id = l.copyId
        JOIN books b ON b.id = c.bookId
        JOIN members m ON m.id = l.memberId
        WHERE l.returnedAt IS NULL AND l.dueAt < :now AND l.isDeleted = 0
        ORDER BY l.dueAt
        """,
    )
    fun overdueDetailed(now: Long): Flow<List<LoanWithDetails>>

    @Query(
        """
        SELECT l.*, b.title AS bookTitle, c.copyCode AS copyCode,
               m.fullName AS memberName, m.memberCode AS memberCode
        FROM loans l
        JOIN book_copies c ON c.id = l.copyId
        JOIN books b ON b.id = c.bookId
        JOIN members m ON m.id = l.memberId
        WHERE l.memberId = :memberId AND l.returnedAt IS NULL AND l.isDeleted = 0
        ORDER BY l.dueAt
        """,
    )
    fun activeForMemberDetailed(memberId: String): Flow<List<LoanWithDetails>>

    @Query(
        """
        SELECT l.*, b.title AS bookTitle, c.copyCode AS copyCode,
               m.fullName AS memberName, m.memberCode AS memberCode
        FROM loans l
        JOIN book_copies c ON c.id = l.copyId
        JOIN books b ON b.id = c.bookId
        JOIN members m ON m.id = l.memberId
        WHERE c.copyCode = :copyCode AND l.returnedAt IS NULL AND l.isDeleted = 0
        LIMIT 1
        """,
    )
    suspend fun activeForCopyDetailed(copyCode: String): LoanWithDetails?

    @Query("SELECT COUNT(*) FROM loans WHERE returnedAt IS NULL AND isDeleted = 0")
    fun activeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM loans WHERE returnedAt IS NULL AND dueAt < :now AND isDeleted = 0")
    fun overdueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun byId(id: String): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LoanEntity>)
}

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE syncedAt IS NULL ORDER BY localId")
    suspend fun pending(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE syncedAt IS NULL")
    fun pendingCount(): Flow<Int>

    @Query("UPDATE sync_queue SET syncedAt = :at WHERE localId = :id")
    suspend fun markSynced(id: Long, at: Long)

    @Query("UPDATE sync_queue SET attemptCount = attemptCount + 1 WHERE localId = :id")
    suspend fun recordAttempt(id: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT `value` FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT `value` FROM settings WHERE `key` = :key")
    fun watch(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}
