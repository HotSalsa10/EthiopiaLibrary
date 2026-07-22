package com.ethiopialibrary.app.data

import java.util.Locale

/**
 * The accession code printed on each physical copy:
 * `category · book# · copy# · volume` → e.g. `TF-001-01-00`.
 *
 * The four parts are stored separately on the book/copy; this renders the
 * single string used for the QR label, scanning, and lookup. bookNumber and
 * volumeNumber are zero-padded so codes stay a fixed width; copyNumber is
 * zero-padded too - without it, a book with 10+ copies would sort "10"
 * before "2" as text (label sheets, restored lists) since it isn't a number
 * there, it's a code.
 */
object BookCode {
    fun render(categoryCode: String, bookNumber: Int, copyNumber: Int, volumeNumber: Int): String =
        // Locale.ROOT: this code is scanned, printed, and re-parsed as a number -
        // it must never render with non-ASCII digits under the Arabic UI locale.
        "%s-%03d-%02d-%02d".format(Locale.ROOT, categoryCode, bookNumber, copyNumber, volumeNumber)
}
