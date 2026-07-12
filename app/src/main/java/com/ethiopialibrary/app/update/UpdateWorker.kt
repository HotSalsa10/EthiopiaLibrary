package com.ethiopialibrary.app.update

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
import com.ethiopialibrary.app.BuildConfig
import com.ethiopialibrary.app.data.LibraryRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Resolves dependencies the worker needs at runtime, mirroring SyncLocator/
 * MaintenanceLocator: the worker stays constructible by WorkManager's default
 * factory, and no-ops until [repositoryFactory] is wired up in LibraryApp.
 */
object UpdateLocator {
    @Volatile
    var repositoryFactory: ((Context) -> LibraryRepository?)? = null

    @Volatile
    var networkClient: UpdateNetworkClient = HttpUpdateNetworkClient()

    @Volatile
    var apkVerifierFactory: (Context) -> ApkVerifier = { context -> PackageManagerApkVerifier(context) }

    fun repository(context: Context): LibraryRepository? = repositoryFactory?.invoke(context)
}

/**
 * Checks for a newer signed build, downloads it to cache, and verifies it
 * twice over (content hash, then signing certificate) before ever marking it
 * "ready" - the install step (Wave 4 item 22) trusts that flag completely.
 * Honors the Console's updateCheckEnabled kill switch and updateManifestUrl
 * override (Wave 3 remote directives) before doing any network work at all.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = UpdateLocator.repository(applicationContext) ?: return Result.success()
        val directives = repo.remoteDirectives().first()
        if (!directives.updateCheckEnabled) return Result.success()

        val manifestUrl = directives.updateManifestUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_MANIFEST_URL
        val json = UpdateLocator.networkClient.fetchManifestJson(manifestUrl) ?: return Result.retry()
        val manifest = (parseUpdateManifest(json, BuildConfig.VERSION_CODE) as? ManifestParseResult.Valid)
            ?.manifest ?: return Result.success() // malformed / insecure / not-newer: nothing to do

        val alreadyReady = repo.updateReadyVersionCode()
        if (alreadyReady != null && alreadyReady >= manifest.versionCode) return Result.success()

        val cacheDir = applicationContext.cacheDir
        val dest = File(cacheDir, "update-${manifest.versionCode}.apk")
        cleanStalePartials(cacheDir, keep = dest)

        if (!UpdateLocator.networkClient.downloadApk(manifest.apkUrl, dest)) {
            dest.delete()
            return Result.retry()
        }

        if (!sha256Matches(manifest, sha256Of(dest))) {
            dest.delete()
            return Result.failure()
        }

        val verifier = UpdateLocator.apkVerifierFactory(applicationContext)
        val packageOk = verifier.packageName(dest) == applicationContext.packageName
        val certOk = verifier.signingCertSha256(dest)?.let(::certMatchesPinnedRelease) == true
        if (!packageOk || !certOk) {
            dest.delete()
            return Result.failure()
        }

        repo.markUpdateReady(manifest.versionCode, manifest.versionName, dest.absolutePath)
        return Result.success()
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Only the file being verified this run survives - a stale download from an older/failed check does not. */
    private fun cleanStalePartials(cacheDir: File, keep: File) {
        cacheDir.listFiles { f -> f.name.startsWith("update-") && f.name.endsWith(".apk") && f != keep }
            ?.forEach { it.delete() }
    }

    companion object {
        /**
         * Stable GitHub Releases URL - always resolves to whatever the latest
         * release published. Overridable per-tablet via the Console's
         * updateManifestUrl remote directive without a new app build.
         */
        const val DEFAULT_MANIFEST_URL =
            "https://github.com/HotSalsa10/EthiopiaLibrary/releases/latest/download/latest.json"

        private const val UNIQUE_PERIODIC = "update-check-periodic"
        private const val UNIQUE_ONESHOT = "update-check-now"

        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Weekly safety-net check; a compromised or offline week is not urgent for a low-traffic desk app. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(7, TimeUnit.DAYS)
                    .setConstraints(connected)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build(),
            )
        }

        /** Staff-triggered "check for update now" from Settings. */
        fun checkNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONESHOT,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(connected).build(),
            )
        }
    }
}
