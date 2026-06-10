package com.ethiopialibrary.app.sync

/** In-memory CloudStore: what Firestore would hold, minus the network. */
class FakeCloudStore : CloudStore {

    val collections = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>()
    var failOn: ((collection: String, docId: String) -> Boolean)? = null

    override suspend fun upsert(collection: String, docId: String, data: Map<String, Any?>) {
        if (failOn?.invoke(collection, docId) == true) {
            throw RuntimeException("simulated upload failure")
        }
        collections.getOrPut(collection) { mutableMapOf() }[docId] = data
    }

    override suspend fun fetchAll(collection: String): List<Pair<String, Map<String, Any?>>> =
        collections[collection].orEmpty().map { it.key to it.value }
}
