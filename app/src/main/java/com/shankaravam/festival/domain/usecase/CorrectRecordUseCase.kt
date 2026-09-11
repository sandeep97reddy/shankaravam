package com.shankaravam.festival.domain.usecase

import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.CorrectionTargetType

/** Typo-fix window (plan §16): inside it the row is edited directly. */
const val CORRECTION_GRACE_WINDOW_MS = 5 * 60 * 1000L

/**
 * Uniform amount-fix flow for donations and expenses.
 * - Inside the grace window: [applyGraceEdit] upserts the fixed row, no ledger row.
 * - Outside: appends a [Correction] via [RecordCorrectionUseCase]; the original
 *   row is never touched here (callers must not update it either).
 * Returns the appended correction, or null for a grace-window edit.
 */
class CorrectRecordUseCase(
    private val recordCorrection: RecordCorrectionUseCase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        eventId: String,
        targetRecordId: String,
        targetType: CorrectionTargetType,
        originalAmount: Double,
        addedTimeMillis: Long,
        newAmount: Double,
        reason: String,
        correctedBy: String = "",
        applyGraceEdit: suspend () -> Unit
    ): Outcome<Correction?> {
        if (newAmount < 0) return Outcome.Err("Amount cannot be negative")
        if (newAmount == originalAmount) return Outcome.Err("No change to save")
        if (clock() - addedTimeMillis <= CORRECTION_GRACE_WINDOW_MS) {
            applyGraceEdit()
            return Outcome.Ok(null)
        }
        return when (
            val result = recordCorrection(
                eventId = eventId,
                targetRecordId = targetRecordId,
                targetType = targetType,
                originalAmount = originalAmount,
                deltaAmount = newAmount - originalAmount,
                reason = reason,
                correctedBy = correctedBy
            )
        ) {
            is Outcome.Ok -> Outcome.Ok(result.value)
            is Outcome.Err -> result
        }
    }
}
