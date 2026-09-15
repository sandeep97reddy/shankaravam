package com.shankaravam.festival.presentation.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.usecase.BalanceSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.shankaravam.festival.domain.model.AccessPolicy
import com.shankaravam.festival.domain.model.MemberStatus
import com.shankaravam.festival.domain.model.memberStatusOf
import com.shankaravam.festival.domain.model.roleOf

@Immutable
data class DashboardUiState(
    val event: Event? = null,
    val totals: BalanceSnapshot = BalanceSnapshot(),
    val canWriteMoney: Boolean = true,
    val isRevoked: Boolean = false
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
                    container.observeEventTotals(eventId),
                    container.foregroundSync.seat
                ) { event, snap, seat ->
                    val role = when {
                        seat != null && seat.eventId == eventId -> roleOf(seat.role)
                        else -> roleOf(prefs.myRole(eventId))
                    }
                    val status = when {
                        seat != null && seat.eventId == eventId -> memberStatusOf(seat.status)
                        else -> memberStatusOf(prefs.myStatus(eventId))
                    }
                    val canWrite = AccessPolicy.canWriteMoney(role, status)
                    val revoked = status == MemberStatus.REVOKED
                    DashboardUiState(
                        event = event,
                        totals = snap,
                        canWriteMoney = canWrite,
                        isRevoked = revoked
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private val _unsynced = MutableStateFlow(0)
    val unsyncedCount: StateFlow<Int> = _unsynced

    fun toggleLanguage() {
        prefs.toggleAppLanguage()
    }

    /** One-shot pending-upload count for the offline badge (G6 clears it via sync). */
    fun refreshUnsynced() {
        viewModelScope.launch {
            _unsynced.value = donationRepo.pendingSync().size + expenseRepo.pendingSync().size
        }
    }
}
