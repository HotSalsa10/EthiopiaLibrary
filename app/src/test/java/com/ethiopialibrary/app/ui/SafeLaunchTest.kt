package com.ethiopialibrary.app.ui

import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.util.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [safeLaunch] is what stands between a bad write and a bare crash - verified in isolation. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SafeLaunchTest {

    @After
    fun tearDown() {
        CrashReporter.recorder = null
    }

    @Test
    fun `a thrown exception is caught and emits the generic error resource`() = runTest {
        val received = mutableListOf<Int>()
        val collector = launch { UiErrorBus.errors.collect { received += it } }

        val job = safeLaunch { throw RuntimeException("simulated write failure") }
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertTrue(R.string.error_write_failed in received)
        collector.cancel()
    }

    @Test
    fun `a thrown exception is recorded to CrashReporter as a non-fatal`() = runTest {
        val recorded = mutableListOf<Throwable>()
        CrashReporter.recorder = { recorded += it }
        val thrown = RuntimeException("simulated write failure")

        safeLaunch { throw thrown }
        advanceUntilIdle()

        assertTrue(recorded.single() === thrown)
    }

    @Test
    fun `a successful block completes without emitting an error`() = runTest {
        val received = mutableListOf<Int>()
        val collector = launch { UiErrorBus.errors.collect { received += it } }

        val job = safeLaunch { /* no-op write */ }
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertTrue(received.isEmpty())
        collector.cancel()
    }

    @Test
    fun `cancellation propagates instead of being swallowed as a generic error`() = runTest {
        val received = mutableListOf<Int>()
        val collector = launch { UiErrorBus.errors.collect { received += it } }

        val job = safeLaunch { throw CancellationException("navigated away") }
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(received.isEmpty())
        collector.cancel()
    }
}
