package com.ethiopialibrary.app.ui

import android.util.Log
import com.ethiopialibrary.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Emits a string resource for the UI to show when a [safeLaunch] block throws. */
object UiErrorBus {
    val errors = MutableSharedFlow<Int>(extraBufferCapacity = 4)
}

/**
 * Every mutating repository call from the UI used to run inside a bare
 * coroutine with no exception handling - any SQLite error (disk full,
 * corruption, a constraint violation) would crash the app with no message
 * the operator could act on. [safeLaunch] catches it, logs it, and emits a
 * generic trilingual error via [UiErrorBus] instead; the repository's own
 * transactions guarantee nothing was partially written either way.
 */
fun CoroutineScope.safeLaunch(block: suspend CoroutineScope.() -> Unit): Job = launch {
    try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        Log.e("EthiopiaLibrary", "unhandled error in a UI-triggered write", e)
        UiErrorBus.errors.tryEmit(R.string.error_write_failed)
    }
}
