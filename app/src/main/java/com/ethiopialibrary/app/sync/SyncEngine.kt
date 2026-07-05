package com.ethiopialibrary.app.sync

import androidx.room.withTransaction
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.SettingEntity
import com.ethiopialibrary.app.data.SettingKeys
import com.ethiopialibrary.app.data.SyncQueueEntity
import java.time.Clock

/**
 * One-way backup engine (single-tablet deployment): drains the transactional
 * outbox to the cloud mirror, and can rebuild an empty tablet from it.
 */
class SyncEngine(
    private val db: LibraryDatabase,
    private val cloud: CloudStore,
    private val clock: Clock,
    private val batchSize: Int = FIRESTORE_BATCH_LIMIT,
) {
    companion object {
        /** [SettingKeys.LAST_SYNC_RESULT] value after a successful backup or restore. */
        const val RESULT_OK = "ok"

        /** Firestore allows at most 500 writes per atomic batch commit. */
        const val FIRESTORE_BATCH_LIMIT = 500
    }

    /**
     * Uploads pending outbox entries in write order as atomic batches
     * (fewer round-trips on flaky internet, all-or-nothing per chunk).
     * Stops at the first failed chunk (WorkManager retries later with
     * backoff); committed chunks stay marked and never re-upload.
     */
    suspend fun drainOutbox(): SyncResult {
        val pending = db.syncQueueDao().pending()
        var uploaded = 0
        for (chunk in pending.chunked(batchSize)) {
            val items = chunk.mapNotNull { entry ->
                buildPayload(entry)?.let { (collection, payload) ->
                    CloudUpsert(collection, entry.entityId, payload)
                }
            }
            try {
                if (items.isNotEmpty()) cloud.upsertBatch(items)
            } catch (e: Exception) {
                chunk.forEach { db.syncQueueDao().recordAttempt(it.localId) }
                recordResult("error:${e.javaClass.simpleName}")
                return SyncResult.Failure(uploaded, e.message)
            }
            val t = now()
            chunk.forEach { db.syncQueueDao().markSynced(it.localId, t) }
            uploaded += chunk.size
        }
        db.settingsDao().put(SettingEntity(SettingKeys.LAST_SYNC_AT, now().toString()))
        recordResult(RESULT_OK)
        return SyncResult.Success(uploaded)
    }

    /** Current entity state at upload time: replaying old outbox rows is harmless. */
    private suspend fun buildPayload(entry: SyncQueueEntity): Pair<String, Map<String, Any?>>? =
        when (entry.entityType) {
            "category" -> db.categoryDao().byId(entry.entityId)?.let { "categories" to it.toMap() }
            "book" -> db.bookDao().byId(entry.entityId)?.let { "books" to it.toMap() }
            "book_copy" -> db.bookCopyDao().byId(entry.entityId)?.let { "book_copies" to it.toMap() }
            "member" -> db.memberDao().byId(entry.entityId)?.let { "members" to it.toMap() }
            "loan" -> db.loanDao().byId(entry.entityId)?.let { "loans" to it.toMap() }
            "setting" -> db.settingsDao().get(entry.entityId)?.let { "config" to mapOf<String, Any?>("value" to it) }
            else -> null
        }

    /**
     * Disaster recovery onto a fresh tablet: pulls every collection, writes
     * them in one local transaction, then recomputes the B-/M- code
     * sequences from the restored data so new codes can never collide.
     */
    suspend fun restore(): Int {
        val categories = cloud.fetchAll("categories").mapDocs("categories", ::categoryFrom)
        val books = cloud.fetchAll("books").mapDocs("books", ::bookFrom)
        val copies = cloud.fetchAll("book_copies").mapDocs("book_copies", ::copyFrom)
        val members = cloud.fetchAll("members").mapDocs("members", ::memberFrom)
        val loans = cloud.fetchAll("loans").mapDocs("loans", ::loanFrom)
        val config = cloud.fetchAll("config")

        // Drop referentially-orphaned children whose parent document was
        // malformed-and-skipped (or never reached the cloud), so a foreign-key
        // violation can never abort the whole restore.
        val bookIds = books.mapTo(HashSet()) { it.id }
        val validCopies = copies.filter { it.bookId in bookIds }
        val copyIds = validCopies.mapTo(HashSet()) { it.id }
        val memberIds = members.mapTo(HashSet()) { it.id }
        val validLoans = loans.filter { it.copyId in copyIds && it.memberId in memberIds }

        db.withTransaction {
            db.categoryDao().upsertAll(categories)
            db.bookDao().upsertAll(books)
            db.bookCopyDao().upsertAll(validCopies)
            db.memberDao().upsertAll(members)
            db.loanDao().upsertAll(validLoans)
            config.forEach { (key, data) ->
                (data["value"] as? String)?.let { db.settingsDao().put(SettingEntity(key, it)) }
            }
            recomputeSequences()
            // A freshly restored tablet IS in sync with the cloud - stamp the
            // status so the dashboard doesn't claim "never backed up".
            db.settingsDao().put(SettingEntity(SettingKeys.LAST_SYNC_AT, now().toString()))
            recordResult(RESULT_OK)
        }
        return categories.size + books.size + validCopies.size + members.size + validLoans.size
    }

    private suspend fun recordResult(value: String) {
        db.settingsDao().put(SettingEntity(SettingKeys.LAST_SYNC_RESULT, value))
    }

    // Book and copy numbers derive from the data itself (max + 1), so only the
    // member code counter needs rebuilding after a restore.
    private suspend fun recomputeSequences() {
        val nextMember = (db.memberDao().allMemberCodes().mapNotNull(::codeNumber).maxOrNull() ?: 0) + 1
        db.settingsDao().put(SettingEntity(SettingKeys.NEXT_MEMBER_SEQ, nextMember.toString()))
    }

    private fun codeNumber(code: String): Int? = code.substringAfter('-', "").toIntOrNull()

    /**
     * Maps cloud documents into entities, skipping any single document that
     * can't be parsed (e.g. a partially-written record from an interrupted
     * older backup) rather than aborting the whole restore. One damaged row
     * must never block recovering everything else.
     */
    private fun <T> List<Pair<String, Map<String, Any?>>>.mapDocs(
        collection: String,
        transform: (String, Map<String, Any?>) -> T,
    ): List<T> = mapNotNull { (id, m) ->
        runCatching { transform(id, m) }.getOrElse { e ->
            android.util.Log.w("LibrarySync", "skipping malformed $collection/$id: ${e.message}")
            null
        }
    }

    private fun now(): Long = clock.instant().toEpochMilli()
}
