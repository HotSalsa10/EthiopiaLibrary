package com.ethiopialibrary.app.sync

import android.os.Build
import androidx.room.withTransaction
import com.ethiopialibrary.app.BuildConfig
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.SettingEntity
import com.ethiopialibrary.app.data.SettingKeys
import com.ethiopialibrary.app.data.SyncQueueEntity
import com.ethiopialibrary.app.data.countBadCodes
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

        /** Collection/doc holding the backup-completeness manifest (see [drainOutbox]). */
        internal const val META_COLLECTION = "meta"
        internal const val MANIFEST_DOC_ID = "manifest"

        /** Collection/doc holding the remote liveness signal (see [drainOutbox]). */
        internal const val HEARTBEAT_DOC_ID = "heartbeat"
    }

    /**
     * Uploads pending outbox entries in write order as atomic batches
     * (fewer round-trips on flaky internet, all-or-nothing per chunk), then
     * writes the manifest doc (row counts per table, backup completeness) and
     * the heartbeat doc (app version/SDK/row counts/bad-code count/device
     * clock - "is this tablet alive and backing up") together LAST, so their
     * presence in the cloud certifies a complete drain. A restore compares
     * against the manifest to detect a torn backup instead of silently
     * reporting success; the heartbeat's device clock vs. Firestore's server
     * timestamp is how a remote clock-drift problem becomes visible without
     * physical access to the tablet.
     * Stops at the first failed chunk or a failed manifest/heartbeat write
     * (WorkManager retries later with backoff); committed chunks stay
     * marked and never re-upload.
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
        try {
            writeManifestAndHeartbeat()
        } catch (e: Exception) {
            recordResult("error:${e.javaClass.simpleName}")
            return SyncResult.Failure(uploaded, e.message)
        }
        db.settingsDao().put(SettingEntity(SettingKeys.LAST_SYNC_AT, now().toString()))
        recordResult(RESULT_OK)
        return SyncResult.Success(uploaded)
    }

    private suspend fun writeManifestAndHeartbeat() {
        val rowCounts = mapOf<String, Any?>(
            "categories" to db.categoryDao().count().toLong(),
            "books" to db.bookDao().totalRowCount().toLong(),
            "book_copies" to db.bookCopyDao().totalRowCount().toLong(),
            "members" to db.memberDao().totalRowCount().toLong(),
            "loans" to db.loanDao().totalRowCount().toLong(),
        )
        val manifest = rowCounts + mapOf<String, Any?>("completedAt" to now())
        val heartbeat = rowCounts + mapOf<String, Any?>(
            "versionCode" to BuildConfig.VERSION_CODE.toLong(),
            "versionName" to BuildConfig.VERSION_NAME,
            "sdkInt" to Build.VERSION.SDK_INT.toLong(),
            "badCodeCount" to db.countBadCodes().toLong(),
            "deviceClockMillis" to now(),
        )
        cloud.upsertBatch(
            listOf(
                CloudUpsert(META_COLLECTION, MANIFEST_DOC_ID, manifest),
                CloudUpsert(META_COLLECTION, HEARTBEAT_DOC_ID, heartbeat),
            ),
        )
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
     *
     * Also compares what actually came back against the last manifest
     * written by [drainOutbox]: fewer documents than the manifest promised
     * means the backup that produced them was torn (interrupted mid-upload)
     * and the restore is [RestoreOutcome.incomplete] - that must reach the
     * operator rather than a bare "success".
     */
    suspend fun restore(): RestoreOutcome {
        val categoriesRaw = cloud.fetchAll("categories")
        val booksRaw = cloud.fetchAll("books")
        val copiesRaw = cloud.fetchAll("book_copies")
        val membersRaw = cloud.fetchAll("members")
        val loansRaw = cloud.fetchAll("loans")
        val config = cloud.fetchAll("config")
        val manifest = cloud.fetchAll(META_COLLECTION).toMap()[MANIFEST_DOC_ID]

        val categoriesResult = categoriesRaw.mapDocs("categories", ::categoryFrom)
        val booksResult = booksRaw.mapDocs("books", ::bookFrom)
        val copiesResult = copiesRaw.mapDocs("book_copies", ::copyFrom)
        val membersResult = membersRaw.mapDocs("members", ::memberFrom)
        val loansResult = loansRaw.mapDocs("loans", ::loanFrom)
        val categories = categoriesResult.items
        val books = booksResult.items
        val copies = copiesResult.items
        val members = membersResult.items
        val loans = loansResult.items

        // Drop referentially-orphaned children whose parent document was
        // malformed-and-skipped (or never reached the cloud), so a foreign-key
        // violation can never abort the whole restore.
        val bookIds = books.mapTo(HashSet()) { it.id }
        val validCopies = copies.filter { it.bookId in bookIds }
        val copyIds = validCopies.mapTo(HashSet()) { it.id }
        val memberIds = members.mapTo(HashSet()) { it.id }
        val validLoans = loans.filter { it.copyId in copyIds && it.memberId in memberIds }
        val orphansDropped = (copies.size - validCopies.size) + (loans.size - validLoans.size)

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

        val fetchedCounts = mapOf(
            "categories" to categoriesRaw.size,
            "books" to booksRaw.size,
            "book_copies" to copiesRaw.size,
            "members" to membersRaw.size,
            "loans" to loansRaw.size,
        )
        return RestoreOutcome(
            restored = categories.size + books.size + validCopies.size + members.size + validLoans.size,
            skippedMalformed = categoriesResult.skipped + booksResult.skipped + copiesResult.skipped +
                membersResult.skipped + loansResult.skipped,
            orphansDropped = orphansDropped,
            incomplete = manifest != null && fetchedCounts.any { (table, fetched) ->
                val expected = (manifest[table] as? Number)?.toInt()
                expected != null && fetched < expected
            },
            manifestMissing = manifest == null,
        )
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

    /** Parsed entities plus a count of documents skipped as malformed. */
    private data class MapDocsResult<T>(val items: List<T>, val skipped: Int)

    /**
     * Maps cloud documents into entities, skipping any single document that
     * can't be parsed (e.g. a partially-written record from an interrupted
     * older backup) rather than aborting the whole restore. One damaged row
     * must never block recovering everything else - but the skip count is
     * carried back so the operator sees it instead of it only reaching logcat.
     */
    private fun <T> List<Pair<String, Map<String, Any?>>>.mapDocs(
        collection: String,
        transform: (String, Map<String, Any?>) -> T,
    ): MapDocsResult<T> {
        var skipped = 0
        val items = mapNotNull { (id, m) ->
            runCatching { transform(id, m) }.getOrElse { e ->
                android.util.Log.w("LibrarySync", "skipping malformed $collection/$id: ${e.message}")
                skipped++
                null
            }
        }
        return MapDocsResult(items, skipped)
    }

    private fun now(): Long = clock.instant().toEpochMilli()
}

/**
 * Outcome of [SyncEngine.restore]. [incomplete] is the important signal: it
 * means fewer documents came back than the last manifest promised, i.e. the
 * backup that produced them was interrupted mid-upload - the operator must
 * be told rather than shown a bare "restore complete".
 */
data class RestoreOutcome(
    val restored: Int,
    val skippedMalformed: Int,
    val orphansDropped: Int,
    val incomplete: Boolean,
    val manifestMissing: Boolean,
)
