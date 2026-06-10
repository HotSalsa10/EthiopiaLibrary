package com.ethiopialibrary.app.labels

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.io.OutputStream

data class LabelData(val code: String, val title: String)

/**
 * Printable QR label sheets (A4, 3x7 grid). Each label carries the QR for
 * the camera and the human-readable code as the manual fallback. The PDF can
 * be printed at any print shop - no label printer required.
 */
object LabelGenerator {

    const val LABELS_PER_PAGE = 21 // 3 columns x 7 rows

    private const val PAGE_WIDTH = 595 // A4 in PostScript points
    private const val PAGE_HEIGHT = 842
    private const val COLUMNS = 3
    private const val ROWS = 7

    fun qrMatrix(content: String, size: Int = 256): BitMatrix =
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)

    fun pageCount(labelCount: Int): Int =
        (labelCount + LABELS_PER_PAGE - 1) / LABELS_PER_PAGE

    fun createLabelPdf(labels: List<LabelData>, dest: OutputStream) {
        val document = PdfDocument()
        val cellWidth = PAGE_WIDTH / COLUMNS
        val cellHeight = PAGE_HEIGHT / ROWS
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        labels.chunked(LABELS_PER_PAGE).forEachIndexed { pageIndex, page ->
            val pdfPage = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create(),
            )
            val canvas = pdfPage.canvas
            page.forEachIndexed { i, label ->
                val x = (i % COLUMNS) * cellWidth
                val y = (i / COLUMNS) * cellHeight
                val qrSize = minOf(cellWidth, cellHeight) - 44
                val qr = qrBitmap(label.code, qrSize)
                canvas.drawBitmap(qr, (x + (cellWidth - qrSize) / 2f), y + 6f, null)
                canvas.drawText(
                    label.code,
                    x + cellWidth / 2f - paint.measureText(label.code) / 2f,
                    y + qrSize + 20f,
                    paint,
                )
                val title = if (label.title.length > 22) label.title.take(21) + "…" else label.title
                canvas.drawText(
                    title,
                    x + cellWidth / 2f - paint.measureText(title) / 2f,
                    y + qrSize + 36f,
                    paint,
                )
                qr.recycle()
            }
            document.finishPage(pdfPage)
        }
        document.writeTo(dest)
        document.close()
    }

    fun qrBitmap(content: String, size: Int): Bitmap {
        val matrix = qrMatrix(content, size)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}
