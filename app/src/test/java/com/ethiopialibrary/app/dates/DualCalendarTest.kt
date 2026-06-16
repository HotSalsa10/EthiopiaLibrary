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
 * Ethiopia uses the Ethiopian calendar in daily life (~7-8 years behind
 * Gregorian, 13 months). The UI shows both: Ethiopian primary in Amharic,
 * Gregorian primary in English/Arabic.
 */
@RunWith(RobolectricTestRunner::class)
class DualCalendarTest {

    // 2026-06-10 Gregorian = Sene (month 10) 3, 2018 in the Ethiopian calendar
    private val epoch = Instant.parse("2026-06-10T09:00:00Z").toEpochMilli()

    @Test
    fun `epoch converts to correct ethiopian date fields`() {
        val date = EthiopianDate.fromEpochMillis(epoch)
        assertEquals(2018, date.year)
        assertEquals(10, date.month)
        assertEquals(3, date.day)
    }

    @Test
    fun `january converts inside ethiopian month tahsas`() {
        // 2026-01-01 Gregorian = Tahsas (month 4) 23, 2018
        val date = EthiopianDate.fromEpochMillis(Instant.parse("2026-01-01T12:00:00Z").toEpochMilli())
        assertEquals(2018, date.year)
        assertEquals(4, date.month)
        assertEquals(23, date.day)
    }

    @Test
    fun `amharic locale shows ethiopian year before gregorian year`() {
        val text = DualCalendarFormatter.format(epoch, Locale("am"))
        assertTrue("expected Ethiopian year in: $text", text.contains("2018"))
        assertTrue("expected Gregorian year in: $text", text.contains("2026"))
        assertTrue("Ethiopian must come first in Amharic: $text", text.indexOf("2018") < text.indexOf("2026"))
    }

    @Test
    fun `english locale shows gregorian year before ethiopian year`() {
        val text = DualCalendarFormatter.format(epoch, Locale.ENGLISH)
        assertTrue("expected Gregorian year in: $text", text.contains("2026"))
        assertTrue("expected Ethiopian year in: $text", text.contains("2018"))
        assertTrue("Gregorian must come first in English: $text", text.indexOf("2026") < text.indexOf("2018"))
    }

    @Test
    fun `arabic locale shows gregorian year before ethiopian year`() {
        val text = DualCalendarFormatter.format(epoch, Locale("ar"))
        assertTrue("expected both years in: $text", text.contains("2026") && text.contains("2018"))
        assertTrue("Gregorian must come first in Arabic: $text", text.indexOf("2026") < text.indexOf("2018"))
    }

    @Test
    fun `ethiopian mode shows only the ethiopian date`() {
        // English locale would normally lead with Gregorian; ETHIOPIAN must drop it.
        val text = DualCalendarFormatter.format(epoch, Locale.ENGLISH, CalendarMode.ETHIOPIAN)
        assertTrue("expected Ethiopian year in: $text", text.contains("2018"))
        assertFalse("Gregorian year must be absent: $text", text.contains("2026"))
    }

    @Test
    fun `gregorian mode shows only the gregorian date`() {
        // Amharic locale would normally lead with Ethiopian; GREGORIAN must drop it.
        val text = DualCalendarFormatter.format(epoch, Locale("am"), CalendarMode.GREGORIAN)
        assertTrue("expected Gregorian year in: $text", text.contains("2026"))
        assertFalse("Ethiopian year must be absent: $text", text.contains("2018"))
    }

    @Test
    fun `dual is the default and shows both dates`() {
        val text = DualCalendarFormatter.format(epoch, Locale.ENGLISH)
        assertTrue("expected both calendars: $text", text.contains("2026") && text.contains("2018"))
    }
}
