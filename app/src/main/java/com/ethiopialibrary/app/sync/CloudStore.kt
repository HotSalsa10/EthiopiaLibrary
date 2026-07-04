package com.ethiopialibrary.app.sync

/**
 * The only thing the sync engine knows about the cloud. Firestore implements
 * it in production; tests use an in-memory fake. Payload values are limited
 * to String, Long, Boolean and null - types Firestore round-trips losslessly.
 */
interface CloudStore {
    /** Writes every item or none: one atomic commit, one network round-trip. */
    suspend fun upsertBatch(items: List<CloudUpsert>)

    suspend fun fetchAll(collection: String): List<Pair<String, Map<String, Any?>>>
}

/** One document write inside a batch. */
data class CloudUpsert(
    val collection: String,
    val docId: String,
    val data: Map<String, Any?>,
)

sealed interface SyncResult {
    data class Success(val uploaded: Int) : SyncResult
    data class Failure(val uploaded: Int, val message: String?) : SyncResult
}
