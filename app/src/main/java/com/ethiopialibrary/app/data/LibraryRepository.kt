package com.ethiopialibrary.app.data

import androidx.room.withTransaction
import com.ethiopialibrary.app.dates.CalendarMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

sealed interface CheckoutResult {
    data class Success(val loan: LoanEntity) : CheckoutResult
    data object CopyNotFound : CheckoutResult
    data object CopyNotAvailable : CheckoutResult
    data object MemberNotFound : CheckoutResult
    data object MemberNotActive : CheckoutResult
    data object LimitReached : CheckoutResult
}

sealed interface ReturnResult {
    data class Success(val loan: LoanEntity, val wasOverdue: Boolean) : ReturnResult
    data object NoActiveLoan : ReturnResult
    data object CopyNotFound : ReturnResult
}

sealed interface RenewResult {
    data class Success(val loan: LoanEntity) : RenewResult
    data object NotActive : RenewResult
}

/**
 * All mutations run inside a single Room transaction that also writes the
 * sync outbox entry, so a power cut can never leave data and sync queue
 * disagreeing.
 */
class LibraryRepository(
    private val db: LibraryDatabase,
    private val clock: Clock,
) {
    companion object {
        const val DEFAULT_LOAN_PERIOD_DAYS = 14
        private const val MILLIS_PER_DAY = 86_400_000L
    }

    private fun now(): Long = clock.instant().toEpochMilli()

    private fun newId(): String = UUID.randomUUID().toString()

    suspend fun addBook(
        title: String,
        author: String,
        category: String,
        language: String,
        isbn: String? = null,
        notes: String? = null,
    ): BookEntity = db.withTransaction {
        val t = now()
        val book = BookEntity(
            id = newId(),
            title = title,
            author = author,
            category = category,
            language = language,
            isbn = isbn,
            notes = notes,
            createdAt = t,
            updatedAt = t,
        )
        db.bookDao().insert(book)
        enqueueSync("book", book.id)
        book
    }

    /** Cataloging is atomic: a power cut can never leave a book with missing copies. */
    suspend fun addBookWithCopies(
        title: String,
        author: String,
        category: String,
        language: String,
        isbn: String? = null,
        notes: String? = null,
        copies: Int,
    ): BookEntity = db.withTransaction {
        val book = addBook(title, author, category, language, isbn, notes)
        repeat(copies.coerceAtLeast(1)) { addCopy(book.id) }
        book
    }

    suspend fun addCopy(bookId: String, shelfLocation: String? = null): BookCopyEntity =
        db.withTransaction {
            val t = now()
            val copy = BookCopyEntity(
                id = newId(),
                bookId = bookId,
                copyCode = "B-%04d".format(nextSequence(SettingKeys.NEXT_COPY_SEQ)),
                shelfLocation = shelfLocation,
                acquiredAt = t,
                createdAt = t,
                updatedAt = t,
            )
            db.bookCopyDao().insert(copy)
            enqueueSync("book_copy", copy.id)
            copy
        }

    suspend fun registerMember(fullName: String, phone: String? = null): MemberEntity =
        db.withTransaction {
            val t = now()
            val member = MemberEntity(
                id = newId(),
                memberCode = "M-%04d".format(nextSequence(SettingKeys.NEXT_MEMBER_SEQ)),
                fullName = fullName,
                phone = phone,
                joinedAt = t,
                createdAt = t,
                updatedAt = t,
            )
            db.memberDao().insert(member)
            enqueueSync("member", member.id)
            member
        }

    suspend fun setMemberStatus(memberId: String, status: MemberStatus) {
        db.withTransaction {
            val member = db.memberDao().byId(memberId) ?: return@withTransaction
            db.memberDao().update(member.copy(status = status, updatedAt = now()))
            enqueueSync("member", memberId)
        }
    }

    suspend fun setCopyStatus(copyId: String, status: CopyStatus) {
        db.withTransaction {
            val copy = db.bookCopyDao().byId(copyId) ?: return@withTransaction
            db.bookCopyDao().update(copy.copy(status = status, updatedAt = now()))
            enqueueSync("book_copy", copyId)
        }
    }

    suspend fun setLoanPeriodDays(days: Int) {
        db.withTransaction {
            db.settingsDao().put(SettingEntity(SettingKeys.LOAN_PERIOD_DAYS, days.toString()))
            enqueueSync("setting", SettingKeys.LOAN_PERIOD_DAYS)
        }
    }

    suspend fun checkout(copyCode: String, memberCode: String): CheckoutResult =
        db.withTransaction {
            val copy = db.bookCopyDao().byCode(copyCode)
                ?: return@withTransaction CheckoutResult.CopyNotFound
            val member = db.memberDao().byCode(memberCode)
                ?: return@withTransaction CheckoutResult.MemberNotFound
            if (member.status != MemberStatus.ACTIVE) {
                return@withTransaction CheckoutResult.MemberNotActive
            }
            if (copy.status != CopyStatus.IN_SERVICE) {
                return@withTransaction CheckoutResult.CopyNotAvailable
            }
            if (db.loanDao().activeLoanForCopy(copy.id) != null) {
                return@withTransaction CheckoutResult.CopyNotAvailable
            }
            val limit = db.settingsDao().get(SettingKeys.MAX_BOOKS_PER_MEMBER)?.toIntOrNull() ?: 3
            if (limit > 0 && db.loanDao().countActiveForMember(member.id) >= limit) {
                return@withTransaction CheckoutResult.LimitReached
            }
            val t = now()
            val periodDays = db.settingsDao().get(SettingKeys.LOAN_PERIOD_DAYS)?.toIntOrNull()
                ?: DEFAULT_LOAN_PERIOD_DAYS
            val loan = LoanEntity(
                id = newId(),
                copyId = copy.id,
                memberId = member.id,
                borrowedAt = t,
                dueAt = t + periodDays * MILLIS_PER_DAY,
                createdAt = t,
                updatedAt = t,
            )
            db.loanDao().insert(loan)
            enqueueSync("loan", loan.id)
            CheckoutResult.Success(loan)
        }

    suspend fun returnBook(copyCode: String): ReturnResult =
        db.withTransaction {
            val copy = db.bookCopyDao().byCode(copyCode)
                ?: return@withTransaction ReturnResult.CopyNotFound
            val active = db.loanDao().activeLoanForCopy(copy.id)
                ?: return@withTransaction ReturnResult.NoActiveLoan
            val t = now()
            val returned = active.copy(returnedAt = t, updatedAt = t)
            db.loanDao().update(returned)
            enqueueSync("loan", returned.id)
            ReturnResult.Success(returned, wasOverdue = t > active.dueAt)
        }

    /** "Can I keep it another week?" - extends the due date from today. */
    suspend fun renewLoan(loanId: String): RenewResult =
        db.withTransaction {
            val loan = db.loanDao().byId(loanId)
            if (loan == null || loan.returnedAt != null || loan.isDeleted) {
                return@withTransaction RenewResult.NotActive
            }
            val t = now()
            val periodDays = db.settingsDao().get(SettingKeys.LOAN_PERIOD_DAYS)?.toIntOrNull()
                ?: DEFAULT_LOAN_PERIOD_DAYS
            val renewed = loan.copy(dueAt = t + periodDays * MILLIS_PER_DAY, updatedAt = t)
            db.loanDao().update(renewed)
            enqueueSync("loan", renewed.id)
            RenewResult.Success(renewed)
        }

    suspend fun overdueLoans(): List<LoanEntity> = db.loanDao().overdue(now())

    suspend fun availableCopyCount(bookId: String): Int =
        db.bookCopyDao().availableCount(bookId)

    suspend fun pendingSyncEntries(): List<SyncQueueEntity> =
        db.syncQueueDao().pending()

    // ---------- reactive queries for the UI ----------

    fun booksWithCounts(query: String): Flow<List<BookWithCounts>> =
        db.bookDao().withCounts(query.trim())

    fun copiesForBook(bookId: String): Flow<List<CopyRow>> =
        db.bookCopyDao().copiesForBook(bookId)

    fun membersWithLoanCounts(query: String): Flow<List<MemberWithLoanCount>> =
        db.memberDao().withLoanCounts(query.trim())

    fun overdueLoansDetailed(): Flow<List<LoanWithDetails>> =
        db.loanDao().overdueDetailed(now())

    fun activeLoansForMember(memberId: String): Flow<List<LoanWithDetails>> =
        db.loanDao().activeForMemberDetailed(memberId)

    fun dashboardStats(): Flow<DashboardStats> = combine(
        db.bookDao().count(),
        db.memberDao().count(),
        db.loanDao().activeCount(),
        db.loanDao().overdueCount(now()),
    ) { books, members, active, overdue ->
        DashboardStats(
            totalBooks = books,
            totalMembers = members,
            activeLoans = active,
            overdueCount = overdue,
        )
    }

    suspend fun copyWithBook(copyCode: String): CopyWithBook? =
        db.bookCopyDao().withBook(copyCode.trim())

    suspend fun memberByCode(code: String): MemberEntity? =
        db.memberDao().byCode(code.trim())

    suspend fun activeLoanDetailedForCopy(copyCode: String): LoanWithDetails? =
        db.loanDao().activeForCopyDetailed(copyCode.trim())

    suspend fun updateBook(book: BookEntity) {
        db.withTransaction {
            db.bookDao().update(book.copy(updatedAt = now()))
            enqueueSync("book", book.id)
        }
    }

    suspend fun updateMember(member: MemberEntity) {
        db.withTransaction {
            db.memberDao().update(member.copy(updatedAt = now()))
            enqueueSync("member", member.id)
        }
    }

    suspend fun loanPeriodDays(): Int =
        db.settingsDao().get(SettingKeys.LOAN_PERIOD_DAYS)?.toIntOrNull() ?: DEFAULT_LOAN_PERIOD_DAYS

    fun pendingSyncCount(): Flow<Int> = db.syncQueueDao().pendingCount()

    fun lastSyncAt(): Flow<Long?> =
        db.settingsDao().watch(SettingKeys.LAST_SYNC_AT).map { it?.toLongOrNull() }

    // ---------- borrowing limit, due-soon, history, statistics ----------

    suspend fun maxBooksPerMember(): Int =
        db.settingsDao().get(SettingKeys.MAX_BOOKS_PER_MEMBER)?.toIntOrNull() ?: 3

    suspend fun setMaxBooksPerMember(value: Int) {
        db.withTransaction {
            db.settingsDao().put(SettingEntity(SettingKeys.MAX_BOOKS_PER_MEMBER, value.toString()))
            enqueueSync("setting", SettingKeys.MAX_BOOKS_PER_MEMBER)
        }
    }

    suspend fun dueSoonDays(): Int =
        db.settingsDao().get(SettingKeys.DUE_SOON_DAYS)?.toIntOrNull() ?: 3

    suspend fun setDueSoonDays(value: Int) {
        db.withTransaction {
            db.settingsDao().put(SettingEntity(SettingKeys.DUE_SOON_DAYS, value.toString()))
            enqueueSync("setting", SettingKeys.DUE_SOON_DAYS)
        }
    }

    // Display preference: which calendar(s) dates are shown in. Defaults to DUAL.
    suspend fun calendarMode(): CalendarMode =
        parseCalendarMode(db.settingsDao().get(SettingKeys.CALENDAR_MODE))

    fun calendarModeFlow(): Flow<CalendarMode> =
        db.settingsDao().watch(SettingKeys.CALENDAR_MODE).map(::parseCalendarMode)

    suspend fun setCalendarMode(mode: CalendarMode) {
        db.withTransaction {
            db.settingsDao().put(SettingEntity(SettingKeys.CALENDAR_MODE, mode.name))
            enqueueSync("setting", SettingKeys.CALENDAR_MODE)
        }
    }

    private fun parseCalendarMode(value: String?): CalendarMode =
        value?.let { runCatching { CalendarMode.valueOf(it) }.getOrNull() } ?: CalendarMode.DUAL

    /** Active loans falling due within the configured window (not yet overdue). */
    suspend fun dueSoonLoans(): Flow<List<LoanWithDetails>> {
        val start = now()
        val until = start + dueSoonDays() * MILLIS_PER_DAY
        return db.loanDao().dueSoonDetailed(start, until)
    }

    fun memberHistory(memberId: String): Flow<List<LoanWithDetails>> =
        db.loanDao().memberHistoryDetailed(memberId)

    fun bookHistory(bookId: String): Flow<List<LoanWithDetails>> =
        db.loanDao().bookHistoryDetailed(bookId)

    suspend fun computeStatistics(): LibraryStatistics {
        val now = now()
        val d30 = 30L * MILLIS_PER_DAY
        return LibraryStatistics(
            totalTitles = db.bookDao().titleCount(),
            totalCopies = db.bookCopyDao().copyCount(),
            totalMembers = db.memberDao().memberCount(),
            activeLoans = db.loanDao().countActive(),
            overdue = db.loanDao().countOverdue(now),
            checkoutsLast30 = db.loanDao().checkoutsBetween(now - d30, now + 1),
            checkoutsPrev30 = db.loanDao().checkoutsBetween(now - 2 * d30, now - d30),
            returnsLast30 = db.loanDao().returnsBetween(now - d30, now + 1),
            newMembersLast30 = db.memberDao().newMembersBetween(now - d30, now + 1),
            topBooks = db.loanDao().topBooks(5),
            topMembers = db.loanDao().topMembers(5),
            byCategory = db.bookDao().categoryCounts(),
            byLanguage = db.bookDao().languageCounts(),
            monthlyLoans = db.loanDao().monthlyLoans(now - 6L * d30),
        )
    }

    // ---------- staff PIN (guards destructive settings) ----------

    suspend fun setStaffPin(pin: String) {
        db.settingsDao().put(SettingEntity(SettingKeys.STAFF_PIN_HASH, hashPin(pin)))
    }

    suspend fun verifyStaffPin(pin: String): Boolean =
        db.settingsDao().get(SettingKeys.STAFF_PIN_HASH) == hashPin(pin)

    suspend fun hasStaffPin(): Boolean =
        db.settingsDao().get(SettingKeys.STAFF_PIN_HASH) != null

    private fun hashPin(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("ethiopialibrary:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun bookById(id: String): BookEntity? = db.bookDao().byId(id)

    suspend fun memberById(id: String): MemberEntity? = db.memberDao().byId(id)

    suspend fun copyLabelRows(): List<LabelRow> = db.bookCopyDao().labelRows()

    suspend fun memberLabelRows(): List<LabelRow> = db.memberDao().labelRows()

    /** Must be called inside a transaction: read-use-increment is not atomic on its own. */
    private suspend fun nextSequence(key: String): Int {
        val current = db.settingsDao().get(key)?.toIntOrNull() ?: 1
        db.settingsDao().put(SettingEntity(key, (current + 1).toString()))
        return current
    }

    private suspend fun enqueueSync(entityType: String, entityId: String) {
        db.syncQueueDao().insert(
            SyncQueueEntity(
                entityType = entityType,
                entityId = entityId,
                operation = "UPSERT",
                createdAt = now(),
            ),
        )
    }
}
