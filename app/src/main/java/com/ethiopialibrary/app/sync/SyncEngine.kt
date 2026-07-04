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
) {
    companion object {
        /** [SettingKeys.LAST_SYNC_RESULT] value after a successful backup or restore. */
        const val RESULT_OK = "ok"
    }

    /**
     * Uploads pending outbox entries in write order. Stops at the first
     * failure (WorkManager retries later with backoff); already-uploaded
     * entries stay marked and never re-upload.
     */
    suspend fun drainOutbox(): SyncResult {
        val pending = db.syncQueueDao().pending()
        var uploaded = 0
        for (entry in pending) {
            try {
                buildPayload(entry)?.let { (collection, payload) ->
                    cloud.upsert(collection, entry.entityId, payload)
                }
                db.syncQueueDao().markSynced(entry.localId, now())
                uploaded++
            } catch (e: Exception) {
                db.syncQueueDao().recordAttempt(entry.localId)
                recordResult("error:${e.javaClass.simpleName}")
                return SyncResult.Failure(uploaded, e.message)
            }
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
        val categories = cloud.fetchAll("categories").map { (id, m) -> categoryFrom(id, m) }
        val books = cloud.fetchAll("books").map { (id, m) -> bookFrom(id, m) }
        val copies = cloud.fetchAll("book_copies").map { (id, m) -> copyFrom(id, m) }
        val members = cloud.fetchAll("members").map { (id, m) -> memberFrom(id, m) }
        val loans = cloud.fetchAll("loans").map { (id, m) -> loanFrom(id, m) }
        val config = cloud.fetchAll("config")

        db.withTransaction {
            db.categoryDao().upsertAll(categories)
            db.bookDao().upsertAll(books)
            db.bookCopyDao().upsertAll(copies)
            db.memberDao().upsertAll(members)
            db.loanDao().upsertAll(loans)
            config.forEach { (key, data) ->
                (data["value"] as? String)?.let { db.settingsDao().put(SettingEntity(key, it)) }
            }
            recomputeSequences()
            // A freshly restored tablet IS in sync with the cloud - stamp the
            // status so the dashboard doesn't claim "never backed up".
            db.settingsDao().put(SettingEntity(SettingKeys.LAST_SYNC_AT, now().toString()))
            recordResult(RESULT_OK)
        }
        return categories.size + books.size + copies.size + members.size + loans.size
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

    private fun now(): Long = clock.instant().toEpochMilli()
}
