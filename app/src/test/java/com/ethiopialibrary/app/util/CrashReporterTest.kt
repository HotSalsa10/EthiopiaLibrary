package com.ethiopialibrary.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [CrashReporter] must never throw or require Firebase to be configured -
 * every safeLaunch catch block calls it unconditionally.
 */
@RunWith(RobolectricTestRunner::class)
class CrashReporterTest {

    @After
    fun tearDown() {
        CrashReporter.recorder = null
    }

    @Test
    fun `record is a no-op when no recorder is installed`() {
        CrashReporter.recorder = null
        // Must not throw even though nothing is installed.
        CrashReporter.record(RuntimeException("simulated"))
    }

    @Test
    fun `record invokes the installed recorder`() {
        val captured = mutableListOf<Throwable>()
        CrashReporter.recorder = { captured += it }

        val error = RuntimeException("simulated")
        CrashReporter.record(error)

        assertTrue(captured.single() === error)
    }

    @Test
    fun `install leaves the recorder null when Firebase is not configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        CrashReporter.install(context)

        // Robolectric's test app never registers a FirebaseApp instance, so
        // install() must fall back to the no-op recorder rather than reaching
        // for FirebaseCrashlytics.getInstance() and crashing.
        assertNull(CrashReporter.recorder)
    }
}
