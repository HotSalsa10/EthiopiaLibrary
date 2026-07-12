package com.ethiopialibrary.app

import android.app.Application
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.maintenance.MaintenanceLocator
import com.ethiopialibrary.app.maintenance.MaintenanceManager
import com.ethiopialibrary.app.maintenance.MaintenanceWorker
import com.ethiopialibrary.app.sync.FirestoreCloudStore
import com.ethiopialibrary.app.sync.SyncEngine
import com.ethiopialibrary.app.sync.SyncLocator
import com.ethiopialibrary.app.sync.SyncWorker
import com.ethiopialibrary.app.util.CrashReporter
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock

class LibraryApp : Application() {

    val database: LibraryDatabase by lazy { LibraryDatabase.create(this) }

    val repository: LibraryRepository by lazy {
        LibraryRepository(database, Clock.systemDefaultZone())
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)

        // Sync stays dormant until Firebase is configured (google-services.json
        // present) AND the library account is signed in.
        SyncLocator.engineFactory = factory@{
            if (FirebaseApp.getApps(this).isEmpty()) return@factory null
            if (FirebaseAuth.getInstance().currentUser == null) return@factory null
            SyncEngine(database, FirestoreCloudStore(), Clock.systemDefaultZone())
        }
        SyncWorker.schedule(this)

        // Throttled backup on data change: cuts the recovery point from the
        // 24h periodic worker down to ~10 minutes whenever online, without
        // polling - WorkManager's own KEEP policy coalesces the bursts of
        // triggers a normal desk session produces into one scheduled upload.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            repository.pendingSyncCount()
                .filter { it > 0 }
                .distinctUntilChanged()
                .collect { SyncWorker.debouncedBackup(this@LibraryApp) }
        }

        MaintenanceLocator.managerFactory = { context ->
            MaintenanceManager(
                db = database,
                snapshotDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "snapshots"),
                clock = Clock.systemDefaultZone(),
            )
        }
        MaintenanceWorker.schedule(this)
    }
}
