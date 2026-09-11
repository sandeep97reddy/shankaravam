package com.durgamma.festival.presentation.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durgamma.festival.data.local.SessionPrefs
import com.durgamma.festival.di.AppContainer
import com.durgamma.festival.domain.model.Event
import com.durgamma.festival.domain.usecase.BalanceSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class DashboardUiState(
    val event: Event? = null,
    val totals: BalanceSnapshot = BalanceSnapshot()
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(container: AppContainer) : ViewModel() {
    private val prefs: SessionPrefs = container.sessionPrefs
    private val donationRepo = container.donationRepository
    private val expenseRepo = container.expenseRepository

    val uiState: StateFlow<DashboardUiState> =
        prefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(DashboardUiState())
            } else {
                combine(
                    container.eventRepository.observeEvent(eventId),
                    container.observeEventTotals(eventId)
                ) { event, snap -> DashboardUiState(event = event, totals = snap) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private val _unsynced = MutableStateFlow(0)
    val unsyncedCount: StateFlow<Int> = _unsynced

    /** One-shot pending-upload count for the offline badge (G6 clears it via sync). */
    fun refreshUnsynced() {
        viewModelScope.launch {
            _unsynced.value = donationRepo.pendingSync().size + expenseRepo.pendingSync().size
        }
    }
}
