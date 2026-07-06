package com.ethiopialibrary.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity)

    // Upsert (not REPLACE): updates existing rows in place instead of
    // delete-then-insert, so a restore onto a non-empty tablet can't orphan
    // FK children (e.g. copies of an existing book) mid-transaction.
    @Upsert
    suspend fun upsertAll(items: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder, name")
    fun all(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder, name")
    suspend fun allOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE code = :code AND isDeleted = 0")
    suspend fun byCode(code: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM categories")
    suspend fun maxSortOrder(): Int?
}

@Dao
interface BookDao {
    @Insert
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: String): BookEntity?

    @Upsert
    suspend fun upsertAll(items: List<BookEntity>)

    /** Highest book number used in a category, so the next is +1. Null when empty. */
    @Query("SELECT MAX(bookNumber) FROM books WHERE categoryCode = :categoryCode AND isDeleted = 0")
    suspend fun maxBookNumber(categoryCode: String): Int?

    @Query(
        """
        SELECT b.*,
            (SELECT cat.name FROM categories cat WHERE cat.code = b.categoryCode) AS categoryName,
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
          AND (:categoryCode = '' OR b.categoryCode = :categoryCode)
          AND (:query = ''
               OR b.title LIKE '%' || :query || '%'
               OR b.author LIKE '%' || :query || '%'
               OR EXISTS (SELECT 1 FROM book_copies c WHERE c.bookId = b.id
                          AND c.isDeleted = 0 AND c.copyCode LIKE '%' || :query || '%'))
        ORDER BY b.categoryCode, b.bookNumber
        """,
    )
    fun withCounts(query: String, categoryCode: String): Flow<List<BookWithCounts>>

    @Query("SELECT COUNT(*) FROM books WHERE isDeleted = 0")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books WHERE isDeleted = 0")
    suspend fun titleCount(): Int

    @Query(
        """
        SELECT COALESCE((SELECT cat.name FROM categories cat WHERE cat.code = b.categoryCode), b.categoryCode) AS label,
               COUNT(*) AS count
        FROM books b WHERE b.isDeleted = 0
        GROUP BY b.categoryCode ORDER BY count DESC, label
        """,
    )
    suspend fun categoryCounts(): List<LabelCount>

    @Query("SELECT language AS label, COUNT(*) AS count FROM books WHERE isDeleted = 0 GROUP BY language ORDER BY count DESC, language")
    suspend fun languageCounts(): List<LabelCount>
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

    /** Highest copy number for a book's volume, so the next copy is +1. Null when none. */
    @Query("SELECT MAX(copyNumber) FROM book_copies WHERE bookId = :bookId AND volumeNumber = :volumeNumber AND isDeleted = 0")
    suspend fun maxCopyNumber(bookId: String, volumeNumber: Int): Int?

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

    /**
     * Checkout search: every matching copy with its book and live loan status, so
     * staff can find a copy by book title/author when they don't have the code.
     */
    @Query(
        """
        SELECT c.*, b.title AS bookTitle, b.author AS bookAuthor,
            EXISTS(SELECT 1 FROM loans l
                   WHERE l.copyId = c.id AND l.returnedAt IS NULL AND l.isDeleted = 0) AS onLoan
        FROM book_copies c
        JOIN books b ON b.id = c.bookId
        WHERE c.isDeleted = 0 AND b.isDeleted = 0
          AND (b.title LIKE '%' || :query || '%'
               OR b.author LIKE '%' || :query || '%'
               OR c.copyCode LIKE '%' || :query || '%')
        ORDER BY b.title, c.copyCode
        LIMIT 50
        """,
    )
    fun search(query: String): Flow<List<CopyWithBook>>

    /**
     * Return search: only copies with an active loan (the ones that can be
     * returned), matched on book title/author or copy code.
     */
    @Query(
        """
        SELECT c.*, b.title AS bookTitle, b.author AS bookAuthor, 1 AS onLoan
        FROM book_copies c
        JOIN books b ON b.id = c.bookId
        WHERE c.isDeleted = 0 AND b.isDeleted = 0
          AND EXISTS(SELECT 1 FROM loans l
                     WHERE l.copyId = c.id AND l.returnedAt IS NULL AND l.isDeleted = 0)
          AND (b.title LIKE '%' || :query || '%'
               OR b.author LIKE '%' || :query || '%'
               OR c.copyCode LIKE '%' || :query || '%')
        ORDER BY b.title, c.copyCode
        LIMIT 50
        """,
    )
    fun searchOnLoan(query: String): Flow<List<CopyWithBook>>

    @Upsert
    suspend fun upsertAll(items: List<BookCopyEntity>)

    @Query("SELECT copyCode FROM book_copies")
    suspend fun allCopyCodes(): List<String>

    @Query("SELECT COUNT(*) FROM book_copies WHERE isDeleted = 0 AND status = 'IN_SERVICE'")
    suspend fun copyCount(): Int
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

    @Upsert
    suspend fun upsertAll(items: List<MemberEntity>)

    @Query("SELECT memberCode FROM members")
    suspend fun allMemberCodes(): List<String>

    @Query("SELECT COUNT(*) FROM members WHERE isDeleted = 0")
    suspend fun memberCount(): Int

    @Query("SELECT COUNT(*) FROM members WHERE isDeleted = 0 AND joinedAt >= :start AND joinedAt < :end")
    suspend fun newMembersBetween(start: Long, end: Long): Int
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

    /**
     * Overdue loans for the dashboard. [query] blank returns all; otherwise filters
     * by book title/author, copy code, or member name/code so staff can find an
     * overdue item by the book or by who has it.
     */
    @Query(
        """
        SELECT l.*, b.title AS bookTitle, c.copyCode AS copyCode,
               m.fullName AS memberName, m.memberCode AS memberCode
        FROM loans l
        JOIN book_copies c ON c.id = l.copyId
        JOIN books b ON b.id = c.bookId
        JOIN members m ON m.id = l.memberId
        WHERE l.returnedAt IS NULL AND l.dueAt < :now AND l.isDeleted = 0
          AND (:query = ''
               OR b.title LIKE '%' || :query || '%'
               OR b.author LIKE '%' || :query || '%'
               OR c.copyCode LIKE '%' || :query || '%'
               OR m.fullName LIKE '%' || :query || '%'
               OR m.memberCode LIKE '%' || :query || '%')
        ORDER BY l.dueAt
        """,
    )
    fun overdueDetailedFiltered(now: Long, query: String): Flow<List<LoanWithDetails>>

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

    /**
     * Every book currently on loan, soonest-due first. [query] blank returns all;
     * otherwise filters by book title/author, copy code, or member name/code.
     */
    @Query(
        """
        SELECT l.*, b.title AS bookTitle, c.copyCode AS copyCode,
               m.fullName AS memberName, m.memberCode AS memberCode
        FROM loans l
        JOIN book_copies c ON c.id = l.copyId
        JOIN books b ON b.id = c.bookId
        JOIN members m ON m.id = l.memberId
        WHERE l.returnedAt IS NULL AND l.isDeleted = 0
          AND (:query = ''
               OR b.title LIKE '%' || :query || '%'
               OR b.author LIKE '%' || :query || '%'
               OR c.copyCode LIKE '%' || :query || '%'
               OR m.fullName LIKE '%' || :query || '%'
               OR m.memberCode LIKE '%' || :query || '%')
        ORDER BY l.dueAt
        """,
    )
    fun allActiveDetailed(query: String): Flow<List<LoanWithDetails>>

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

    @Upsert
    suspend fun upsertAll(items: List<LoanEntity>)

    @Query("SELECT COUNT(*) FROM loans WHERE returnedAt IS NULL AND isDeleted = 0")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM loans WHERE returnedAt IS NULL AND dueAt < :now AND isDeleted = 0")
    suspend fun countOverdue(now: Long): Int

    @Query("SELECT COUNT(*) FROM loans WHERE memberId = :memberId AND returnedAt IS NULL AND isDeleted = 0")
    suspend fun countActiveForMember(memberId: String): Int

    /** Member's average rating across all rated loans; null when none are rated. */
    @Query("SELECT AVG(rating) FROM loans WHERE memberId = :memberId AND rating IS NOT NULL AND isDeleted = 0")
    suspend fun averageRatingForMember(memberId: String): Double?

    @Query("SELECT COUNT(*) FROM loans WHERE borrowedAt >= :start AND borrowedAt < :end AND isDeleted = 0")
    suspend fun checkoutsBetween(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM loans WHERE returnedAt >= :start AND returnedAt < :end AND isDeleted = 0")
    suspend fun returnsBetween(start: Long, end: Long): Int

    @Query(
        """
        SELECT b.title AS label, COUNT(*) AS count
        FROM loans l JOIN book_copies c ON c.id = l.copyId JOIN books b ON b.id = c.bookId
        WHERE l.isDeleted = 0
        GROUP BY b.id ORDER BY count DESC, b.title LIMIT :limit
        """,
    )
    suspend fun topBooks(limit: Int): List<LabelCount>

    @Query(
        """
        SELECT m.fullName AS label, COUNT(*) AS count
        FROM loans l JOIN members m ON m.id = l.memberId
        WHERE l.isDeleted = 0
        GROUP BY m.id ORDER BY count DESC, m.fullName LIMIT :limit
        """,
    )
    suspend fun topMembers(limit: Int): List<LabelCount>

    @Query(
        """
        SELECT strftime('%Y-%m', l.borrowedAt / 1000, 'unixepoch') AS label, COUNT(*) AS count
        FROM loans l WHERE l.borrowedAt >= :since AND l.isDeleted = 0
        GROUP BY label ORDER BY label
        """,
    )
    suspend fun monthlyLoans(since: Long): List<LabelCount>

    @Query(
        """
        SELECT l.*, b.title AS bookTitle, c.copyCode AS copyCode,
               m.fullName AS memberName, m.memberCode AS memberCode
        FROM loans l
        JOIN book_copies c ON c.id = l.copyId
        JOIN books b ON b.id = c.bookId
        JOIN members m ON m.id = l.memberId
        WHERE l.returnedAt IS NULL AND l.dueAt >= :now AND l.dueAt <= :until AND l.isDeleted = 0
        ORDER BY l.dueAt
        """,
    )
    fun dueSoonDetailed(now: Long, until: Long): Flow<List<LoanWithDetails>>

    @Query(
        """
        SELECT l.*, b.title AS bookTitle, c.copyCode AS copyCode,
               m.fullName AS memberName, m.memberCode AS memberCode
        FROM loans l
        JOIN book_copies c ON c.id = l.copyId
        JOIN books b ON b.id = c.bookId
        JOIN members m ON m.id = l.memberId
        WHERE l.memberId = :memberId AND l.returnedAt IS NOT NULL AND l.isDeleted = 0
        ORDER BY l.returnedAt DESC
        """,
    )
    fun memberHistoryDetailed(memberId: String): Flow<List<LoanWithDetails>>

    @Query(
        """
        SELECT l.*, b.title AS bookTitle, c.copyCode AS copyCode,
               m.fullName AS memberName, m.memberCode AS memberCode
        FROM loans l
        JOIN book_copies c ON c.id = l.copyId
        JOIN books b ON b.id = c.bookId
        JOIN members m ON m.id = l.memberId
        WHERE c.bookId = :bookId AND l.returnedAt IS NOT NULL AND l.isDeleted = 0
        ORDER BY l.returnedAt DESC
        """,
    )
    fun bookHistoryDetailed(bookId: String): Flow<List<LoanWithDetails>>
}

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE syncedAt IS NULL ORDER BY localId")
    suspend fun pending(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE syncedAt IS NULL")
    fun pendingCount(): Flow<Int>

    /** Creation time of the oldest un-synced change; null when everything is synced. */
    @Query("SELECT MIN(createdAt) FROM sync_queue WHERE syncedAt IS NULL")
    fun oldestPendingCreatedAt(): Flow<Long?>

    @Query("UPDATE sync_queue SET syncedAt = :at WHERE localId = :id")
    suspend fun markSynced(id: Long, at: Long)

    @Query("UPDATE sync_queue SET attemptCount = attemptCount + 1 WHERE localId = :id")
    suspend fun recordAttempt(id: Long)
}

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(entry: ActivityLogEntity)

    /** Feed for the dashboard: entries since [since], newest first. */
    @Query("SELECT * FROM activity_log WHERE at >= :since ORDER BY at DESC LIMIT :limit")
    fun recent(since: Long, limit: Int): Flow<List<ActivityLogEntity>>
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
