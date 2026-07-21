package com.ethiopialibrary.app.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.Locale

/**
 * The app shows Hijri (Umm al-Qura) as the primary calendar, Gregorian in
 * parentheses, in every locale.
 */
@RunWith(RobolectricTestRunner::class)
class DualCalendarTest {

    // 2026-06-10 Gregorian falls in Dhu al-Hijjah 1447 (Umm al-Qura).
    private val epoch = Instant.parse("2026-06-10T09:00:00Z").toEpochMilli()

    @Test
    fun `epoch converts to correct hijri year`() {
        val date = HijriDate.fromEpochMillis(epoch)
        assertEquals(1447, date.year)
    }

    @Test
    fun `january 2026 falls in hijri year 1447`() {
        val date = HijriDate.fromEpochMillis(Instant.parse("2026-01-01T12:00:00Z").toEpochMilli())
        assertEquals(1447, date.year)
    }

    @Test
    fun `dual shows hijri before gregorian in amharic locale`() {
        val text = DualCalendarFormatter.format(epoch, Locale("am"))
        assertTrue("expected Hijri year in: $text", text.contains("1447"))
        assertTrue("expected Gregorian year in: $text", text.contains("2026"))
        assertTrue("Hijri must come first: $text", text.indexOf("1447") < text.indexOf("2026"))
    }

    @Test
    fun `dual shows hijri before gregorian in english locale`() {
        val text = DualCalendarFormatter.format(epoch, Locale.ENGLISH)
        assertTrue("expected Hijri year in: $text", text.contains("1447"))
        assertTrue("expected Gregorian year in: $text", text.contains("2026"))
        assertTrue("Hijri must come first: $text", text.indexOf("1447") < text.indexOf("2026"))
    }

    @Test
    fun `dual shows hijri before gregorian in arabic locale`() {
        val text = DualCalendarFormatter.format(epoch, Locale("ar"))
        assertTrue("expected both years in: $text", text.contains("2026") && text.contains("1447"))
        assertTrue("Hijri must come first: $text", text.indexOf("1447") < text.indexOf("2026"))
    }

    @Test
    fun `hijri mode shows only the hijri date`() {
        val text = DualCalendarFormatter.format(epoch, Locale.ENGLISH, CalendarMode.HIJRI)
        assertTrue("expected Hijri year in: $text", text.contains("1447"))
        assertFalse("Gregorian year must be absent: $text", text.contains("2026"))
    }

    @Test
    fun `gregorian mode shows only the gregorian date`() {
        val text = DualCalendarFormatter.format(epoch, Locale("am"), CalendarMode.GREGORIAN)
        assertTrue("expected Gregorian year in: $text", text.contains("2026"))
        assertFalse("Hijri year must be absent: $text", text.contains("1447"))
    }

    @Test
    fun `dual is the default and shows both dates`() {
        val text = DualCalendarFormatter.format(epoch, Locale.ENGLISH)
        assertTrue("expected both calendars: $text", text.contains("2026") && text.contains("1447"))
    }
}
