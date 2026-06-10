package com.ethiopialibrary.app.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Fixed, manually advanceable clock so tests control "now". */
class TestClock(
    private var now: Instant = Instant.parse("2026-06-10T09:00:00Z"),
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now

    fun advanceDays(days: Long) {
        now = now.plusSeconds(days * 24 * 3600)
    }
}
