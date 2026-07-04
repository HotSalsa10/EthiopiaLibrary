package com.ethiopialibrary.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Emits true while the device has a default network. Event-driven
 * (no polling, no battery cost); used to time the gentle backup nudge.
 */
fun connectivityFlow(context: Context): Flow<Boolean> = callbackFlow {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }

        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    trySend(manager.activeNetwork != null)
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
