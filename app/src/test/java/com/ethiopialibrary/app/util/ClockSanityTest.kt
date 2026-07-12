package com.ethiopialibrary.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSanityTest {

    @Test
    fun `a device clock before the build time looks wrong`() {
        assertTrue(clockLooksWrong(now = 100L, buildTimeMillis = 200L))
    }

    @Test
    fun `a device clock at or after the build time looks fine`() {
        assertFalse(clockLooksWrong(now = 200L, buildTimeMillis = 200L))
        assertFalse(clockLooksWrong(now = 300L, buildTimeMillis = 200L))
    }
}
