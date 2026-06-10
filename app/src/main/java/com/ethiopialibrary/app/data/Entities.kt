package com.ethiopialibrary.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// All primary keys are client-generated UUIDs so cloud sync retries are
// idempotent upserts. Timestamps are UTC epoch millis. Rows are soft-deleted
// (isDeleted) because hard deletes cannot be replayed to the cloud mirror.

enum class CopyStatus { IN_SERVICE, LOST, DAMAGED, RETIRED }

enum class MemberStatus { ACTIVE, SUSPENDED }

/** Bibliographic record: one row per title, regardless of physical copies. */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val category: String,
    val language: String,
    val isbn: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

/** One physical copy. copyCode is the accession code printed on the label. */
@Entity(
    tableName = "book_copies",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
        ),
    ],
    indices = [
        Index("bookId"),
        Index(value = ["copyCode"], unique = true),
    ],
)
data class BookCopyEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val copyCode: String,
    val shelfLocation: String? = null,
    val status: CopyStatus = CopyStatus.IN_SERVICE,
    val acquiredAt: Long,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Entity(
    tableName = "members",
    indices = [Index(value = ["memberCode"], unique = true)],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val memberCode: String,
    val fullName: String,
    val phone: String? = null,
    val joinedAt: Long,
    val status: MemberStatus = MemberStatus.ACTIVE,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

/**
 * A borrowing transaction. A loan is active while returnedAt is null;
 * overdue is derived (returnedAt IS NULL AND dueAt < now), never stored.
 */
@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = BookCopyEntity::class,
            parentColumns = ["id"],
            childColumns = ["copyId"],
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
        ),
    ],
    indices = [
        Index("copyId"),
        Index("memberId"),
        Index(value = ["returnedAt", "dueAt"]),
    ],
)
data class LoanEntity(
    @PrimaryKey val id: String,
    val copyId: String,
    val memberId: String,
    val borrowedAt: Long,
    val dueAt: Long,
    val returnedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

/**
 * Transactional outbox: written in the same SQLite transaction as the data
 * change it records, so the sync queue can never disagree with the data.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val syncedAt: Long? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
