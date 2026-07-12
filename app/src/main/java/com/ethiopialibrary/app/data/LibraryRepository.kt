package com.ethiopialibrary.app.data

import androidx.room.withTransaction
import com.ethiopialibrary.app.dates.CalendarMode
import com.ethiopialibrary.app.sync.RemoteDirectives
import com.ethiopialibrary.app.sync.remoteDirectivesFromSettings
import com.ethiopialibrary.app.util.clockLooksWrong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

sealed interface CheckoutResult {
    data class Success(val loan: LoanEntity) : CheckoutResult
    data object CopyNotFound : CheckoutResult
    data object CopyNotAvailable : CheckoutResult
    data object MemberNotFound : CheckoutResult
    data object MemberNotActive : CheckoutResult
    data object LimitReached : CheckoutResult
    data object ClockWrong : CheckoutResult
}

sealed interface ReturnResult {
    data class Success(val loan: LoanEntity, val wasOverdue: Boolean) : ReturnResult
    data object NoActiveLoan : ReturnResult
    data object CopyNotFound : ReturnResult
}

sealed interface RenewResult {
    data class Success(val loan: LoanEntity) : RenewResult
    data object NotActive : RenewResult
    data object ClockWrong : RenewResult
}

sealed interface AddCategoryResult {
    data class Success(val category: CategoryEntity) : AddCategoryResult
    data object DuplicateCode : AddCategoryResult
}

