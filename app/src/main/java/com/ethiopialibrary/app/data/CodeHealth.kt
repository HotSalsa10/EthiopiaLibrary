package com.ethiopialibrary.app.data

/**
 * True when [code] contains a Unicode digit that isn't plain ASCII 0-9 -
 * the signature of a member/copy code rendered under a non-Latin-digit
 * default locale (e.g. Arabic) before formatting was pinned to Locale.ROOT.
 * Detected rather than auto-repaired: rewriting a code already printed on a
 * physical label would be worse than flagging it.
 */
fun hasNonAsciiDigit(code: String): Boolean = code.any { it.isDigit() && it !in '0'..'9' }

/** Shared by the daily maintenance sweep and the sync heartbeat. */
suspend fun LibraryDatabase.countBadCodes(): Int {
    val badMemberCodes = memberDao().allMemberCodes().count(::hasNonAsciiDigit)
    val badCopyCodes = bookCopyDao().allCopyCodes().count(::hasNonAsciiDigit)
    return badMemberCodes + badCopyCodes
}
