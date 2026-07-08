package com.ethiopialibrary.app.data

import androidx.room.Embedded

/** Book row for lists: counts are derived, never stored, so they can't drift. */
data class BookWithCounts(
    @Embedded val book: BookEntity,
    val categoryName: String?,
    val totalCopies: Int,
    val availableCopies: Int,
)

data class MemberWithLoanCount(
    @Embedded val member: MemberEntity,
    val activeLoans: Int,
)

/** Loan joined with everything staff need to read it aloud. */
data class LoanWithDetails(
    @Embedded val loan: LoanEntity,
    val bookTitle: String,
    val copyCode: String,
    val memberName: String,
    val memberCode: String,
)

data class CopyRow(
    @Embedded val copy: BookCopyEntity,
    val onLoan: Boolean,
)

/** Checkout-preview lookup: what staff see after scanning a copy label. */
data class CopyWithBook(
    @Embedded val copy: BookCopyEntity,
    val bookTitle: String,
    val bookAuthor: String,
    val onLoan: Boolean,
)

/** One activity_log row joined with what staff need to read it aloud, for the dashboard feed. */
data class ActivityWithDetails(
    @Embedded val entry: ActivityLogEntity,
    val bookTitle: String,
    val copyCode: String,
    val memberName: String,
    val memberCode: String,
)

data class DashboardStats(
    val totalBooks: Int,
    val totalMembers: Int,
    val activeLoans: Int,
    val overdueCount: Int,
)

/** One printable label: the QR/manual code plus the line printed under it. */
data class LabelRow(
    val code: String,
    val title: String,
)
