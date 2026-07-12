package com.ethiopialibrary.app.util

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Null-safe wrapper over Crashlytics so callers never need to guard on
 * whether Firebase is configured - same pattern as SyncLocator/
 * MaintenanceLocator. No-ops in unit tests and in a debug build with no
 * google-services.json.
 */
object CrashReporter {
    @Volatile
    var recorder: ((Throwable) -> Unit)? = null

    fun install(context: Context) {
        recorder = if (FirebaseApp.getApps(context).isNotEmpty()) {
            { e -> FirebaseCrashlytics.getInstance().recordException(e) }
        } else {
            null
        }
    }

    fun record(e: Throwable) {
        recorder?.invoke(e)
    }
}
