package com.ethiopialibrary.app.maintenance

import com.ethiopialibrary.app.data.IntegrityChecker
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.SnapshotManager
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class MaintenanceResult(
    val databaseHealthy: Boolean,
    val snapshot: File,
    val prunedCount: Int,
)

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

        return MaintenanceResult(
            databaseHealthy = healthy,
            snapshot = snapshot,
            prunedCount = stale.size,
        )
    }
}
