package com.ethiopialibrary.app.update

import java.io.File

/** In-memory stand-in for the real HTTP client: no network, fully controllable. */
class FakeUpdateNetworkClient : UpdateNetworkClient {

    var manifestJson: Map<String, Any?>? = null
    var manifestFetchFails: Boolean = false
    var apkBytes: ByteArray = ByteArray(0)
    var downloadFails: Boolean = false

    val fetchedUrls = mutableListOf<String>()
    val downloadedUrls = mutableListOf<String>()

    override suspend fun fetchManifestJson(url: String): Map<String, Any?>? {
        fetchedUrls += url
        if (manifestFetchFails) return null
        return manifestJson
    }

    override suspend fun downloadApk(url: String, destination: File): Boolean {
        downloadedUrls += url
        if (downloadFails) return false
        destination.writeBytes(apkBytes)
        return true
    }
}
