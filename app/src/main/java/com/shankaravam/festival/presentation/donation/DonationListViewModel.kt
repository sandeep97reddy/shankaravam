package com.shankaravam.festival.presentation.donation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@Immutable
data class DonationListUiState(
    val donations: List<Donation> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val statusFilter: DonationStatus? = null,
    val tagFilter: String? = null,
    val sort: String = SessionPrefs.SORT_NEWEST,
    val availableTags: List<String> = emptyList(),
    val hasEvent: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class DonationListViewModel(container: AppContainer) : ViewModel() {
    private val prefs: SessionPrefs = container.sessionPrefs
    private val repo = container.donationRepository

    private val query = MutableStateFlow("")
    private val statusFilter = MutableStateFlow<DonationStatus?>(
        prefs.donationStatusFilter?.let { runCatching { DonationStatus.valueOf(it) }.getOrNull() }
    )
    private val tagFilter = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(prefs.donationSort)

    val uiState: StateFlow<DonationListUiState> =
        prefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(DonationListUiState())
            } else {
                combine(
                    repo.observeForEvent(eventId),
                    query, statusFilter, tagFilter, sort
                ) { donations, q, status, tag, sortKey ->
                    val filtered = donations
                        .asSequence()
                        .filter { d ->
                            (status == null || d.status == status) &&
                                (tag == null || tag in d.tags) &&
                                (q.isBlank() ||
                                    d.donorName.contains(q, ignoreCase = true) ||
                                    (d.itemDescription?.contains(q, ignoreCase = true) == true))
                        }
                        .toList()
                        .sortedWith(comparatorFor(sortKey))
                    DonationListUiState(
                        donations = filtered,
                        totalCount = donations.size,
                        query = q,
                        statusFilter = status,
                        tagFilter = tag,
                        sort = sortKey,
                        availableTags = donations.flatMap { it.tags }.distinct().sorted(),
                        hasEvent = true
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DonationListUiState())

    fun setQuery(q: String) { query.value = q }

    fun setStatus(status: DonationStatus?) {
        statusFilter.value = status
        prefs.donationStatusFilter = status?.name
    }

    fun setTag(tag: String?) { tagFilter.value = tag }

    fun setSort(sortKey: String) {
        sort.value = sortKey
        prefs.donationSort = sortKey
    }

    fun clearFilters() {
        query.value = ""
        setStatus(null)
        setTag(null)
    }

    companion object {
        fun comparatorFor(sortKey: String): Comparator<Donation> = when (sortKey) {
            SessionPrefs.SORT_OLDEST -> compareBy { it.addedTime }
            SessionPrefs.SORT_AMOUNT_DESC -> compareByDescending { it.amount }
            SessionPrefs.SORT_AMOUNT_ASC -> compareBy { it.amount }
            SessionPrefs.SORT_NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.donorName }
            else -> compareByDescending { it.addedTime }
        }
    }
}
