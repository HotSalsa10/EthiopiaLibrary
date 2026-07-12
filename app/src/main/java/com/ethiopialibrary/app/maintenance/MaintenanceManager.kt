package com.ethiopialibrary.app.maintenance

import com.ethiopialibrary.app.data.IntegrityChecker
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.SnapshotManager
import com.ethiopialibrary.app.data.countBadCodes
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
            val badCodes = db.countBadCodes()
            val cutoff = now - SYNC_ROW_RETENTION_DAYS * 24 * 60 * 60 * 1000
            val prunedSync = db.syncQueueDao().deleteSyncedBefore(cutoff)
            Triple(overdue, badCodes, prunedSync)
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
