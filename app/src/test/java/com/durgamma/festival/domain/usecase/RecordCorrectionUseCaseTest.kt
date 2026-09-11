package com.durgamma.festival.domain.usecase

import com.durgamma.festival.core.util.Outcome
import com.durgamma.festival.domain.model.ActivityRecord
import com.durgamma.festival.domain.model.Correction
import com.durgamma.festival.domain.model.CorrectionTargetType
import com.durgamma.festival.domain.repository.ActivityRepository
import com.durgamma.festival.domain.repository.CorrectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeCorrectionRepository : CorrectionRepository {
    val recorded = mutableListOf<Correction>()
    private val state = MutableStateFlow<List<Correction>>(emptyList())

    override fun observeForEvent(eventId: String): Flow<List<Correction>> = state
    override fun observeForTarget(targetId: String): Flow<List<Correction>> = state
    override suspend fun record(correction: Correction) {
        recorded += correction
        state.value = recorded.toList()
    }
}

private class FakeActivity2 : ActivityRepository {
    val logged = mutableListOf<ActivityRecord>()
    override fun observeRecent(eventId: String, limit: Int): Flow<List<ActivityRecord>> =
        MutableStateFlow(emptyList())
    override suspend fun log(entry: ActivityRecord) {
        logged += entry
    }
}

class RecordCorrectionUseCaseTest {

    private val corrections = FakeCorrectionRepository()
    private val activity = FakeActivity2()
    private val useCase = RecordCorrectionUseCase(corrections, activity) { 2000L }

    @Test
    fun typo_fix_appends_correction_and_preserves_original() = runTest {
        // Original: 5000 entered instead of 500 -> delta -4500.
        val result = useCase(
            eventId = "e1",
            targetRecordId = "d-1",
            targetType = CorrectionTargetType.DONATION,
            originalAmount = 5000.0,
            deltaAmount = -4500.0,
            reason = "Typo entered 5000 instead of 500",
            correctedBy = "collector-1"
        )

        assertTrue(result is Outcome.Ok, "expected Ok, got $result")
        val correction = (result as Outcome.Ok).value
        assertEquals(500.0, correction.effectiveAmount)
        assertEquals(2000L, correction.createdAt)
        assertEquals(1, corrections.recorded.size)
        assertEquals(1, activity.logged.size)
    }

    @Test
    fun blank_reason_is_rejected() = runTest {
        val result = useCase(
            eventId = "e1", targetRecordId = "d-1",
            targetType = CorrectionTargetType.DONATION,
            originalAmount = 5000.0, deltaAmount = -4500.0, reason = "  "
        )

        assertTrue(result is Outcome.Err)
        assertTrue(corrections.recorded.isEmpty())
    }

    @Test
    fun zero_delta_is_rejected() = runTest {
        val result = useCase(
            eventId = "e1", targetRecordId = "d-1",
            targetType = CorrectionTargetType.EXPENSE,
            originalAmount = 500.0, deltaAmount = 0.0, reason = "no-op"
        )

        assertTrue(result is Outcome.Err)
        assertTrue(corrections.recorded.isEmpty())
    }

    @Test
    fun correction_below_zero_is_rejected() = runTest {
        val result = useCase(
            eventId = "e1", targetRecordId = "d-1",
            targetType = CorrectionTargetType.DONATION,
            originalAmount = 500.0, deltaAmount = -600.0, reason = "over-correct"
        )

        assertTrue(result is Outcome.Err)
        assertTrue(corrections.recorded.isEmpty())
    }
}
