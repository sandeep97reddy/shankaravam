package com.shankaravam.festival.domain.usecase

import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.newRecordId
import com.shankaravam.festival.domain.model.ActivityActions
import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.CorrectionTargetType
import com.shankaravam.festival.domain.model.SyncStatus
import com.shankaravam.festival.domain.repository.ActivityRepository
import com.shankaravam.festival.domain.repository.CorrectionRepository

/**
 * Non-destructive fix (plan §16): appends a correction row. The original
 * donation/expense is intentionally left untouched — callers must NOT
 * update the target row. Effective amount = original + delta.
 */
class RecordCorrectionUseCase(
    private val corrections: CorrectionRepository,
    private val activity: ActivityRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        eventId: String,
        targetRecordId: String,
        targetType: CorrectionTargetType,
        originalAmount: Double,
        deltaAmount: Double,
        reason: String,
        correctedBy: String = ""
    ): Outcome<Correction> {
        if (reason.isBlank()) return Outcome.Err("A reason is required for every correction")
        if (deltaAmount == 0.0) return Outcome.Err("Correction amount cannot be zero")
        if (originalAmount + deltaAmount < 0) {
            return Outcome.Err("Corrected amount cannot go below zero")
        }

        val now = clock()
        val correction = Correction(
            id = newRecordId(),
            eventId = eventId,
            targetRecordId = targetRecordId,
            targetType = targetType,
            originalAmount = originalAmount,
            deltaAmount = deltaAmount,
            reason = reason.trim(),
            correctedBy = correctedBy,
            createdAt = now,
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        corrections.record(correction)
        activity.log(
            ActivityRecord(
                id = newRecordId(),
                eventId = eventId,
                actionType = ActivityActions.CORRECTION_ADDED,
                details = targetRecordId,
                actorId = correctedBy,
                timestamp = now
            )
        )
        return Outcome.Ok(correction)
    }
}
