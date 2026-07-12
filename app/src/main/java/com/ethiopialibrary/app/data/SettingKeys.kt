package com.ethiopialibrary.app.data

object SettingKeys {
    const val LOAN_PERIOD_DAYS = "loan_period_days"
    const val NEXT_COPY_SEQ = "next_copy_seq"
    const val NEXT_MEMBER_SEQ = "next_member_seq"
    const val LAST_SYNC_AT = "last_sync_at"
    const val LAST_SYNC_RESULT = "last_sync_result"
    const val BACKUP_NUDGE_DISMISSED_DAY = "backup_nudge_dismissed_day"
    const val STAFF_PIN_HASH = "staff_pin_hash"
    const val MAX_BOOKS_PER_MEMBER = "max_books_per_member"
    const val DUE_SOON_DAYS = "due_soon_days"
    const val CALENDAR_MODE = "calendar_mode"

    // Config-from-cloud cache (see sync/RemoteDirectives.kt) - refreshed after
    // every successful backup drain from the `remote/directives` Firestore
    // doc. Deliberately separate from the `config` collection restore()
    // writes into settings, so a restore can never smuggle a console
    // override in as if it were an operator setting.
    const val REMOTE_ANNOUNCEMENT_AM = "remote_announcement_am"
    const val REMOTE_ANNOUNCEMENT_AR = "remote_announcement_ar"
    const val REMOTE_ANNOUNCEMENT_EN = "remote_announcement_en"
    const val REMOTE_ANNOUNCEMENT_ID = "remote_announcement_id"
    const val REMOTE_ANNOUNCEMENT_DISMISSED_ID = "remote_announcement_dismissed_id"
    const val REMOTE_UPDATE_MANIFEST_URL = "remote_update_manifest_url"
    const val REMOTE_UPDATE_CHECK_ENABLED = "remote_update_check_enabled"
    const val REMOTE_DEBOUNCED_BACKUP_ENABLED = "remote_debounced_backup_enabled"
    const val REMOTE_MIN_SUPPORTED_VERSION_CODE = "remote_min_supported_version_code"
}
