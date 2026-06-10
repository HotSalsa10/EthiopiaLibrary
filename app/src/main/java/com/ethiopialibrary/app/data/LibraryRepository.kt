package com.ethiopialibrary.app.data

import androidx.room.withTransaction
import java.time.Clock
import java.util.UUID

sealed interface CheckoutResult {
    data class Success(val loan: LoanEntity) : CheckoutResult
    data object CopyNotFound : CheckoutResult
    data object CopyNotAvailable : CheckoutResult
    data object MemberNotFound : CheckoutResult
    data object MemberNotActive : CheckoutResult
}

sealed interface ReturnResult {
    data class Success(val loan: LoanEntity, val wasOverdue: Boolean) : ReturnResult
    data object NoActiveLoan : ReturnResult
    data object CopyNotFound : ReturnResult
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
        private const val KEY_LOAN_PERIOD = "loan_period_days"
        private const val KEY_NEXT_COPY_SEQ = "next_copy_seq"
        private const val KEY_NEXT_MEMBER_SEQ = "next_member_seq"
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

    suspend fun addCopy(bookId: String, shelfLocation: String? = null): BookCopyEntity =
        db.withTransaction {
            val t = now()
            val copy = BookCopyEntity(
                id = newId(),
                bookId = bookId,
                copyCode = "B-%04d".format(nextSequence(KEY_NEXT_COPY_SEQ)),
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
                memberCode = "M-%04d".format(nextSequence(KEY_NEXT_MEMBER_SEQ)),
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
        db.settingsDao().put(SettingEntity(KEY_LOAN_PERIOD, days.toString()))
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
            val t = now()
            val periodDays = db.settingsDao().get(KEY_LOAN_PERIOD)?.toIntOrNull()
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

    suspend fun overdueLoans(): List<LoanEntity> = db.loanDao().overdue(now())

    suspend fun availableCopyCount(bookId: String): Int =
        db.bookCopyDao().availableCount(bookId)

    suspend fun pendingSyncEntries(): List<SyncQueueEntity> =
        db.syncQueueDao().pending()

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
