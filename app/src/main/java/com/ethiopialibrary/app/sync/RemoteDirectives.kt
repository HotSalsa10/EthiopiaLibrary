package com.ethiopialibrary.app.sync

import com.ethiopialibrary.app.data.SettingKeys

/**
 * Config-from-cloud: an operator-invisible Firestore doc (`remote/directives`)
 * the developer edits from the Console to push announcements and kill
 * switches without a device release - deliberately not the `config`
 * collection, which restore() writes straight into operator settings
 * (mixing the two would let a restore smuggle a console override in).
 */
data class RemoteDirectives(
    val announcementAm: String? = null,
    val announcementAr: String? = null,
    val announcementEn: String? = null,
    val announcementId: String? = null,
    val updateManifestUrl: String? = null,
    val updateCheckEnabled: Boolean = true,
    val debouncedBackupEnabled: Boolean = true,
    val minSupportedVersionCode: Int? = null,
)

/**
 * A present, correctly-typed field in [doc] is kept; anything missing or the
 * wrong type falls back to the compiled default rather than crashing a
 * backup drain over a bad Console edit. A null [doc] (nothing was ever
 * fetched) is the all-defaults case too.
 */
fun parseRemoteDirectives(doc: Map<String, Any?>?): RemoteDirectives {
    val defaults = RemoteDirectives()
    if (doc == null) return defaults
    return RemoteDirectives(
        announcementAm = doc["announcement_am"] as? String,
        announcementAr = doc["announcement_ar"] as? String,
        announcementEn = doc["announcement_en"] as? String,
        announcementId = doc["announcementId"] as? String,
        updateManifestUrl = doc["updateManifestUrl"] as? String,
        updateCheckEnabled = doc["updateCheckEnabled"] as? Boolean ?: defaults.updateCheckEnabled,
        debouncedBackupEnabled = doc["debouncedBackupEnabled"] as? Boolean ?: defaults.debouncedBackupEnabled,
        minSupportedVersionCode = (doc["minSupportedVersionCode"] as? Number)?.toInt(),
    )
}

/** Rebuilds [RemoteDirectives] from the cached settings rows; a missing key is the compiled default. */
fun remoteDirectivesFromSettings(raw: Map<String, String?>): RemoteDirectives {
    val defaults = RemoteDirectives()
    return RemoteDirectives(
        announcementAm = raw[SettingKeys.REMOTE_ANNOUNCEMENT_AM],
        announcementAr = raw[SettingKeys.REMOTE_ANNOUNCEMENT_AR],
        announcementEn = raw[SettingKeys.REMOTE_ANNOUNCEMENT_EN],
        announcementId = raw[SettingKeys.REMOTE_ANNOUNCEMENT_ID],
        updateManifestUrl = raw[SettingKeys.REMOTE_UPDATE_MANIFEST_URL],
        updateCheckEnabled = raw[SettingKeys.REMOTE_UPDATE_CHECK_ENABLED]?.toBooleanStrictOrNull()
            ?: defaults.updateCheckEnabled,
        debouncedBackupEnabled = raw[SettingKeys.REMOTE_DEBOUNCED_BACKUP_ENABLED]?.toBooleanStrictOrNull()
            ?: defaults.debouncedBackupEnabled,
        minSupportedVersionCode = raw[SettingKeys.REMOTE_MIN_SUPPORTED_VERSION_CODE]?.toIntOrNull(),
    )
}

/**
 * Which announcement text to show, if any: the current UI language, falling
 * back to Amharic then English then Arabic so a translation gap never hides
 * an announcement outright. Null when there's nothing to show - no
 * announcement configured, or every language field is blank.
 */
fun announcementText(directives: RemoteDirectives, languageTag: String): String? {
    if (directives.announcementId == null) return null
    val byLanguage = when (languageTag) {
        "am" -> directives.announcementAm
        "ar" -> directives.announcementAr
        "en" -> directives.announcementEn
        else -> null
    }
    return listOfNotNull(
        byLanguage,
        directives.announcementAm,
        directives.announcementEn,
        directives.announcementAr,
    ).firstOrNull { it.isNotBlank() }
}

/** True when the running build is older than the Console's configured minimum. */
fun updateRequired(directives: RemoteDirectives, currentVersionCode: Int): Boolean {
    val min = directives.minSupportedVersionCode ?: return false
    return currentVersionCode < min
}
