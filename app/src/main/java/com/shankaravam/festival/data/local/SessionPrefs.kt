package com.shankaravam.festival.data.local

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

    private val _appLanguage = MutableStateFlow(prefs.getString(KEY_APP_LANG, LANG_ENGLISH) ?: LANG_ENGLISH)
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _counterName = MutableStateFlow(prefs.getString(KEY_COUNTER, "") ?: "")
    val counterName: StateFlow<String> = _counterName.asStateFlow()

    fun setCurrentEventId(id: String?) {
        prefs.edit().apply { if (id == null) remove(KEY_EVENT) else putString(KEY_EVENT, id) }.apply()
        _currentEventId.value = id
    }

    fun setCounterName(name: String) {
        val trimmed = name.trim()
        prefs.edit().putString(KEY_COUNTER, trimmed).apply()
        _counterName.value = trimmed
    }

    var googleDisplayName: String?
        get() = prefs.getString(KEY_GOOGLE_NAME, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_GOOGLE_NAME) else putString(KEY_GOOGLE_NAME, value.trim())
        }.apply()

    var googleEmail: String?
        get() = prefs.getString(KEY_GOOGLE_EMAIL, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_GOOGLE_EMAIL) else putString(KEY_GOOGLE_EMAIL, value.trim())
        }.apply()

    /**
     * Attribution for every ledger row:
     * 1. If signed in with Google: "Counter • Name (email)" or "Name (email)"
     * 2. If signed out with counter name: "Counter Name"
     * 3. Offline fallback: "Counter-last4(deviceId)"
     */
    fun attributionName(): String {
        val counter = _counterName.value.trim().ifBlank { prefs.getString(KEY_COUNTER, "")?.trim() ?: "" }
        val gName = googleDisplayName?.takeIf { it.isNotBlank() }
        val gEmail = googleEmail?.takeIf { it.isNotBlank() }

        val userLabel = when {
            gName != null && gEmail != null -> "$gName ($gEmail)"
            gName != null -> gName
            gEmail != null -> gEmail
            else -> null
        }

        return when {
            counter.isNotBlank() && userLabel != null -> "$counter • $userLabel"
            counter.isNotBlank() -> counter
            userLabel != null -> userLabel
            else -> "Counter-${deviceId.takeLast(4)}"
        }
    }

    /**
     * Team-visible counter label for the presence heartbeat (no email —
     * email/displayName travel in their own member-doc fields). Use this for
     * every Firestore write; [attributionName] stays local-ledger-only.
     */
    fun rawCounterName(): String {
        val counter = _counterName.value.trim().ifBlank { prefs.getString(KEY_COUNTER, "")?.trim() ?: "" }
        return counter.ifBlank { "Counter-${deviceId.takeLast(4)}" }
    }

    fun setAppLanguage(lang: String) {
        prefs.edit().putString(KEY_APP_LANG, lang).apply()
        _appLanguage.value = lang
    }

    fun toggleAppLanguage() {
        val next = if (_appLanguage.value == LANG_TELUGU) LANG_ENGLISH else LANG_TELUGU
        setAppLanguage(next)
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

    var nativeTtsVoice: String?
        get() = prefs.getString(KEY_NATIVE_VOICE, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_NATIVE_VOICE) else putString(KEY_NATIVE_VOICE, value) }.apply()

    var nativeTtsSpeed: Float
        get() = prefs.getFloat(KEY_NATIVE_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_NATIVE_SPEED, value).apply()

    var queueRosterMode: Boolean
        get() = prefs.getBoolean(KEY_QUEUE_ROSTER_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_QUEUE_ROSTER_MODE, value).apply()

    /** Temple bell before announcements (feature #4): on by default. */
    var playTempleChime: Boolean
        get() = prefs.getBoolean(KEY_TEMPLE_CHIME, true)
        set(value) = prefs.edit().putBoolean(KEY_TEMPLE_CHIME, value).apply()

    var queueFestivalPreset: String
        get() = prefs.getString(KEY_QUEUE_PRESET, "VINAYAKA_CHAVITHI") ?: "VINAYAKA_CHAVITHI"
        set(value) = prefs.edit().putString(KEY_QUEUE_PRESET, value).apply()

    /** DEV holder for the Sarvam key so G4 is testable without G6 cloud. */
    var sarvamApiKey: String
        get() = prefs.getString(KEY_SARVAM, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SARVAM, value.trim()).apply()

    var sarvamSpeaker: String
        get() = com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(prefs.getString(KEY_SPEAKER, "priya"))
        set(value) = prefs.edit().putString(KEY_SPEAKER, com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(value)).apply()

    /**
     * P4 Sarvam budget: 10 cloud generations per 45-min rolling window per
     * device. Over budget → caller falls back to native TTS silently.
     */
    fun takeSarvamSlot(now: Long = System.currentTimeMillis()): Boolean {
        val window = com.shankaravam.festival.core.util.RateWindow(
            maxCalls = SARVAM_MAX_CALLS,
            windowMillis = SARVAM_WINDOW_MILLIS,
            windowStart = prefs.getLong(KEY_SARVAM_WINDOW, 0L),
            taken = prefs.getInt(KEY_SARVAM_COUNT, 0)
        )
        val allowed = window.takeSlot(now)
        prefs.edit()
            .putLong(KEY_SARVAM_WINDOW, window.windowStart)
            .putInt(KEY_SARVAM_COUNT, window.taken)
            .apply()
        return allowed
    }

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

    /**
     * Whitelisted-admin flag (ADMIN_HEAD_PLAN S2.3). Owned ONLY by
     * AuthRepository's auth-state listener — never set from ViewModels.
     * Cleared on sign-out (null user). Global across events on purpose; the
     * per-event scope gate lives in [myRole] via [isCloudEvent].
     */
    var isGlobalHeadUser: Boolean
        get() = prefs.getBoolean(KEY_HEAD_USER, false)
        set(value) = prefs.edit().putBoolean(KEY_HEAD_USER, value).apply()

    /**
     * Local membership-status cache per event (plan §6 + ADMIN_HEAD_PLAN).
     * Orthogonal to role: revoke is a status, never a role string (S1 seal).
     * Defaults ACTIVE so pre-cloud offline events keep full counter powers.
     */
    fun myStatus(eventId: String): String =
        prefs.getString(KEY_STATUS + eventId, STATUS_ACTIVE) ?: STATUS_ACTIVE

    fun setMyStatus(eventId: String, status: String) {
        prefs.edit().putString(KEY_STATUS + eventId, status).apply()
    }

    /** True once this event has touched cloud (published or joined). */
    fun isCloudEvent(eventId: String): Boolean =
        prefs.getBoolean(KEY_CLOUD + eventId, false)

    fun markCloudEvent(eventId: String) {
        prefs.edit().putBoolean(KEY_CLOUD + eventId, true).apply()
    }

    /**
     * Local role cache per event (plan §6). Default ORGANIZER keeps offline
     * behavior. The admin override is SCOPED to cloud-joined events: signing
     * in as the whitelisted head never elevates local-only events (Rule #1).
     * Revoke is NOT handled here — callers gate on [myStatus] (S3).
     */
    fun myRole(eventId: String): String {
        if (isGlobalHeadUser && isCloudEvent(eventId)) return ROLE_GLOBAL_HEAD
        return prefs.getString(KEY_ROLE + eventId, ROLE_ORGANIZER) ?: ROLE_ORGANIZER
    }

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

    /**
     * Forget the local invite mapping (feature #2, after the head closes the
     * code). The Invite card flips back to "Create invite code", which mints a
     * FRESH code — a closed cloud code is never silently resurrected.
     */
    fun clearShareCode(eventId: String) {
        val code = shareCodeFor(eventId)
        prefs.edit().remove(KEY_CODE + eventId).apply()
        if (code != null) prefs.edit().remove(KEY_CODE_REV + code.uppercase()).apply()
    }

    companion object {
        private const val FILE = "shankaravam_prefs"
        private const val KEY_EVENT = "current_event_id"
        private const val KEY_COUNTER = "counter_name"
        private const val KEY_APP_LANG = "app_language"
        private const val KEY_SORT = "donation_sort"
        private const val KEY_STATUS_FILTER = "donation_status_filter"
        private const val KEY_QUEUE_GAP = "queue_gap_seconds"
        private const val KEY_QUEUE_SORT = "queue_sort"
        private const val KEY_QUEUE_LANG = "queue_language"
        private const val KEY_SARVAM = "sarvam_api_key"
        private const val KEY_SPEAKER = "sarvam_speaker"
        private const val KEY_SARVAM_WINDOW = "sarvam_window_start"
        private const val KEY_SARVAM_COUNT = "sarvam_window_count"

        /** P4 budget: 10 Sarvam calls per 45 minutes per device. */
        const val SARVAM_MAX_CALLS = 10
        const val SARVAM_WINDOW_MILLIS = 45L * 60L * 1000L

        /** P4 cache ceiling: 300 clips / 20 days, whichever trims first. */
        const val AUDIO_CACHE_MAX_FILES = 300
        const val AUDIO_CACHE_MAX_AGE_DAYS = 20

        /** P4 re-fetch guard: a peer's generation counts as fresh for 30 min. */
        const val AUDIO_META_FRESH_MILLIS = 30L * 60L * 1000L

        /** P4 import guard: refuse absurdly large shared clips. */
        const val AUDIO_IMPORT_MAX_BYTES = 5L * 1024L * 1024L
        private const val KEY_NATIVE_VOICE = "native_tts_voice"
        private const val KEY_NATIVE_SPEED = "native_tts_speed"
        private const val KEY_QUEUE_ROSTER_MODE = "queue_roster_mode"
        private const val KEY_TEMPLE_CHIME = "temple_chime"
        private const val KEY_QUEUE_PRESET = "queue_festival_preset"
        private const val KEY_GOOGLE_NAME = "google_display_name"
        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_LAST_SYNC = "last_sync_"
        private const val KEY_ROLE = "my_role_"
        private const val KEY_STATUS = "member_status_"
        private const val KEY_CLOUD = "cloud_event_"
        private const val KEY_HEAD_USER = "is_global_head_user"
        private const val KEY_CODE = "share_code_"
        private const val KEY_CODE_REV = "share_code_rev_"

        const val ROLE_GLOBAL_HEAD = "global_head"
        const val ROLE_ORGANIZER = "organizer"
        const val ROLE_MEMBER = "member"

        const val STATUS_ACTIVE = "active"
        const val STATUS_PENDING = "pending"
        const val STATUS_REVOKED = "revoked"

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
