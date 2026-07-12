package com.ethiopialibrary.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File

private const val TAG = "UpdateInstall"

/** Installs an already-downloaded-and-verified APK (see UpdateWorker) without the user browsing for it. */
interface UpdateInstaller {
    /** Android 8+ requires per-app consent to install from a source other than Play. */
    fun canInstall(): Boolean

    /** Sends the operator to the one-time system settings screen that grants [canInstall]. */
    fun requestInstallPermission()

    fun install(apkPath: String)
}

/**
 * `PackageInstaller` reads and writes an install session without ever needing
 * the file to sit in public storage. `USER_ACTION_NOT_REQUIRED` (API 31+)
 * is what makes a second and later self-update near-silent once this app is
 * its own installer-of-record - the very first self-update on a given
 * tablet still shows one system confirm dialog regardless of API level,
 * which is why Wave 6's dress rehearsal performs that first update in-hand
 * before the tablet ships.
 */
class PackageInstallerUpdateInstaller(private val context: Context) : UpdateInstaller {

    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun requestInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun install(apkPath: String) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        session.use {
            val apkFile = File(apkPath)
            session.openWrite("update", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            session.commit(installStatusPendingIntent(sessionId).intentSender)
        }
    }

    private fun installStatusPendingIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}

/**
 * Receives the install session's outcome. `STATUS_PENDING_USER_ACTION` carries
 * the system confirm dialog as an [Intent] that must be launched from here;
 * anything else is terminal (success or a real failure) and just gets logged
 * - there is no UI left to update by the time this fires (the app may not
 * even be in the foreground).
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "self-update installed successfully")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e(TAG, "self-update install failed: status=$status message=$message")
            }
        }
    }
}
