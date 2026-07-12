package com.ethiopialibrary.app.sync

import com.ethiopialibrary.app.data.SettingKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain-Kotlin config-from-cloud parsing - no Firestore/Robolectric needed. */
class RemoteDirectivesTest {

    // ---------- parseRemoteDirectives (fetched-doc parsing) ----------

    @Test
    fun `a null doc (never fetched or a failed fetch) yields compiled defaults`() {
        val d = parseRemoteDirectives(null)

        assertNull(d.announcementAm)
        assertNull(d.announcementId)
        assertNull(d.updateManifestUrl)
        assertTrue(d.updateCheckEnabled)
        assertTrue(d.debouncedBackupEnabled)
        assertNull(d.minSupportedVersionCode)
    }

    @Test
    fun `a well-formed doc is parsed field by field`() {
        val doc = mapOf<String, Any?>(
            "announcement_am" to "የቤተ መጻሕፍት ይዘጋል",
            "announcement_ar" to "ستغلق المكتبة",
            "announcement_en" to "The library will be closed",
            "announcementId" to "2026-07-holiday",
            "updateManifestUrl" to "https://example.com/latest.json",
            "updateCheckEnabled" to false,
            "debouncedBackupEnabled" to false,
            "minSupportedVersionCode" to 14L,
        )

        val d = parseRemoteDirectives(doc)

        assertEquals("የቤተ መጻሕፍት ይዘጋል", d.announcementAm)
        assertEquals("ستغلق المكتبة", d.announcementAr)
        assertEquals("The library will be closed", d.announcementEn)
        assertEquals("2026-07-holiday", d.announcementId)
        assertEquals("https://example.com/latest.json", d.updateManifestUrl)
        assertFalse(d.updateCheckEnabled)
        assertFalse(d.debouncedBackupEnabled)
        assertEquals(14, d.minSupportedVersionCode)
    }

    @Test
    fun `an unknown extra field in the doc is harmless`() {
        val doc = mapOf<String, Any?>("someFutureField" to "whatever", "announcementId" to "x")

        val d = parseRemoteDirectives(doc)

        assertEquals("x", d.announcementId)
    }

    @Test
    fun `a wrong-typed known field falls back to the compiled default instead of crashing`() {
        val doc = mapOf<String, Any?>(
            "updateCheckEnabled" to "yes", // should be a Boolean
            "minSupportedVersionCode" to "not-a-number",
            "announcementId" to 12345L, // should be a String
        )

        val d = parseRemoteDirectives(doc)

        assertTrue(d.updateCheckEnabled) // compiled default
        assertNull(d.minSupportedVersionCode)
        assertNull(d.announcementId)
    }

    // ---------- remoteDirectivesFromSettings (cached-settings reconstruction) ----------

    @Test
    fun `no cached settings (never fetched) reconstructs compiled defaults`() {
        val d = remoteDirectivesFromSettings(emptyMap())

        assertNull(d.announcementId)
        assertTrue(d.updateCheckEnabled)
        assertTrue(d.debouncedBackupEnabled)
        assertNull(d.minSupportedVersionCode)
    }

    @Test
    fun `cached settings round-trip back into typed fields`() {
        val raw = mapOf(
            SettingKeys.REMOTE_ANNOUNCEMENT_AM to "አስታውቅ",
            SettingKeys.REMOTE_ANNOUNCEMENT_ID to "ann-1",
            SettingKeys.REMOTE_UPDATE_CHECK_ENABLED to "false",
            SettingKeys.REMOTE_DEBOUNCED_BACKUP_ENABLED to "false",
            SettingKeys.REMOTE_MIN_SUPPORTED_VERSION_CODE to "15",
        )

        val d = remoteDirectivesFromSettings(raw)

        assertEquals("አስታውቅ", d.announcementAm)
        assertEquals("ann-1", d.announcementId)
        assertFalse(d.updateCheckEnabled)
        assertFalse(d.debouncedBackupEnabled)
        assertEquals(15, d.minSupportedVersionCode)
    }

    // ---------- announcementText ----------

    @Test
    fun `no announcement configured shows nothing`() {
        assertNull(announcementText(RemoteDirectives(), "am"))
    }

    @Test
    fun `picks the text for the current language`() {
        val d = RemoteDirectives(
            announcementAm = "አማርኛ", announcementAr = "عربي", announcementEn = "English",
            announcementId = "x",
        )

        assertEquals("English", announcementText(d, "en"))
        assertEquals("عربي", announcementText(d, "ar"))
        assertEquals("አማርኛ", announcementText(d, "am"))
    }

    @Test
    fun `falls back to Amharic then English then Arabic when the current language is blank`() {
        val amOnly = RemoteDirectives(announcementAm = "አማርኛ", announcementId = "x")
        assertEquals("አማርኛ", announcementText(amOnly, "en"))

        val enOnly = RemoteDirectives(announcementEn = "English", announcementId = "x")
        assertEquals("English", announcementText(enOnly, "ar"))
    }

    // ---------- updateRequired ----------

    @Test
    fun `no minimum configured never requires an update`() {
        assertFalse(updateRequired(RemoteDirectives(), currentVersionCode = 1))
    }

    @Test
    fun `running build older than the configured minimum requires an update`() {
        val d = RemoteDirectives(minSupportedVersionCode = 20)
        assertTrue(updateRequired(d, currentVersionCode = 14))
        assertFalse(updateRequired(d, currentVersionCode = 20))
        assertFalse(updateRequired(d, currentVersionCode = 21))
    }
}
