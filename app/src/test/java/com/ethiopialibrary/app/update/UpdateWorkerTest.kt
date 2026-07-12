package com.ethiopialibrary.app.update

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.ethiopialibrary.app.BuildConfig
import com.ethiopialibrary.app.data.LibraryDatabase
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.SettingEntity
import com.ethiopialibrary.app.data.SettingKeys
import com.ethiopialibrary.app.data.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class UpdateWorkerTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var network: FakeUpdateNetworkClient
    private lateinit var verifier: FakeApkVerifier

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LibraryRepository(db, TestClock())
        network = FakeUpdateNetworkClient()
        verifier = FakeApkVerifier()
        UpdateLocator.repositoryFactory = { repo }
        UpdateLocator.networkClient = network
        UpdateLocator.apkVerifierFactory = { verifier }
    }

    @After
    fun tearDown() {
        UpdateLocator.repositoryFactory = null
        UpdateLocator.networkClient = HttpUpdateNetworkClient()
        UpdateLocator.apkVerifierFactory = { context -> PackageManagerApkVerifier(context) }
        db.close()
    }

    private fun runWorker(): ListenableWorker.Result {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<UpdateWorker>(context).build()
        return runBlocking { worker.doWork() }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun manifestJson(versionCode: Long, apkBytes: ByteArray): Map<String, Any?> = mapOf(
        "versionCode" to versionCode,
        "versionName" to "2.0.$versionCode",
        "apkUrl" to "https://example.com/app-release.apk",
        "sha256" to sha256Hex(apkBytes),
    )

    @Test
    fun `worker succeeds quietly when not configured`() {
        UpdateLocator.repositoryFactory = null
        assertTrue(runWorker() is ListenableWorker.Result.Success)
    }

    @Test
    fun `worker succeeds quietly when the remote directive disables update checking`() = runBlocking {
        db.settingsDao().put(SettingEntity(SettingKeys.REMOTE_UPDATE_CHECK_ENABLED, "false"))

        assertTrue(runWorker() is ListenableWorker.Result.Success)
        assertTrue(network.fetchedUrls.isEmpty())
    }

    @Test
    fun `worker fetches the default manifest url when no override is cached`() {
        network.manifestFetchFails = true // fails fast; only the URL used matters here
        runWorker()
        assertEquals(listOf(UpdateWorker.DEFAULT_MANIFEST_URL), network.fetchedUrls)
    }

    @Test
    fun `worker fetches the remote-directive override url instead of the default`() = runBlocking {
        db.settingsDao().put(SettingEntity(SettingKeys.REMOTE_UPDATE_MANIFEST_URL, "https://override.example/latest.json"))
        network.manifestFetchFails = true

        runWorker()

        assertEquals(listOf("https://override.example/latest.json"), network.fetchedUrls)
    }

    @Test
    fun `a manifest fetch failure retries`() {
        network.manifestFetchFails = true
        assertTrue(runWorker() is ListenableWorker.Result.Retry)
    }

    @Test
    fun `a manifest at or below the running version is a no-op`() = runBlocking {
        network.manifestJson = manifestJson(BuildConfig.VERSION_CODE.toLong(), ByteArray(4))

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(repo.updateReadyVersionCode())
        assertTrue(network.downloadedUrls.isEmpty())
    }

    @Test
    fun `a newer valid manifest downloads, verifies, and marks the update ready`() = runBlocking {
        val apkBytes = "fake apk contents".toByteArray()
        network.apkBytes = apkBytes
        network.manifestJson = manifestJson(BuildConfig.VERSION_CODE + 1L, apkBytes)

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(BuildConfig.VERSION_CODE + 1, repo.updateReadyVersionCode())
        val info = repo.updateReadyInfo().first()!!
        assertEquals(BuildConfig.VERSION_CODE + 1, info.versionCode)
        assertEquals("2.0.${BuildConfig.VERSION_CODE + 1}", info.versionName)
    }

    @Test
    fun `an already-ready equal-or-newer version skips re-downloading`() = runBlocking {
        repo.markUpdateReady(BuildConfig.VERSION_CODE + 5, "already-ready", "/cache/existing.apk")
        network.manifestJson = manifestJson(BuildConfig.VERSION_CODE + 1L, ByteArray(4))

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(network.downloadedUrls.isEmpty())
        assertEquals(BuildConfig.VERSION_CODE + 5, repo.updateReadyVersionCode()) // untouched
    }

    @Test
    fun `a download failure retries`() = runBlocking {
        network.downloadFails = true
        network.manifestJson = manifestJson(BuildConfig.VERSION_CODE + 1L, ByteArray(4))

        assertTrue(runWorker() is ListenableWorker.Result.Retry)
        assertNull(repo.updateReadyVersionCode())
    }

    @Test
    fun `a sha256 mismatch fails the work and never marks ready`() = runBlocking {
        network.apkBytes = "actual bytes".toByteArray()
        // Manifest declares a hash for different bytes than what's actually downloaded.
        network.manifestJson = manifestJson(BuildConfig.VERSION_CODE + 1L, "declared bytes".toByteArray())

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertNull(repo.updateReadyVersionCode())
    }

    @Test
    fun `a package name mismatch fails the work and never marks ready`() = runBlocking {
        val apkBytes = "fake apk".toByteArray()
        network.apkBytes = apkBytes
        network.manifestJson = manifestJson(BuildConfig.VERSION_CODE + 1L, apkBytes)
        verifier.packageNameResult = "com.evil.app"

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertNull(repo.updateReadyVersionCode())
    }

    @Test
    fun `a signing-cert mismatch fails the work and never marks ready`() = runBlocking {
        val apkBytes = "fake apk".toByteArray()
        network.apkBytes = apkBytes
        network.manifestJson = manifestJson(BuildConfig.VERSION_CODE + 1L, apkBytes)
        verifier.certSha256Result = "0000000000000000000000000000000000000000000000000000000000000000"

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertNull(repo.updateReadyVersionCode())
    }

    @Test
    fun `stale cached apk files are cleaned up after a successful update`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stale = java.io.File(context.cacheDir, "update-1.apk").apply { writeBytes(ByteArray(1)) }

        val apkBytes = "fake apk".toByteArray()
        network.apkBytes = apkBytes
        network.manifestJson = manifestJson(BuildConfig.VERSION_CODE + 1L, apkBytes)

        runWorker()

        assertTrue(!stale.exists())
    }

    // ---------- scheduling ----------

    @Test
    fun `schedule enqueues a weekly periodic check`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        UpdateWorker.schedule(context)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("update-check-periodic").get()
        assertEquals(1, infos.size)
        assertTrue(infos.single().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `checkNow enqueues exactly one on-demand check across repeated calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        UpdateWorker.checkNow(context)
        UpdateWorker.checkNow(context)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("update-check-now").get()
        assertEquals(1, infos.size)
    }
}
