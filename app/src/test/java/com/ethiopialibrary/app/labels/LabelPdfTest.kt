package com.ethiopialibrary.app.labels

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * android.graphics.pdf.PdfDocument has no JVM implementation under
 * Robolectric, so PDF byte output is verified on-device (Phase 2 checklist).
 * The QR rendering that feeds the PDF is fully testable and tested here.
 */
@RunWith(RobolectricTestRunner::class)
class LabelPdfTest {

    @Test
    fun `qr bitmap renders black and white modules at requested size`() {
        val bitmap = LabelGenerator.qrBitmap("B-0173", 128)

        assertEquals(128, bitmap.width)
        assertEquals(128, bitmap.height)

        var black = 0
        var white = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                when (bitmap.getPixel(x, y)) {
                    Color.BLACK -> black++
                    Color.WHITE -> white++
                }
            }
        }
        assertTrue("QR must contain black modules", black > 0)
        assertTrue("QR must contain white quiet zone", white > 0)
        assertEquals("every pixel must be pure black or white", 128 * 128, black + white)
    }
}
