package com.shankaravam.festival.presentation.settings

import com.shankaravam.festival.data.remote.CloudUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncFlowTest {

    data class TestUiState(
        val eventId: String? = null,
        val user: CloudUser? = null,
        val syncEnabled: Boolean = false
    )

    @Test
    fun flow_updates_when_user_signs_in_without_event() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val currentEventId = MutableStateFlow<String?>(null)
        val userFlow = MutableStateFlow<CloudUser?>(null)
        val syncEnabledFlow = MutableStateFlow(false)
        val codeTick = MutableStateFlow(0)

        // The exact flatMapLatest + combine pattern used in CloudSyncViewModel.uiState
        val uiStateFlow = currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                combine(
                    userFlow,
                    syncEnabledFlow,
                    codeTick
                ) { user, syncEnabled, _ ->
                    TestUiState(
                        eventId = null,
                        user = user,
                        syncEnabled = syncEnabled
                    )
                }
            } else {
                combine(
                    userFlow,
                    syncEnabledFlow,
                    codeTick
                ) { user, syncEnabled, _ ->
                    TestUiState(
                        eventId = eventId,
                        user = user,
                        syncEnabled = syncEnabled
                    )
                }
            }
        }

        val emissions = mutableListOf<TestUiState>()
        val job = launch(testDispatcher) {
            uiStateFlow.toList(emissions)
        }

        // Initial state: no event, no user
        assertEquals(1, emissions.size)
        assertEquals(null, emissions.last().user)
        assertEquals(null, emissions.last().eventId)

        // User signs in with Google before creating any event
        val signedInUser = CloudUser(uid = "uid-123", email = "collector@example.com", displayName = "Collector")
        userFlow.value = signedInUser

        assertEquals(2, emissions.size)
        assertEquals(signedInUser, emissions.last().user)
        assertEquals(null, emissions.last().eventId)

        // User joins an event (setting currentEventId)
        currentEventId.value = "event-456"

        assertEquals(3, emissions.size)
        assertEquals(signedInUser, emissions.last().user)
        assertEquals("event-456", emissions.last().eventId)

        // User signs out
        userFlow.value = null

        assertEquals(4, emissions.size)
        assertEquals(null, emissions.last().user)
        assertEquals("event-456", emissions.last().eventId)

        job.cancel()
    }
}
