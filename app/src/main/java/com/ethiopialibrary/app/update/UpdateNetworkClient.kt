package com.ethiopialibrary.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

/** The only thing the update worker knows about the network; Firestore has its own [com.ethiopialibrary.app.sync.CloudStore] equivalent. */
interface UpdateNetworkClient {
    /** Null on any failure (offline, non-2xx, malformed JSON) - the caller treats that as "try again later". */
    suspend fun fetchManifestJson(url: String): Map<String, Any?>?

    /** False on any failure; the caller is responsible for cleaning up a partial [destination]. */
    suspend fun downloadApk(url: String, destination: File): Boolean
}

/** Plain HttpURLConnection - no new dependency for what's a single small JSON fetch plus one file download. */
class HttpUpdateNetworkClient : UpdateNetworkClient {

    override suspend fun fetchManifestJson(url: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        try {
            val text = URL(url).openStream().bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            obj.keys().asSequence().associateWith { key ->
                obj.get(key).takeIf { it != JSONObject.NULL }
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun downloadApk(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            URL(url).openStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
