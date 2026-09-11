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
