package com.ethiopialibrary.app.maintenance

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object MaintenanceLocator {
    @Volatile
    var managerFactory: ((Context) -> MaintenanceManager)? = null

    fun manager(context: Context): MaintenanceManager? = managerFactory?.invoke(context)
}

class MaintenanceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = MaintenanceLocator.manager(applicationContext) ?: return Result.success()
        return try {
            val result = withContext(Dispatchers.IO) { manager.runDailyMaintenance() }
            if (!result.databaseHealthy) {
                Log.e(TAG, "Database integrity check FAILED - snapshot taken at ${result.snapshot}")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Daily maintenance failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MaintenanceWorker"
        private const val UNIQUE_NAME = "daily-maintenance"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(24, TimeUnit.HOURS).build(),
            )
        }
    }
}
