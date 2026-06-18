package com.ethiopialibrary.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The physical-copy code: category · book# · copy# · volume → e.g. TF-001-1-00. */
class BookCodeTest {

    @Test
    fun `renders the four-part code`() {
        assertEquals("TF-001-1-00", BookCode.render("TF", bookNumber = 1, copyNumber = 1, volumeNumber = 0))
    }

    @Test
    fun `pads book number to 3 and volume to 2 digits`() {
        assertEquals("AQ-045-2-13", BookCode.render("AQ", bookNumber = 45, copyNumber = 2, volumeNumber = 13))
    }

    @Test
    fun `uses the full three digit book number when large`() {
        assertEquals("FQ-999-9-99", BookCode.render("FQ", bookNumber = 999, copyNumber = 9, volumeNumber = 99))
    }
}
