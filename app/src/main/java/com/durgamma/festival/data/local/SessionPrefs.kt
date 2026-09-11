package com.durgamma.festival.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * G3 lightweight session store (plain SharedPreferences — nothing sensitive).
 * Holds the current event id + last-used donation list sort/filter.
 * G6 keeps the Sarvam API key in EncryptedSharedPreferences separately.
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

    companion object {
        private const val FILE = "durgamma_prefs"
        private const val KEY_EVENT = "current_event_id"
        private const val KEY_SORT = "donation_sort"
        private const val KEY_STATUS_FILTER = "donation_status_filter"

        const val SORT_NEWEST = "newest"
        const val SORT_OLDEST = "oldest"
        const val SORT_AMOUNT_DESC = "amount_desc"
        const val SORT_AMOUNT_ASC = "amount_asc"
        const val SORT_NAME_ASC = "name_asc"
    }
}
