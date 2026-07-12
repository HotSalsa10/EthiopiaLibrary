package com.ethiopialibrary.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure parsing/validation for a fetched latest.json - no network, no Android framework. */
class UpdateManifestTest {

    private val validJson = mapOf<String, Any?>(
        "versionCode" to 15L,
        "versionName" to "2.0.1",
        "apkUrl" to "https://example.com/releases/latest/download/app-release.apk",
        "sha256" to "abc123",
        "notes_am" to "ማሻሻያ",
        "notes_ar" to "تحديث",
        "notes_en" to "Update",
    )

    @Test
    fun `a well-formed newer manifest parses as valid`() {
        val result = parseUpdateManifest(validJson, currentVersionCode = 14)

        assertTrue(result is ManifestParseResult.Valid)
        val manifest = (result as ManifestParseResult.Valid).manifest
        assertEquals(15, manifest.versionCode)
        assertEquals("2.0.1", manifest.versionName)
        assertEquals("https://example.com/releases/latest/download/app-release.apk", manifest.apkUrl)
        assertEquals("abc123", manifest.sha256)
        assertEquals("ማሻሻያ", manifest.notesAm)
        assertEquals("تحديث", manifest.notesAr)
        assertEquals("Update", manifest.notesEn)
    }

    @Test
    fun `missing required fields are malformed`() {
        assertTrue(parseUpdateManifest(validJson - "versionCode", 14) is ManifestParseResult.Malformed)
        assertTrue(parseUpdateManifest(validJson - "versionName", 14) is ManifestParseResult.Malformed)
        assertTrue(parseUpdateManifest(validJson - "apkUrl", 14) is ManifestParseResult.Malformed)
        assertTrue(parseUpdateManifest(validJson - "sha256", 14) is ManifestParseResult.Malformed)
    }

    @Test
    fun `wrong-typed required fields are malformed`() {
        val wrongTyped = validJson + mapOf("versionCode" to "not-a-number")
        assertTrue(parseUpdateManifest(wrongTyped, 14) is ManifestParseResult.Malformed)
    }

    @Test
    fun `an http apkUrl is rejected as insecure`() {
        val httpJson = validJson + mapOf("apkUrl" to "http://example.com/app-release.apk")
        assertTrue(parseUpdateManifest(httpJson, 14) is ManifestParseResult.InsecureUrl)
    }

    @Test
    fun `a manifest at or below the current version is not newer`() {
        assertTrue(parseUpdateManifest(validJson, currentVersionCode = 15) is ManifestParseResult.NotNewer)
        assertTrue(parseUpdateManifest(validJson, currentVersionCode = 16) is ManifestParseResult.NotNewer)
    }

    // ---------- sha256Matches ----------

    @Test
    fun `sha256Matches is case-insensitive`() {
        val manifest = UpdateManifest(15, "2.0.1", "https://x/y.apk", sha256 = "ABC123", null, null, null)
        assertTrue(sha256Matches(manifest, "abc123"))
        assertTrue(sha256Matches(manifest, "ABC123"))
    }

    @Test
    fun `sha256Matches rejects a mismatch`() {
        val manifest = UpdateManifest(15, "2.0.1", "https://x/y.apk", sha256 = "abc123", null, null, null)
        assertFalse(sha256Matches(manifest, "def456"))
    }

    // ---------- certMatchesPinnedRelease ----------

    @Test
    fun `certMatchesPinnedRelease accepts the pinned digest regardless of colon or case formatting`() {
        assertTrue(certMatchesPinnedRelease(PINNED_RELEASE_CERT_SHA256))
        assertTrue(certMatchesPinnedRelease(PINNED_RELEASE_CERT_SHA256.lowercase()))
        assertTrue(
            certMatchesPinnedRelease(
                "E3:EE:F8:A3:9B:88:BC:91:88:1F:68:CF:6C:AF:42:60:01:F9:C9:E9:5C:7F:45:8F:05:DC:87:20:16:53:B0:AD",
            ),
        )
    }

    @Test
    fun `certMatchesPinnedRelease rejects a different cert`() {
        assertFalse(certMatchesPinnedRelease("0000000000000000000000000000000000000000000000000000000000000000"))
    }

    // ---------- updateAvailable ----------

    @Test
    fun `no ready info means no update available`() {
        assertFalse(updateAvailable(null, currentVersionCode = 14))
    }

    @Test
    fun `a ready version newer than the running build is available`() {
        val ready = UpdateReadyInfo(versionCode = 15, versionName = "2.0.1", apkPath = "/cache/update-15.apk")
        assertTrue(updateAvailable(ready, currentVersionCode = 14))
    }

    @Test
    fun `a ready version at or below the running build is not available (already installed)`() {
        val ready = UpdateReadyInfo(versionCode = 14, versionName = "2.0.0", apkPath = "/cache/update-14.apk")
        assertFalse(updateAvailable(ready, currentVersionCode = 14))
        assertFalse(updateAvailable(ready, currentVersionCode = 15))
    }
}
