package com.ethiopialibrary.app.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Thin Firestore adapter. Firestore's own offline persistence is disabled -
 * the Room outbox is the offline queue, and the worker only runs with a
 * network constraint, so writes go straight to the server.
 */
class FirestoreCloudStore : CloudStore {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    override suspend fun upsertBatch(items: List<CloudUpsert>) {
        val batch = firestore.batch()
        items.forEach { item ->
            batch.set(firestore.collection(item.collection).document(item.docId), item.data)
        }
        batch.commit().await()
    }

    override suspend fun fetchAll(collection: String): List<Pair<String, Map<String, Any?>>> =
        firestore.collection(collection).get(Source.SERVER).await().documents.map { doc ->
            doc.id to (doc.data ?: emptyMap())
        }
}
