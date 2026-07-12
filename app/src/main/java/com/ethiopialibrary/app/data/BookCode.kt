package com.ethiopialibrary.app.data

import java.util.Locale

/**
 * The accession code printed on each physical copy:
 * `category · book# · copy# · volume` → e.g. `TF-001-1-00`.
 *
 * The four parts are stored separately on the book/copy; this renders the
 * single string used for the QR label, scanning, and lookup.
 */
object BookCode {
    fun render(categoryCode: String, bookNumber: Int, copyNumber: Int, volumeNumber: Int): String =
        // Locale.ROOT: this code is scanned, printed, and re-parsed as a number -
        // it must never render with non-ASCII digits under the Arabic UI locale.
        "%s-%03d-%d-%02d".format(Locale.ROOT, categoryCode, bookNumber, copyNumber, volumeNumber)
}
