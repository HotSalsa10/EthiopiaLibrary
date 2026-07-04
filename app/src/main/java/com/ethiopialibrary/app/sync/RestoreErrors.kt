package com.ethiopialibrary.app.sync

import com.ethiopialibrary.app.R
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/** Maps a restore failure to the message the operator should see. */
fun restoreFailureMessageRes(error: Throwable): Int = when {
    error is FirebaseFirestoreException &&
        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        R.string.restore_permission_denied
    error is FirebaseFirestoreException &&
        error.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
        R.string.restore_network_error
    error is IOException -> R.string.restore_network_error
    else -> R.string.restore_failed
}
