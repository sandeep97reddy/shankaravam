package com.shankaravam.festival.domain.usecase

import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.SyncStatus
import com.shankaravam.festival.domain.repository.ActivityRepository
import com.shankaravam.festival.domain.repository.DonationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeDonationRepository : DonationRepository {
    val saved = mutableListOf<Donation>()
    private val state = MutableStateFlow<List<Donation>>(emptyList())

    override fun observeForEvent(eventId: String): Flow<List<Donation>> = state
    override fun observeById(id: String): Flow<Donation?> = state.map { list -> list.find { it.id == id } }
    override suspend fun save(donation: Donation) {
        saved += donation
        state.value = saved.toList()
    }

    override suspend fun updateStatus(id: String, status: DonationStatus, now: Long) = Unit
    override suspend fun updateSyncState(id: String, status: SyncStatus) = Unit
    override suspend fun updateAudioStatus(
        id: String,
        status: com.shankaravam.festival.domain.model.AudioStatus,
        now: Long
    ) = Unit
    override suspend fun pendingSync(): List<Donation> = saved.toList()
    override suspend fun latestForEvent(eventId: String): Donation? =
        saved.filter { it.eventId == eventId }.maxByOrNull { it.createdAt }
}

private class FakeActivityRepository : ActivityRepository {
    val logged = mutableListOf<ActivityRecord>()
    private val state = MutableStateFlow<List<ActivityRecord>>(emptyList())

    override fun observeRecent(eventId: String, limit: Int): Flow<List<ActivityRecord>> = state
    override suspend fun log(entry: ActivityRecord) {
        logged += entry
        state.value = logged.toList()
    }
}

class SaveDonationUseCaseTest {

    private val donations = FakeDonationRepository()
    private val activity = FakeActivityRepository()
    private val useCase = SaveDonationUseCase(donations, activity) { 1000L }

    @Test
    fun valid_cash_donation_is_saved_with_sane_defaults() = runTest {
        val result = useCase(eventId = "e1", donorName = " Ramesh ", amount = 5000.0, addedBy = "collector-1")

        assertTrue(result is com.shankaravam.festival.core.util.Outcome.Ok, "expected Ok, got $result")
        val saved = (result as com.shankaravam.festival.core.util.Outcome.Ok).value
        assertEquals("Ramesh", saved.donorName)
        assertEquals(1000L, saved.addedTime)
        assertEquals(DonationStatus.RECEIVED, saved.status)
        assertEquals(com.shankaravam.festival.domain.model.SyncStatus.PENDING_UPLOAD, saved.syncStatus)
        assertTrue(saved.id.isNotBlank())
        assertEquals(1, donations.saved.size)
        assertEquals(1, activity.logged.size)
        assertEquals("e1", activity.logged.single().eventId)
    }

    @Test
    fun blank_donor_name_is_rejected_without_side_effects() = runTest {
        val result = useCase(eventId = "e1", donorName = "   ", amount = 100.0)

        assertTrue(result is com.shankaravam.festival.core.util.Outcome.Err)
        assertTrue(donations.saved.isEmpty())
        assertTrue(activity.logged.isEmpty())
    }

    @Test
    fun non_cash_without_item_is_rejected() = runTest {
        val result = useCase(eventId = "e1", donorName = "Sita", isNonCash = true, amount = 0.0)

        assertTrue(result is com.shankaravam.festival.core.util.Outcome.Err)
        assertTrue(donations.saved.isEmpty())
    }

    @Test
    fun negative_amount_is_rejected() = runTest {
        val result = useCase(eventId = "e1", donorName = "Sita", amount = -50.0)

        assertTrue(result is com.shankaravam.festival.core.util.Outcome.Err)
        assertTrue(donations.saved.isEmpty())
    }
}
