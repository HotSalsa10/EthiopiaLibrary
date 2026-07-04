package com.ethiopialibrary.app.data

private const val DAY_MILLIS = 86_400_000L

/** Calendar-free day bucket used to throttle the nudge to once per day. */
fun epochDay(at: Long): Long = at / DAY_MILLIS

/**
 * Whether to gently suggest a backup: changes are waiting, the last backup
 * is stale (or never happened), and the operator hasn't dismissed the
 * suggestion today. Connectivity is checked by the caller.
 */
fun shouldNudgeBackup(pending: Int, lastSyncAt: Long?, dismissedDay: Long?, now: Long): Boolean {
    if (pending <= 0) return false
    val stale = lastSyncAt == null || now - lastSyncAt > DAY_MILLIS
    if (!stale) return false
    return dismissedDay != epochDay(now)
}
