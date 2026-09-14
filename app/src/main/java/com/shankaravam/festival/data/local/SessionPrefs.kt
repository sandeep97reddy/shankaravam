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

    /**
     * Phase 2 reactive voice settings (RC1 remedy). Every voice picker collects
     * these — never snapshots prefs into `remember {}` — so a change in
     * Settings is visible in the Announcement queue instantly and vice versa.
     */
    private val _sarvamSpeakerFlow = MutableStateFlow(sarvamSpeaker)
    val sarvamSpeakerFlow: StateFlow<String> = _sarvamSpeakerFlow.asStateFlow()

    private val _nativeTtsVoiceFlow = MutableStateFlow(nativeTtsVoice)
    val nativeTtsVoiceFlow: StateFlow<String?> = _nativeTtsVoiceFlow.asStateFlow()

    private val _nativeTtsSpeedFlow = MutableStateFlow(nativeTtsSpeed)
    val nativeTtsSpeedFlow: StateFlow<Float> = _nativeTtsSpeedFlow.asStateFlow()

    private val _playTempleChimeFlow = MutableStateFlow(playTempleChime)
    val playTempleChimeFlow: StateFlow<Boolean> = _playTempleChimeFlow.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_ENABLED, true))
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
        _hapticFeedbackEnabled.value = enabled
    }

    private val _voiceEngineModeFlow =
        MutableStateFlow(com.shankaravam.festival.domain.model.voiceModeOf(prefs.getString(KEY_ENGINE_MODE, null)))
    val voiceEngineModeFlow: StateFlow<com.shankaravam.festival.domain.model.VoiceEngineMode> =
        _voiceEngineModeFlow.asStateFlow()

    private val _gatewayBaseUrlFlow = MutableStateFlow(
        when (val stored = prefs.getString(KEY_GATEWAY_URL, null)) {
            null -> DEFAULT_GATEWAY_URL
            "OFFLINE" -> ""
            else -> stored.trim().trimEnd('/')
        }
    )
    val gatewayBaseUrlFlow: StateFlow<String> = _gatewayBaseUrlFlow.asStateFlow()

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

    /**
     * F4 member-doc counter (pure logic in [pickSyncCounter]): entered counter
     * wins; else the Google display name; else null (omitted — lets
     * `resolveMemberName` fall through to displayName/email instead of a
     * `Counter-XXXX` placeholder). Replaces [rawCounterName] for all member
     * writes; the ledger keeps its own attribution.
     */
    fun syncCounterName(): String? =
        pickSyncCounter(
            _counterName.value.trim().ifBlank { prefs.getString(KEY_COUNTER, "")?.trim() },
            googleDisplayName
        )

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

    /** Seconds of silence between queue announcements (plan §11: 2s/5s/10s; default 2s). */
    var queueGapSeconds: Int
        get() = prefs.getInt(KEY_QUEUE_GAP, 2).coerceIn(0, 30)
        set(value) = prefs.edit().putInt(KEY_QUEUE_GAP, value).apply()

    var queueSort: String
        get() = prefs.getString(KEY_QUEUE_SORT, SORT_NEWEST) ?: SORT_NEWEST
        set(value) = prefs.edit().putString(KEY_QUEUE_SORT, value).apply()

    var queueLanguage: String
        get() = prefs.getString(KEY_QUEUE_LANG, LANG_TELUGU) ?: LANG_TELUGU
        set(value) = prefs.edit().putString(KEY_QUEUE_LANG, value).apply()

    var nativeTtsVoice: String?
        get() = prefs.getString(KEY_NATIVE_VOICE, null)
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_NATIVE_VOICE) else putString(KEY_NATIVE_VOICE, value) }.apply()
            _nativeTtsVoiceFlow.value = value
        }

    var nativeTtsSpeed: Float
        get() = prefs.getFloat(KEY_NATIVE_SPEED, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_NATIVE_SPEED, value).apply()
            _nativeTtsSpeedFlow.value = value
        }

    var queueRosterMode: Boolean
        get() = prefs.getBoolean(KEY_QUEUE_ROSTER_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_QUEUE_ROSTER_MODE, value).apply()

    /** Temple bell before announcements (feature #4): on by default. */
    var playTempleChime: Boolean
        get() = prefs.getBoolean(KEY_TEMPLE_CHIME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TEMPLE_CHIME, value).apply()
            _playTempleChimeFlow.value = value
        }

    var queueFestivalPreset: String
        get() = prefs.getString(KEY_QUEUE_PRESET, "VINAYAKA_CHAVITHI") ?: "VINAYAKA_CHAVITHI"
        set(value) = prefs.edit().putString(KEY_QUEUE_PRESET, value).apply()

    /** DEV holder for the Sarvam key so G4 is testable without G6 cloud. */
    var sarvamApiKey: String
        get() = prefs.getString(KEY_SARVAM, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SARVAM, value.trim()).apply()

    /**
     * T0.5: fresh installs (no stored key) resolve to Shubh. Stored choices
     * pass through normalization untouched — never force-migrated.
     */
    var sarvamSpeaker: String
        get() = com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(prefs.getString(KEY_SPEAKER, "shubh"))
        set(value) {
            val norm = com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(value)
            prefs.edit().putString(KEY_SPEAKER, norm).apply()
            _sarvamSpeakerFlow.value = norm
        }

    /**
     * Phase 2 explicit engine mode (RC5). Default SARVAM_CLOUD preserves the
     * pre-Phase-2 behavior (cloud cache first, native fallback). OFFLINE_NATIVE
     * forces device voice + human imports with zero cloud calls.
     */
    var voiceEngineMode: com.shankaravam.festival.domain.model.VoiceEngineMode
        get() = com.shankaravam.festival.domain.model.voiceModeOf(prefs.getString(KEY_ENGINE_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_ENGINE_MODE, value.name).apply()
            _voiceEngineModeFlow.value = value
        }

    /**
     * Phase 1 speaker-aware cache: true once pre-speaker legacy files have
     * been attributed to the active speaker (one-time rename migration,
     * guarded so it runs exactly once per install).
     */
    var audioCacheV2Migrated: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_V2, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_V2, value).apply()

    /**
     * Phase-3 temple gateway base URL. Defaults to the deployed Cloudflare Worker gateway
     * (`DEFAULT_GATEWAY_URL`) for out-of-the-box cloud voice (Sarvam AI) and receipt photos.
     * Stored as "OFFLINE" if explicitly disconnected by the user.
     */
    var gatewayBaseUrl: String
        get() = when (val stored = prefs.getString(KEY_GATEWAY_URL, null)) {
            null -> DEFAULT_GATEWAY_URL
            "OFFLINE" -> ""
            else -> stored.trim().trimEnd('/')
        }
        set(value) {
            val cleaned = value.trim().trimEnd('/')
            val toStore = if (cleaned.isBlank()) "OFFLINE" else cleaned
            prefs.edit().putString(KEY_GATEWAY_URL, toStore).apply()
            _gatewayBaseUrlFlow.value = if (cleaned.isBlank()) "" else cleaned
        }

    /**
     * P4 Sarvam budget: 20 cloud generations per 30-min rolling window per
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

    /**
     * Phase 3 transparency: calls consumed in the CURRENT Sarvam window.
     * Returns 0 when the window has rolled (or never opened) — never throws.
     */
    fun sarvamQuotaUsed(now: Long = System.currentTimeMillis()): Int {
        val start = prefs.getLong(KEY_SARVAM_WINDOW, 0L)
        if (now - start >= SARVAM_WINDOW_MILLIS) return 0
        return prefs.getInt(KEY_SARVAM_COUNT, 0).coerceIn(0, SARVAM_MAX_CALLS)
    }

    /**
     * Phase 3 transparency: when the current Sarvam window resets and calls
     * succeed again. Returns [now] when no window is open. Never throws.
     */
    fun sarvamQuotaResetAt(now: Long = System.currentTimeMillis()): Long {
        val resetAt = prefs.getLong(KEY_SARVAM_WINDOW, 0L) + SARVAM_WINDOW_MILLIS
        return if (resetAt <= now) now else resetAt
    }

    /**
     * F5 shared-voice bookkeeping: last successful shared-key apply (pill),
     * last pull attempt (15-min throttle), and the explicit offline lock (the
     * user's Offline chip tap wins over auto-pull mode flips).
     */
    fun lastVoiceSyncAt(): Long = prefs.getLong(KEY_VOICE_SYNCED_AT, 0L)

    fun setLastVoiceSyncAt(millis: Long) {
        prefs.edit().putLong(KEY_VOICE_SYNCED_AT, millis).apply()
    }

    fun lastVoicePullAt(): Long = prefs.getLong(KEY_VOICE_PULL_AT, 0L)

    fun setLastVoicePullAt(millis: Long) {
        prefs.edit().putLong(KEY_VOICE_PULL_AT, millis).apply()
    }

    var voiceOfflineLocked: Boolean
        get() = prefs.getBoolean(KEY_VOICE_OFFLINE_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_OFFLINE_LOCK, value).apply()

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
        set(value) {
            prefs.edit().putBoolean(KEY_SYNC_ENABLED, value).apply()
            _cloudSyncFlow.value = value
        }

    /**
     * F3: reactive mirror of [cloudSyncEnabled] so the foreground listener can
     * attach/detach without polling a plain var.
     */
    private val _cloudSyncFlow = MutableStateFlow(prefs.getBoolean(KEY_SYNC_ENABLED, false))
    val cloudSyncEnabledFlow: StateFlow<Boolean> = _cloudSyncFlow.asStateFlow()

    fun lastSyncMillis(eventId: String): Long =
        prefs.getLong(KEY_LAST_SYNC + eventId, 0L)

    fun setLastSyncMillis(eventId: String, millis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC + eventId, millis).apply()
    }

    /**
     * F3 corrections cursor (correction docs carry `createdAt`, not
     * `updatedAt` — they need their own watermark). Monotonic: only advances.
     */
    fun lastCorrectionMillis(eventId: String): Long =
        prefs.getLong(KEY_LAST_CORR + eventId, 0L)

    fun setLastCorrectionMillis(eventId: String, millis: Long) {
        if (millis > lastCorrectionMillis(eventId)) {
            prefs.edit().putLong(KEY_LAST_CORR + eventId, millis).apply()
        }
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
     * F4: revoked is checked FIRST — a revoked admin keeps no head powers.
     * Callers must still gate writes on [myStatus] (S3).
     */
    fun myRole(eventId: String): String {
        if (myStatus(eventId) == STATUS_REVOKED) return ROLE_MEMBER
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

    /**
     * Phase 1 local-scrub purge (Step 4). Removes every per-event key so a
     * deleted local festival leaves no role/status/code/sync residue.
     * currentEventId retargeting is the caller's job (Step 5).
     */
    fun clearEventPrefs(eventId: String) {
        val code = shareCodeFor(eventId)
        with(prefs.edit()) {
            remove(KEY_LAST_SYNC + eventId)
            remove(KEY_STATUS + eventId)
            remove(KEY_CLOUD + eventId)
            remove(KEY_ROLE + eventId)
            remove(KEY_CODE + eventId)
            if (code != null) remove(KEY_CODE_REV + code.uppercase())
            apply()
        }
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
        private const val KEY_ENGINE_MODE = "voice_engine_mode"
        private const val KEY_SARVAM_WINDOW = "sarvam_window_start"
        private const val KEY_SARVAM_COUNT = "sarvam_window_count"
        private const val KEY_AUDIO_V2 = "audio_cache_v2_migrated"
        private const val KEY_GATEWAY_URL = "gateway_base_url"

        /** Phase-3 default Cloudflare Worker media gateway. */
        const val DEFAULT_GATEWAY_URL = "https://shankaravam-gateway.kingsandeepreddy1.workers.dev"

        /** P4 budget: 20 Sarvam calls per 30 minutes per device. */
        const val SARVAM_MAX_CALLS = 20
        const val SARVAM_WINDOW_MILLIS = 30L * 60L * 1000L

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
        private const val KEY_HAPTIC_ENABLED = "haptic_feedback_enabled"
        private const val KEY_QUEUE_PRESET = "queue_festival_preset"
        private const val KEY_GOOGLE_NAME = "google_display_name"
        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_LAST_SYNC = "last_sync_"
        private const val KEY_LAST_CORR = "last_corr_"
        private const val KEY_VOICE_SYNCED_AT = "voice_key_synced_at"
        private const val KEY_VOICE_PULL_AT = "voice_key_pull_at"
        private const val KEY_VOICE_OFFLINE_LOCK = "voice_offline_locked"
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

/**
 * F4 member-doc counter pick (pure + unit-tested): entered counter wins, else
 * the Google display name, else null (omitted from the member doc so
 * `resolveMemberName` falls through to displayName/email — never a
 * `Counter-XXXX` placeholder as the primary roster title).
 */
fun pickSyncCounter(counter: String?, googleDisplayName: String?): String? {
    if (!counter.isNullOrBlank()) return counter.trim()
    if (!googleDisplayName.isNullOrBlank()) return googleDisplayName.trim()
    return null
}
