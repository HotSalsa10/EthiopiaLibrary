package com.ethiopialibrary.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNudgeTest {

    private val day = 86_400_000L
    private val now = 100 * day + 5_000_000L // some mid-day instant

    @Test
    fun `nudges when changes wait and the backup is older than a day`() {
        assertTrue(shouldNudgeBackup(pending = 3, lastSyncAt = now - 2 * day, dismissedDay = null, now = now))
    }

    @Test
    fun `nudges when there was never a backup`() {
        assertTrue(shouldNudgeBackup(pending = 1, lastSyncAt = null, dismissedDay = null, now = now))
    }

    @Test
    fun `stays quiet when nothing is pending`() {
        assertFalse(shouldNudgeBackup(pending = 0, lastSyncAt = null, dismissedDay = null, now = now))
    }

    @Test
    fun `stays quiet when the backup is fresh`() {
        assertFalse(shouldNudgeBackup(pending = 5, lastSyncAt = now - day / 2, dismissedDay = null, now = now))
    }

    @Test
    fun `stays quiet for the rest of the day once dismissed`() {
        assertFalse(shouldNudgeBackup(pending = 5, lastSyncAt = null, dismissedDay = epochDay(now), now = now))
        // Yesterday's dismissal no longer applies.
        assertTrue(shouldNudgeBackup(pending = 5, lastSyncAt = null, dismissedDay = epochDay(now) - 1, now = now))
    }
}
