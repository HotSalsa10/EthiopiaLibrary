package com.ethiopialibrary.app.maintenance

import com.ethiopialibrary.app.data.IntegrityChecker
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.SnapshotManager
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class MaintenanceResult(
    val databaseHealthy: Boolean,
    val snapshot: File,
    val prunedCount: Int,
    val overdueCount: Int,
    val badCodeCount: Int,
    val prunedSyncRows: Int,
)

/** Already-uploaded outbox rows older than this are dead weight; nothing reads them again. */
private const val SYNC_ROW_RETENTION_DAYS = 30L

/**
 * True when [code] contains a Unicode digit that isn't plain ASCII 0-9 -
 * the signature of a member/copy code rendered under a non-Latin-digit
 * default locale (e.g. Arabic) before formatting was pinned to Locale.ROOT.
 * Detected rather than auto-repaired: rewriting a code already printed on a
 * physical label would be worse than flagging it.
 */
private fun hasNonAsciiDigit(code: String): Boolean = code.any { it.isDigit() && it !in '0'..'9' }

/**
 * Daily on-device safety net, independent of cloud sync: verifies database
 * integrity, writes a dated snapshot, and rotates old ones so cheap-tablet
 * storage never fills up. Snapshot names sort lexically by date.
 */
class MaintenanceManager(
    private val db: LibraryDatabase,
    private val snapshotDir: File,
    private val clock: Clock,
    private val keepCount: Int = 7,
) {
    private val nameFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun runDailyMaintenance(): MaintenanceResult {
        snapshotDir.mkdirs()
        val healthy = IntegrityChecker.quickCheck(db)

        val snapshot = File(snapshotDir, "library-${nameFormat.format(clock.instant())}.db")
        SnapshotManager(db).createSnapshot(snapshot)

        val all = snapshotDir
            .listFiles { f -> f.name.startsWith("library-") && f.name.endsWith(".db") }
            .orEmpty()
            .sortedByDescending { it.name }
        val stale = all.drop(keepCount)
        stale.forEach { it.delete() }

        // Suspend Room query runs on Room's own executor, so blocking here is
        // safe even when maintenance is invoked off a coroutine.
        val (overdueCount, badCodeCount, prunedSyncRows) = runBlocking {
            val now = clock.instant().toEpochMilli()
            val overdue = db.loanDao().countOverdue(now)
            val badMemberCodes = db.memberDao().allMemberCodes().count(::hasNonAsciiDigit)
            val badCopyCodes = db.bookCopyDao().allCopyCodes().count(::hasNonAsciiDigit)
            val cutoff = now - SYNC_ROW_RETENTION_DAYS * 24 * 60 * 60 * 1000
            val prunedSync = db.syncQueueDao().deleteSyncedBefore(cutoff)
            Triple(overdue, badMemberCodes + badCopyCodes, prunedSync)
        }

        return MaintenanceResult(
            databaseHealthy = healthy,
            snapshot = snapshot,
            prunedCount = stale.size,
            overdueCount = overdueCount,
            badCodeCount = badCodeCount,
            prunedSyncRows = prunedSyncRows,
        )
    }
}
