package com.shankaravam.festival.presentation.event

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.util.newRecordId
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.ActivityActions
import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.model.SyncStatus
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
    private val container: AppContainer,
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
        val who = addedBy.ifBlank { prefs.attributionName() }
        // Head identity: signed-in uid wins, else this install's device id.
        // Rules recognize globalHeadId as the event creator (see firestore.rules).
        val ownerId = container.authRepository.user.value?.uid ?: prefs.deviceId
        val event = Event(
            id = newRecordId(),
            name = name.trim(),
            templeName = templeName.trim(),
            location = location.trim(),
            startDateMillis = now,
            endDateMillis = null,
            globalHeadId = ownerId,
            creatorId = ownerId,
            deviceId = prefs.deviceId,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        viewModelScope.launch {
            eventsRepo.save(event)
            prefs.setMyRole(event.id, SessionPrefs.ROLE_GLOBAL_HEAD)
            activityRepo.log(
                ActivityRecord(
                    id = newRecordId(),
                    eventId = event.id,
                    actionType = ActivityActions.EVENT_CREATED,
                    details = event.name,
                    actorId = who,
                    timestamp = now
                )
            )
            prefs.setCurrentEventId(event.id)
        }
        return true
    }
}
