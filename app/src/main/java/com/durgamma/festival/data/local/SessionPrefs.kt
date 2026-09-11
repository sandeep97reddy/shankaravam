package com.durgamma.festival.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * G3 lightweight session store (plain SharedPreferences — nothing sensitive).
 * Holds the current event id + last-used donation list sort/filter.
 * G4 adds queue prefs + a DEV Sarvam key holder; G6 migrates the key to
 * EncryptedSharedPreferences + Firestore sync.
 */
class SessionPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _currentEventId = MutableStateFlow(prefs.getString(KEY_EVENT, null))
    val currentEventId: StateFlow<String?> = _currentEventId.asStateFlow()

    fun setCurrentEventId(id: String?) {
        prefs.edit().apply { if (id == null) remove(KEY_EVENT) else putString(KEY_EVENT, id) }.apply()
        _currentEventId.value = id
    }

    var donationSort: String
        get() = prefs.getString(KEY_SORT, SORT_NEWEST) ?: SORT_NEWEST
        set(value) = prefs.edit().putString(KEY_SORT, value).apply()

    var donationStatusFilter: String?
        get() = prefs.getString(KEY_STATUS_FILTER, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_STATUS_FILTER) else putString(KEY_STATUS_FILTER, value) }.apply()

    /** Seconds of silence between queue announcements (plan §11: 2s/5s/10s). */
    var queueGapSeconds: Int
        get() = prefs.getInt(KEY_QUEUE_GAP, 5).coerceIn(0, 30)
        set(value) = prefs.edit().putInt(KEY_QUEUE_GAP, value).apply()

    var queueSort: String
        get() = prefs.getString(KEY_QUEUE_SORT, SORT_NEWEST) ?: SORT_NEWEST
        set(value) = prefs.edit().putString(KEY_QUEUE_SORT, value).apply()

    var queueLanguage: String
        get() = prefs.getString(KEY_QUEUE_LANG, LANG_TELUGU) ?: LANG_TELUGU
        set(value) = prefs.edit().putString(KEY_QUEUE_LANG, value).apply()

    /** DEV holder for the Sarvam key so G4 is testable without G6 cloud. */
    var sarvamApiKey: String
        get() = prefs.getString(KEY_SARVAM, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SARVAM, value.trim()).apply()

    var sarvamSpeaker: String
        get() = prefs.getString(KEY_SPEAKER, "meera") ?: "meera"
        set(value) = prefs.edit().putString(KEY_SPEAKER, value).apply()

    // ---- G6 cloud session (all inert until the user enables Cloud Sync) ----

    /** Stable per-install id used as creator/device attribution (plan §20). */
    var deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE, null)
            if (id.isNullOrBlank()) {
                id = java.util.UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_DEVICE, value).apply()

    var cloudSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_ENABLED, value).apply()

    fun lastSyncMillis(eventId: String): Long =
        prefs.getLong(KEY_LAST_SYNC + eventId, 0L)

    fun setLastSyncMillis(eventId: String, millis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC + eventId, millis).apply()
    }

    /** Local role cache per event (plan §6). Default ORGANIZER keeps offline behavior. */
    fun myRole(eventId: String): String =
        prefs.getString(KEY_ROLE + eventId, ROLE_ORGANIZER) ?: ROLE_ORGANIZER

    fun setMyRole(eventId: String, role: String) {
        prefs.edit().putString(KEY_ROLE + eventId, role).apply()
    }

    /** Share-code → eventId directory for QR/code joins (codes also live in Firestore). */
    fun shareCodeFor(eventId: String): String? =
        prefs.getString(KEY_CODE + eventId, null)

    fun putShareCode(eventId: String, code: String) {
        prefs.edit().putString(KEY_CODE + eventId, code).apply()
    }

    fun eventIdForShareCode(code: String): String? =
        prefs.getString(KEY_CODE_REV + code.uppercase(), null)

    fun putShareCodeReverse(code: String, eventId: String) {
        prefs.edit().putString(KEY_CODE_REV + code.uppercase(), eventId).apply()
    }

    companion object {
        private const val FILE = "durgamma_prefs"
        private const val KEY_EVENT = "current_event_id"
        private const val KEY_SORT = "donation_sort"
        private const val KEY_STATUS_FILTER = "donation_status_filter"
        private const val KEY_QUEUE_GAP = "queue_gap_seconds"
        private const val KEY_QUEUE_SORT = "queue_sort"
        private const val KEY_QUEUE_LANG = "queue_language"
        private const val KEY_SARVAM = "sarvam_api_key"
        private const val KEY_SPEAKER = "sarvam_speaker"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_LAST_SYNC = "last_sync_"
        private const val KEY_ROLE = "my_role_"
        private const val KEY_CODE = "share_code_"
        private const val KEY_CODE_REV = "share_code_rev_"

        const val ROLE_GLOBAL_HEAD = "global_head"
        const val ROLE_ORGANIZER = "organizer"
        const val ROLE_MEMBER = "member"

        const val SORT_NEWEST = "newest"
        const val SORT_OLDEST = "oldest"
        const val SORT_AMOUNT_DESC = "amount_desc"
        const val SORT_AMOUNT_ASC = "amount_asc"
        const val SORT_NAME_ASC = "name_asc"

        const val LANG_TELUGU = "te"
        const val LANG_ENGLISH = "en"
        const val LANG_BILINGUAL = "te-en"
    }
}
