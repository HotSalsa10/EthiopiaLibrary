package com.ethiopialibrary.app.sync

/**
 * The only thing the sync engine knows about the cloud. Firestore implements
 * it in production; tests use an in-memory fake. Payload values are limited
 * to String, Long, Boolean and null - types Firestore round-trips losslessly.
 */
interface CloudStore {
    suspend fun upsert(collection: String, docId: String, data: Map<String, Any?>)
    suspend fun fetchAll(collection: String): List<Pair<String, Map<String, Any?>>>
}

sealed interface SyncResult {
    data class Success(val uploaded: Int) : SyncResult
    data class Failure(val uploaded: Int, val message: String?) : SyncResult
}
