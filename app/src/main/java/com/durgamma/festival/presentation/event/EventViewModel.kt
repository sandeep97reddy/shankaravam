package com.durgamma.festival.presentation.event

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durgamma.festival.core.util.newRecordId
import com.durgamma.festival.data.local.SessionPrefs
import com.durgamma.festival.di.AppContainer
import com.durgamma.festival.domain.model.ActivityActions
import com.durgamma.festival.domain.model.ActivityRecord
import com.durgamma.festival.domain.model.Event
import com.durgamma.festival.domain.model.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class EventUiState(
    val events: List<Event> = emptyList(),
    val currentEvent: Event? = null
)

class EventViewModel(
    container: AppContainer,
    val prefs: SessionPrefs
) : ViewModel() {
    private val eventsRepo = container.eventRepository
    private val activityRepo = container.activityRepository

    val uiState: StateFlow<EventUiState> =
        combine(eventsRepo.observeEvents(), prefs.currentEventId) { events, currentId ->
            val current = events.find { it.id == currentId } ?: events.firstOrNull()
            EventUiState(events = events, currentEvent = current)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EventUiState())

    fun selectEvent(id: String) = prefs.setCurrentEventId(id)

    fun createEvent(
        name: String,
        templeName: String,
        location: String,
        addedBy: String = ""
    ): Boolean {
        if (name.isBlank()) return false
        val now = System.currentTimeMillis()
        val event = Event(
            id = newRecordId(),
            name = name.trim(),
            templeName = templeName.trim(),
            location = location.trim(),
            startDateMillis = now,
            endDateMillis = null,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        viewModelScope.launch {
            eventsRepo.save(event)
            activityRepo.log(
                ActivityRecord(
                    id = newRecordId(),
                    eventId = event.id,
                    actionType = ActivityActions.EVENT_CREATED,
                    details = event.name,
                    actorId = addedBy,
                    timestamp = now
                )
            )
            prefs.setCurrentEventId(event.id)
        }
        return true
    }
}
