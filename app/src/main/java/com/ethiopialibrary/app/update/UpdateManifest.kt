package com.ethiopialibrary.app.update

/**
 * The release keystore's own certificate SHA-256 (public - a fingerprint is
 * not a secret; safe to embed and verify with `keytool -list`, see
 * MAINTAINER.md). Android already enforces same-signature + no-downgrade on
 * every install, so this pin's job is narrower: refuse to even *offer* an
 * install whose APK wasn't signed by this exact key, before the user ever
 * sees a system dialog.
 */
const val PINNED_RELEASE_CERT_SHA256 = "E3EEF8A39B88BC91881F68CF6CAF426001F9C9E95C7F458F05DC87201653B0AD"

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notesAm: String?,
    val notesAr: String?,
    val notesEn: String?,
)

/** A downloaded-and-verified APK sitting in cache, ready for the installer to install (Wave 4 item 22). */
data class UpdateReadyInfo(
    val versionCode: Int,
    val versionName: String,
    val apkPath: String,
)

sealed interface ManifestParseResult {
    data class Valid(val manifest: UpdateManifest) : ManifestParseResult
    data object Malformed : ManifestParseResult
    data object InsecureUrl : ManifestParseResult
    data object NotNewer : ManifestParseResult
}

/**
 * Parses and validates a fetched `latest.json` against the currently running
 * build. Rejects anything malformed, an apkUrl that isn't https (MITM
 * protection alongside the sha256/cert checks below), or a manifest that
 * isn't actually newer than [currentVersionCode] - a stale or compromised
 * manifest replaying an old version must never look "worthy".
 */
fun parseUpdateManifest(json: Map<String, Any?>, currentVersionCode: Int): ManifestParseResult {
    val versionCode = (json["versionCode"] as? Number)?.toInt() ?: return ManifestParseResult.Malformed
    val versionName = json["versionName"] as? String ?: return ManifestParseResult.Malformed
    val apkUrl = json["apkUrl"] as? String ?: return ManifestParseResult.Malformed
    val sha256 = json["sha256"] as? String ?: return ManifestParseResult.Malformed
    if (!apkUrl.startsWith("https://")) return ManifestParseResult.InsecureUrl
    if (versionCode <= currentVersionCode) return ManifestParseResult.NotNewer
    return ManifestParseResult.Valid(
        UpdateManifest(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            notesAm = json["notes_am"] as? String,
            notesAr = json["notes_ar"] as? String,
            notesEn = json["notes_en"] as? String,
        ),
    )
}

/** True when the downloaded APK's own digest matches what the manifest declared. */
fun sha256Matches(manifest: UpdateManifest, computedSha256: String): Boolean =
    normalizeHex(manifest.sha256) == normalizeHex(computedSha256)

/** True when a computed signing-certificate digest matches [PINNED_RELEASE_CERT_SHA256]. */
fun certMatchesPinnedRelease(certSha256: String): Boolean =
    normalizeHex(certSha256) == normalizeHex(PINNED_RELEASE_CERT_SHA256)

private fun normalizeHex(value: String): String = value.replace(":", "").lowercase()
