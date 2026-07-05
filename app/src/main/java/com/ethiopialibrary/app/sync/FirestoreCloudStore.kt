package com.ethiopialibrary.app.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Thin Firestore adapter.
 *
 * We deliberately do NOT touch [FirebaseFirestore.setFirestoreSettings]: those
 * can only be set before Firestore's first use, and this adapter is created
 * fresh for every backup/restore, so re-applying them threw
 * IllegalStateException on the second operation onward (which surfaced as
 * "Restore failed"). Firestore's default on-disk cache is also the safer
 * choice - a write survives a process death and is retried until the backend
 * acknowledges it, instead of being lost from an in-memory queue.
 */
class FirestoreCloudStore : CloudStore {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override suspend fun upsertBatch(items: List<CloudUpsert>) {
        val batch = firestore.batch()
        items.forEach { item ->
            batch.set(firestore.collection(item.collection).document(item.docId), item.data)
        }
        batch.commit().await()
        // commit() resolves on the local write; block until the backend has
        // actually acknowledged everything so the outbox is only cleared once
        // the data is durably in the cloud - never on a mere local commit.
        firestore.waitForPendingWrites().await()
    }

    override suspend fun fetchAll(collection: String): List<Pair<String, Map<String, Any?>>> =
        firestore.collection(collection).get(Source.SERVER).await().documents.map { doc ->
            doc.id to (doc.data ?: emptyMap())
        }
}
