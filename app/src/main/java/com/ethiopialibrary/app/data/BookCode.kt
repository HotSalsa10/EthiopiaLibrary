package com.ethiopialibrary.app.data

/**
 * The accession code printed on each physical copy:
 * `category · book# · copy# · volume` → e.g. `TF-001-1-00`.
 *
 * The four parts are stored separately on the book/copy; this renders the
 * single string used for the QR label, scanning, and lookup.
 */
object BookCode {
    fun render(categoryCode: String, bookNumber: Int, copyNumber: Int, volumeNumber: Int): String =
        "%s-%03d-%d-%02d".format(categoryCode, bookNumber, copyNumber, volumeNumber)
}
