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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeCorrections : CorrectionRepository {
    val recorded = mutableListOf<Correction>()
    override fun observeForEvent(eventId: String): Flow<List<Correction>> =
        MutableStateFlow(recorded.toList())
    override fun observeForTarget(targetId: String): Flow<List<Correction>> =
        MutableStateFlow(recorded.toList())
    override suspend fun record(correction: Correction) {
        recorded += correction
    }
}

private class FakeActivity : ActivityRepository {
    val logged = mutableListOf<ActivityRecord>()
    override fun observeRecent(eventId: String, limit: Int): Flow<List<ActivityRecord>> =
        MutableStateFlow(emptyList())
    override suspend fun log(entry: ActivityRecord) {
        logged += entry
    }
}

class CorrectRecordUseCaseTest {

    private val corrections = FakeCorrections()
    private val activity = FakeActivity()
    private var now = 1_000_000L
    private val record = RecordCorrectionUseCase(corrections, activity) { now }
    private val useCase = CorrectRecordUseCase(record) { now }

    @Test
    fun inside_grace_window_edits_directly_with_no_ledger_row() = runTest {
        var edited = false
        val result = useCase(
            eventId = "e1", targetRecordId = "d1",
            targetType = CorrectionTargetType.DONATION,
            originalAmount = 5000.0, addedTimeMillis = now - 60_000L,
            newAmount = 500.0, reason = "",
            applyGraceEdit = { edited = true }
        )

        assertTrue(result is Outcome.Ok)
        assertNull((result as Outcome.Ok).value)
        assertTrue(edited)
        assertTrue(corrections.recorded.isEmpty())
    }

    @Test
    fun outside_window_appends_correction_and_never_edits() = runTest {
        var edited = false
        val result = useCase(
            eventId = "e1", targetRecordId = "d1",
            targetType = CorrectionTargetType.DONATION,
            originalAmount = 5000.0, addedTimeMillis = now - 10 * 60_000L,
            newAmount = 500.0, reason = "Typo",
            applyGraceEdit = { edited = true }
        )

        assertTrue(result is Outcome.Ok, "expected Ok, got $result")
        val correction = (result as Outcome.Ok).value!!
        assertEquals(-4500.0, correction.deltaAmount)
        assertEquals(500.0, correction.effectiveAmount)
        assertTrue(!edited, "grace edit must NOT run outside the window")
        assertEquals(1, corrections.recorded.size)
    }

    @Test
    fun outside_window_without_reason_is_rejected() = runTest {
        val result = useCase(
            eventId = "e1", targetRecordId = "d1",
            targetType = CorrectionTargetType.EXPENSE,
            originalAmount = 500.0, addedTimeMillis = now - 10 * 60_000L,
            newAmount = 400.0, reason = "  ",
            applyGraceEdit = {}
        )

        assertTrue(result is Outcome.Err)
        assertTrue(corrections.recorded.isEmpty())
    }

    @Test
    fun identical_amount_is_rejected_everywhere() = runTest {
        val result = useCase(
            eventId = "e1", targetRecordId = "d1",
            targetType = CorrectionTargetType.DONATION,
            originalAmount = 500.0, addedTimeMillis = now,
            newAmount = 500.0, reason = "oops",
            applyGraceEdit = {}
        )

        assertTrue(result is Outcome.Err)
    }
}
