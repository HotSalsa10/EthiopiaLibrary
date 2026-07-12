package com.ethiopialibrary.app.update

import java.io.File

/** Stand-in for the real PackageManager-backed verifier - avoids needing an actual signed APK in tests. */
class FakeApkVerifier(
    var packageNameResult: String? = "com.ethiopialibrary.app",
    var certSha256Result: String? = PINNED_RELEASE_CERT_SHA256,
) : ApkVerifier {
    override fun packageName(file: File): String? = packageNameResult
    override fun signingCertSha256(file: File): String? = certSha256Result
}
