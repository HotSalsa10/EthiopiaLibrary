package com.ethiopialibrary.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Resolves the sync engine at runtime so the worker stays constructible by
 * WorkManager's default factory. Returns null until Firebase is configured
 * and the library account is signed in - the worker then no-ops.
 */
object SyncLocator {
    @Volatile
    var engineFactory: ((Context) -> SyncEngine?)? = null

    fun engine(context: Context): SyncEngine? = engineFactory?.invoke(context)
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val engine = SyncLocator.engine(applicationContext) ?: return Result.success()
        return when (engine.drainOutbox()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Failure ->
                if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 10
        private const val UNIQUE_PERIODIC = "cloud-backup-periodic"
        private const val UNIQUE_ONESHOT = "cloud-backup-now"

        private val connected =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * Daily safety-net backup plus a pending one-shot that the OS fires
         * the moment connectivity appears - no polling, no battery cost.
         */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                    .setConstraints(connected)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build(),
            )
            wm.enqueueUniqueWork(
                UNIQUE_ONESHOT,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(connected).build(),
            )
        }

        fun backupNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONESHOT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(connected).build(),
            )
        }
    }
}
