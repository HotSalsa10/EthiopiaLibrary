package com.ethiopialibrary.app.labels

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test

/** QR labels carry the copy/member code; printed text is the manual fallback. */
class LabelTest {

    /** Feeds a generated BitMatrix straight back into the zxing decoder (no AWT). */
    private class BitMatrixLuminanceSource(private val matrix: BitMatrix) :
        LuminanceSource(matrix.width, matrix.height) {

        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = row?.takeIf { it.size >= width } ?: ByteArray(width)
            for (x in 0 until width) out[x] = if (matrix[x, y]) 0 else 255.toByte()
            return out
        }

        override fun getMatrix(): ByteArray {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[y * width + x] = if (matrix[x, y]) 0 else 255.toByte()
                }
            }
            return out
        }
    }

    private fun decode(matrix: BitMatrix): String =
        MultiFormatReader()
            .decode(BinaryBitmap(HybridBinarizer(BitMatrixLuminanceSource(matrix))))
            .text

    @Test
    fun `qr code round trips copy code`() {
        assertEquals("B-0173", decode(LabelGenerator.qrMatrix("B-0173")))
    }

    @Test
    fun `qr code round trips member code`() {
        assertEquals("M-0021", decode(LabelGenerator.qrMatrix("M-0021")))
    }

    @Test
    fun `label sheets paginate at 21 labels per A4 page`() {
        assertEquals(0, LabelGenerator.pageCount(0))
        assertEquals(1, LabelGenerator.pageCount(1))
        assertEquals(1, LabelGenerator.pageCount(21))
        assertEquals(2, LabelGenerator.pageCount(22))
        assertEquals(3, LabelGenerator.pageCount(43))
    }
}
