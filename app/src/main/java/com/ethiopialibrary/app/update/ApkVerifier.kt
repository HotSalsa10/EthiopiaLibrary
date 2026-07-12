package com.ethiopialibrary.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Reads facts about a downloaded-but-not-yet-installed APK file, without installing it. */
interface ApkVerifier {
    /** Null if the file isn't a parseable APK at all. */
    fun packageName(file: File): String?

    /** Hex SHA-256 (no colons) of the APK's signing certificate, or null if it can't be read. */
    fun signingCertSha256(file: File): String?
}

/**
 * `getPackageArchiveInfo` reads an APK's manifest/signature without installing it - exactly
 * what's needed to check "is this really our app, signed by our key" before ever handing the
 * file to [android.content.pm.PackageInstaller]. `GET_SIGNING_CERTIFICATES` (API 28+) replaced
 * the deprecated `GET_SIGNATURES`; minSdk is 26, so both paths are needed.
 */
class PackageManagerApkVerifier(private val context: Context) : ApkVerifier {

    override fun packageName(file: File): String? = archiveInfo(file)?.packageName

    override fun signingCertSha256(file: File): String? {
        val certBytes = certBytes(file) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun archiveInfo(file: File) = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags())

    @Suppress("DEPRECATION")
    private fun certBytes(file: File): ByteArray? {
        val info = archiveInfo(file) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            info.signatures?.firstOrNull()?.toByteArray()
        }
    }

    @Suppress("DEPRECATION")
    private fun flags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
}