/**
 * All mutations run inside a single Room transaction that also writes the
 * sync outbox entry, so a power cut can never leave data and sync queue
 * disagreeing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepository(
    private val db: LibraryDatabase,
    private val clock: Clock,
    /** How often time-sensitive Flows (overdue/due-soon) re-bind "now" - overridable in tests. */
    private val tickMillis: Long = 60_000L,
    /**
     * This build's own compile time (see [com.ethiopialibrary.app.util.clockLooksWrong]);
     * defaults to 0 (clock can never look wrong) so the ~20+ existing call
     * sites that construct a repository with a fixed test clock aren't
     * affected - only production (BuildConfig.BUILD_TIME_MS) and tests that
     * opt into exercising the gate pass a real value.
     */
    private val buildTimeMillis: Long = 0L,
) {
    companion object {
        const val DEFAULT_LOAN_PERIOD_DAYS = 14
        const val MAX_LOAN_PERIOD_DAYS = 365
        const val MAX_BOOKS_PER_MEMBER_CEILING = 50
        const val MAX_DUE_SOON_DAYS = 60
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val RECENT_ACTIVITY_LIMIT = 10

        /** Exported share-sheet backups kept on device before rotation. */
        private const val EXPORT_KEEP_COUNT = 3

        private val REMOTE_SETTING_KEYS = listOf(
            SettingKeys.REMOTE_ANNOUNCEMENT_AM,
            SettingKeys.REMOTE_ANNOUNCEMENT_AR,
            SettingKeys.REMOTE_ANNOUNCEMENT_EN,
            SettingKeys.REMOTE_ANNOUNCEMENT_ID,
            SettingKeys.REMOTE_UPDATE_MANIFEST_URL,
            SettingKeys.REMOTE_UPDATE_CHECK_ENABLED,
            SettingKeys.REMOTE_DEBOUNCED_BACKUP_ENABLED,
            SettingKeys.REMOTE_MIN_SUPPORTED_VERSION_CODE,
        )
    }

    private fun now(): Long = clock.instant().toEpochMilli()

    private fun isClockWrong(): Boolean = clockLooksWrong(now(), buildTimeMillis)

    /** Live: re-evaluates every [tickMillis] so fixing the date without an app restart clears the Dashboard banner. */
    fun clockWrong(): Flow<Boolean> = tick().map { nowMs -> clockLooksWrong(nowMs, buildTimeMillis) }

    /** Midnight in the clock's own zone - the boundary for "today" in the activity feed. */
    private fun startOfToday(): Long =
        clock.instant().atZone(clock.zone).toLocalDate().atStartOfDay(clock.zone).toInstant().toEpochMilli()

    /**
     * Re-emits the current time every [tickMillis], so Flows built from it (overdue
     * counts/lists, due-soon, today's activity feed) stay accurate on a tablet that's
     * left running for days - a loan becoming overdue is a pure clock event, not a DB
     * write, so nothing would otherwise invalidate a Flow whose SQL already has an
     * old "now" bound into it.
     */
    private fun tick(): Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(tickMillis)
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    suspend fun addBook(
        title: String,
        author: String,
        categoryCode: String,
        language: String,
        isbn: String? = null,
        notes: String? = null,
    ): BookEntity = db.withTransaction {
        val t = now()
        // Book number is the next free one within this category.
        val bookNumber = (db.bookDao().maxBookNumber(categoryCode) ?: 0) + 1
        val book = BookEntity(
            id = newId(),
            title = title,
            author = author,
            categoryCode = categoryCode,
            bookNumber = bookNumber,
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
        categoryCode: String,
        language: String,
        isbn: String? = null,
        notes: String? = null,
        copies: Int,
    ): BookEntity = db.withTransaction {
        val book = addBook(title, author, categoryCode, language, isbn, notes)
        repeat(copies.coerceAtLeast(1)) { addCopy(book.id) }
        book
    }

    /**
     * Adds one physical copy. The copy number auto-increments per book+volume,
     * so two copies never share a code; volume is 0 for a single-volume work.
     */
    suspend fun addCopy(
        bookId: String,
        volumeNumber: Int = 0,
        shelfLocation: String? = null,
    ): BookCopyEntity = db.withTransaction {
        val t = now()
        val book = db.bookDao().byId(bookId) ?: error("addCopy: book $bookId not found")
        val copyNumber = (db.bookCopyDao().maxCopyNumber(bookId, volumeNumber) ?: 0) + 1
        val copy = BookCopyEntity(
            id = newId(),
            bookId = bookId,
            copyCode = BookCode.render(book.categoryCode, book.bookNumber, copyNumber, volumeNumber),
            copyNumber = copyNumber,
            volumeNumber = volumeNumber,
            shelfLocation = shelfLocation,
            acquiredAt = t,
            createdAt = t,
            updatedAt = t,
        )
        db.bookCopyDao().insert(copy)
        enqueueSync("book_copy", copy.id)
        copy
    }

    // ---------- categories ----------

    fun categories(): Flow<List<CategoryEntity>> = db.categoryDao().all()

    suspend fun categoryByCode(code: String): CategoryEntity? = db.categoryDao().byCode(code.trim())

    /** Adds a category; [code] must be unique (its books are prefixed with it). */
    suspend fun addCategory(name: String, code: String): AddCategoryResult = db.withTransaction {
        val normalizedCode = code.trim().uppercase()
        if (db.categoryDao().byCode(normalizedCode) != null) {
            return@withTransaction AddCategoryResult.DuplicateCode
        }
        val t = now()
        val category = CategoryEntity(
            id = newId(),
            code = normalizedCode,
            name = name.trim(),
            sortOrder = (db.categoryDao().maxSortOrder() ?: 0) + 1,
            createdAt = t,
            updatedAt = t,
        )
        db.categoryDao().insert(category)
        enqueueSync("category", category.id)
        AddCategoryResult.Success(category)
    }

    suspend fun registerMember(
        fullName: String,
        phone: String? = null,
        nationalId: String? = null,
        address: String? = null,
    ): MemberEntity =
        db.withTransaction {
            val t = now()
            val member = MemberEntity(
                id = newId(),
                // Locale.ROOT: this code is printed, scanned, and re-parsed as a
                // number by restore's sequence recompute - it must never render
                // with non-ASCII digits under the Arabic UI locale.
                memberCode = "M-%04d".format(Locale.ROOT, nextSequence(SettingKeys.NEXT_MEMBER_SEQ)),
                fullName = fullName,
                phone = phone,
                nationalId = nationalId,
                address = address,
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
            val clamped = days.coerceIn(1, MAX_LOAN_PERIOD_DAYS)
            db.settingsDao().put(SettingEntity(SettingKeys.LOAN_PERIOD_DAYS, clamped.toString()))
            enqueueSync("setting", SettingKeys.LOAN_PERIOD_DAYS)
        }
    }

    /**
     * [periodDays] lets staff override the loan length for this one checkout;
     * null (or non-positive) falls back to the configured setting / default.
     * [allowOverLimit] bypasses the borrowing-limit check (staff-PIN-gated in the UI).
     */
    suspend fun checkout(
        copyCode: String,
        memberCode: String,
        periodDays: Int? = null,
        allowOverLimit: Boolean = false,
    ): CheckoutResult =
        db.withTransaction {
            if (isClockWrong()) return@withTransaction CheckoutResult.ClockWrong
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
            if (limit > 0 && !allowOverLimit && db.loanDao().countActiveForMember(member.id) >= limit) {
                return@withTransaction CheckoutResult.LimitReached
            }
            val t = now()
            val effectivePeriod = (
                periodDays?.takeIf { it > 0 }
                    ?: db.settingsDao().get(SettingKeys.LOAN_PERIOD_DAYS)?.toIntOrNull()
                    ?: DEFAULT_LOAN_PERIOD_DAYS
                ).coerceIn(1, MAX_LOAN_PERIOD_DAYS)
            val loan = LoanEntity(
                id = newId(),
                copyId = copy.id,
                memberId = member.id,
                borrowedAt = t,
                dueAt = t + effectivePeriod * MILLIS_PER_DAY,
                createdAt = t,
                updatedAt = t,
            )
            db.loanDao().insert(loan)
            enqueueSync("loan", loan.id)
            db.activityLogDao().insert(ActivityLogEntity(id = newId(), type = ActivityType.CHECKOUT.name, loanId = loan.id, at = t))
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
            db.activityLogDao().insert(ActivityLogEntity(id = newId(), type = ActivityType.RETURN.name, loanId = returned.id, at = t))
            ReturnResult.Success(returned, wasOverdue = t > active.dueAt)
        }

    /**
     * Records the staff's 1–5 rating of how a member handled a loan (set at the
     * return desk; the step is skippable, so this is only called when they pick).
     */
    suspend fun rateLoan(loanId: String, rating: Int) {
        require(rating in 1..5) { "rating must be 1..5, was $rating" }
        db.withTransaction {
            val loan = db.loanDao().byId(loanId) ?: return@withTransaction
            db.loanDao().update(loan.copy(rating = rating, updatedAt = now()))
            enqueueSync("loan", loanId)
        }
    }

    suspend fun memberAverageRating(memberId: String): Double? =
        db.loanDao().averageRatingForMember(memberId)

    /**
     * "Can I keep it another week?" - extends the due date from today. Never
     * shortens it: a loan checked out with a longer-than-default period keeps
     * its later due date if the global setting would compute a nearer one.
     */
    suspend fun renewLoan(loanId: String): RenewResult =
        db.withTransaction {
            if (isClockWrong()) return@withTransaction RenewResult.ClockWrong
            val loan = db.loanDao().byId(loanId)
            if (loan == null || loan.returnedAt != null || loan.isDeleted) {
                return@withTransaction RenewResult.NotActive
            }
            val t = now()
            val periodDays = db.settingsDao().get(SettingKeys.LOAN_PERIOD_DAYS)?.toIntOrNull()
                ?: DEFAULT_LOAN_PERIOD_DAYS
            val renewed = loan.copy(dueAt = maxOf(loan.dueAt, t + periodDays * MILLIS_PER_DAY), updatedAt = t)
            db.loanDao().update(renewed)
            enqueueSync("loan", renewed.id)
            db.activityLogDao().insert(
                ActivityLogEntity(id = newId(), type = ActivityType.RENEW.name, loanId = renewed.id, at = t, prevDueAt = loan.dueAt),
            )
            RenewResult.Success(renewed)
        }

    suspend fun overdueLoans(): List<LoanEntity> = db.loanDao().overdue(now())

    /** Preflight check for the checkout member step: how many books this member already has overdue. */
    suspend fun overdueCountForMember(memberId: String): Int =
        db.loanDao().countOverdueForMember(memberId, now())

    suspend fun availableCopyCount(bookId: String): Int =
        db.bookCopyDao().availableCount(bookId)

    suspend fun pendingSyncEntries(): List<SyncQueueEntity> =
        db.syncQueueDao().pending()

    // ---------- reactive queries for the UI ----------

    fun booksWithCounts(query: String, categoryCode: String = ""): Flow<List<BookWithCounts>> =
        db.bookDao().withCounts(query.trim(), categoryCode)

    fun copiesForBook(bookId: String): Flow<List<CopyRow>> =
        db.bookCopyDao().copiesForBook(bookId)

    fun membersWithLoanCounts(query: String): Flow<List<MemberWithLoanCount>> =
        db.memberDao().withLoanCounts(query.trim())

    /** Overdue loans for the dashboard; [query] filters by book/member/code (blank = all). */
    fun overdueLoansDetailed(query: String = ""): Flow<List<LoanWithDetails>> =
        tick().flatMapLatest { nowMs -> db.loanDao().overdueDetailedFiltered(nowMs, query.trim()) }

    fun activeLoansForMember(memberId: String): Flow<List<LoanWithDetails>> =
        db.loanDao().activeForMemberDetailed(memberId)

    /** Everything currently on loan; [query] filters by book/member/code (blank = all). */
    fun currentlyOutLoans(query: String = ""): Flow<List<LoanWithDetails>> =
        db.loanDao().allActiveDetailed(query.trim())

    fun dashboardStats(): Flow<DashboardStats> = tick().flatMapLatest { nowMs ->
        combine(
            db.bookDao().count(),
            db.memberDao().count(),
            db.loanDao().activeCount(),
            db.loanDao().overdueCount(nowMs),
        ) { books, members, active, overdue ->
            DashboardStats(
                totalBooks = books,
                totalMembers = members,
                activeLoans = active,
                overdueCount = overdue,
            )
        }
    }

    suspend fun copyWithBook(copyCode: String): CopyWithBook? =
        db.bookCopyDao().withBook(copyCode.trim())

    /** Checkout search by book title/author/copy code; all matches with loan status. */
    fun searchCopies(query: String): Flow<List<CopyWithBook>> =
        db.bookCopyDao().search(query.trim())

    /** Return search by book title/author/copy code; only copies currently on loan. */
    fun searchOnLoanCopies(query: String): Flow<List<CopyWithBook>> =
        db.bookCopyDao().searchOnLoan(query.trim())

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

    /**
     * The due date renewing [loanId] would actually set, for a confirm preview -
     * mirrors renewLoan()'s never-shorten rule so the preview never lies.
     */
    suspend fun renewalPreviewDueAt(loanId: String): Long {
        val computed = now() + loanPeriodDays() * MILLIS_PER_DAY
        val currentDueAt = db.loanDao().byId(loanId)?.dueAt
        return if (currentDueAt != null) maxOf(currentDueAt, computed) else computed
    }

    fun pendingSyncCount(): Flow<Int> = db.syncQueueDao().pendingCount()

    fun lastSyncAt(): Flow<Long?> =
        db.settingsDao().watch(SettingKeys.LAST_SYNC_AT).map { it?.toLongOrNull() }

    /** Outcome of the most recent backup or restore: "ok", "error:<type>", or null if never run. */
    fun lastSyncResult(): Flow<String?> =
        db.settingsDao().watch(SettingKeys.LAST_SYNC_RESULT)

    /** When the oldest un-backed-up change was made; null when everything is synced. */
    fun oldestPendingSince(): Flow<Long?> =
        db.syncQueueDao().oldestPendingCreatedAt()

    /** Gentle once-per-day backup suggestion; the UI adds the connectivity condition. */
    fun backupNudgeWanted(): Flow<Boolean> = combine(
        pendingSyncCount(),
        lastSyncAt(),
        db.settingsDao().watch(SettingKeys.BACKUP_NUDGE_DISMISSED_DAY),
    ) { pending, last, dismissed ->
        shouldNudgeBackup(pending, last, dismissed?.toLongOrNull(), now())
    }

    /** Local-only quiet switch; deliberately never synced to the cloud. */
    suspend fun dismissBackupNudgeForToday() {
        db.settingsDao().put(
            SettingEntity(SettingKeys.BACKUP_NUDGE_DISMISSED_DAY, epochDay(now()).toString()),
        )
    }

    /** Config-from-cloud cache, refreshed by [com.ethiopialibrary.app.sync.SyncEngine] after every successful drain. */
    fun remoteDirectives(): Flow<RemoteDirectives> = combine(
        REMOTE_SETTING_KEYS.map { db.settingsDao().watch(it) },
    ) { values -> remoteDirectivesFromSettings(REMOTE_SETTING_KEYS.zip(values.toList()).toMap()) }

    fun dismissedAnnouncementId(): Flow<String?> =
        db.settingsDao().watch(SettingKeys.REMOTE_ANNOUNCEMENT_DISMISSED_ID)

    /** Local-only; the dismissal itself is never synced to the cloud. */
    suspend fun dismissAnnouncement(id: String) {
        db.settingsDao().put(SettingEntity(SettingKeys.REMOTE_ANNOUNCEMENT_DISMISSED_ID, id))
    }

    /**
     * Off-device insurance: checkpoint-copies the live database into [dir]
     * as a timestamped file the operator can share to Drive/WhatsApp/USB.
     * Older exports rotate away so repeated use can't fill the tablet.
     */
    suspend fun createBackupFile(dir: File): File = withContext(Dispatchers.IO) {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(clock.instant())
        val out = File(dir, "library-backup-$stamp.db")
        SnapshotManager(db).createSnapshot(out)
        dir.listFiles { f -> f.name.startsWith("library-backup-") && f.name.endsWith(".db") }
            .orEmpty()
            .sortedByDescending { it.name }
            .drop(EXPORT_KEEP_COUNT)
            .forEach { it.delete() }
        out
    }

    // ---------- borrowing limit, due-soon, history, statistics ----------

    suspend fun maxBooksPerMember(): Int =
        db.settingsDao().get(SettingKeys.MAX_BOOKS_PER_MEMBER)?.toIntOrNull() ?: 3

    suspend fun setMaxBooksPerMember(value: Int) {
        db.withTransaction {
            val clamped = value.coerceIn(0, MAX_BOOKS_PER_MEMBER_CEILING)
            db.settingsDao().put(SettingEntity(SettingKeys.MAX_BOOKS_PER_MEMBER, clamped.toString()))
            enqueueSync("setting", SettingKeys.MAX_BOOKS_PER_MEMBER)
        }
    }

    suspend fun dueSoonDays(): Int =
        db.settingsDao().get(SettingKeys.DUE_SOON_DAYS)?.toIntOrNull() ?: 3

    suspend fun setDueSoonDays(value: Int) {
        db.withTransaction {
            val clamped = value.coerceIn(1, MAX_DUE_SOON_DAYS)
            db.settingsDao().put(SettingEntity(SettingKeys.DUE_SOON_DAYS, clamped.toString()))
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
    fun dueSoonLoans(): Flow<List<LoanWithDetails>> = tick().flatMapLatest { start ->
        val until = start + dueSoonDays() * MILLIS_PER_DAY
        db.loanDao().dueSoonDetailed(start, until)
    }

    /** Dashboard feed: today's desk activity, newest first, capped to the last few. */
    fun recentActivity(): Flow<List<ActivityWithDetails>> = tick().flatMapLatest {
        db.activityLogDao().recentDetailed(startOfToday(), RECENT_ACTIVITY_LIMIT)
    }

    /**
     * Reverts a CHECKOUT/RETURN/RENEW entry (checkout -> soft-delete the loan; return ->
     * un-return it; renew -> restore the pre-renewal due date), re-syncs the loan, and logs
     * the undo as its own entry. Returns false if the entry/loan is gone, isn't undoable
     * (e.g. it's already an UNDO entry - undoing an undo isn't supported), has already
     * been undone (a repeat click on the same row is rejected, not re-applied), is shadowed
     * by a later action on the same loan (only the most recent action is undoable), or the
     * loan's current state no longer matches what the entry assumes (e.g. undoing a RETURN
     * after the copy was re-checked-out under a different loan would otherwise leave two
     * active loans on one copy).
     */
    suspend fun undoActivity(activityId: String): Boolean =
        db.withTransaction {
            val entry = db.activityLogDao().byId(activityId) ?: return@withTransaction false
            if (entry.undoneAt != null) return@withTransaction false // already undone - reject the repeat click
            val loan = db.loanDao().byId(entry.loanId) ?: return@withTransaction false
            if (db.activityLogDao().countNewerForLoan(entry.loanId, entry.at) > 0) {
                return@withTransaction false // a later action on this loan shadows this entry
            }
            val t = now()
            when (entry.type) {
                ActivityType.CHECKOUT.name -> {
                    if (loan.returnedAt != null || loan.isDeleted) return@withTransaction false
                    db.loanDao().update(loan.copy(isDeleted = true, updatedAt = t))
                }
                ActivityType.RETURN.name -> {
                    if (loan.returnedAt == null) return@withTransaction false
                    // A different loan record may have re-claimed this copy since
                    // the return; un-returning would then create two active loans.
                    if (db.loanDao().activeLoanForCopy(loan.copyId) != null) return@withTransaction false
                    db.loanDao().update(loan.copy(returnedAt = null, updatedAt = t))
                }
                ActivityType.RENEW.name -> {
                    if (loan.returnedAt != null || loan.isDeleted) return@withTransaction false
                    db.loanDao().update(loan.copy(dueAt = entry.prevDueAt ?: loan.dueAt, updatedAt = t))
                }
                else -> return@withTransaction false
            }
            enqueueSync("loan", loan.id)
            db.activityLogDao().markUndone(entry.id, t)
            db.activityLogDao().insert(ActivityLogEntity(id = newId(), type = ActivityType.UNDO.name, loanId = loan.id, at = t))
            true
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
        db.withTransaction {
            db.settingsDao().put(SettingEntity(SettingKeys.STAFF_PIN_HASH, hashPin(pin)))
            enqueueSync("setting", SettingKeys.STAFF_PIN_HASH)
        }
    }

    suspend fun verifyStaffPin(pin: String): Boolean =
        db.settingsDao().get(SettingKeys.STAFF_PIN_HASH) == hashPin(pin)

    suspend fun hasStaffPin(): Boolean =
        db.settingsDao().get(SettingKeys.STAFF_PIN_HASH) != null

    private fun hashPin(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("ethiopialibrary:$pin".toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

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
