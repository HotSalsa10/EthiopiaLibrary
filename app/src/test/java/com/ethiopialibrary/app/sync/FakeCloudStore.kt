package com.ethiopialibrary.app.sync

/** In-memory CloudStore: what Firestore would hold, minus the network. */
class FakeCloudStore : CloudStore {

    val collections = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>()
    var failOn: ((collection: String, docId: String) -> Boolean)? = null

    /** Size of every committed batch, in commit order. */
    val batchSizes = mutableListOf<Int>()

    override suspend fun upsertBatch(items: List<CloudUpsert>) {
        // Validate first, apply second: a failing item aborts the whole batch,
        // mirroring Firestore's all-or-nothing WriteBatch commit.
        items.forEach { item ->
            if (failOn?.invoke(item.collection, item.docId) == true) {
                throw RuntimeException("simulated upload failure")
            }
        }
        items.forEach { collections.getOrPut(it.collection) { mutableMapOf() }[it.docId] = it.data }
        batchSizes += items.size
    }

    override suspend fun fetchAll(collection: String): List<Pair<String, Map<String, Any?>>> =
        collections[collection].orEmpty().map { it.key to it.value }
}
