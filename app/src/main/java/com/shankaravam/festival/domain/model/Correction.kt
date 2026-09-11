package com.shankaravam.festival.domain.model

import androidx.compose.runtime.Immutable

/**
 * Non-destructive ledger entry (plan §16). The original donation/expense row is
 * NEVER modified — the correction carries the delta and the reason.
 */
@Immutable
data class Correction(
    val id: String,
    val eventId: String,
    val targetRecordId: String,
    val targetType: CorrectionTargetType,
    val originalAmount: Double,
    val deltaAmount: Double,
    val reason: String,
    val correctedBy: String = "",
    val createdAt: Long = 0L,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD
) {
    val effectiveAmount: Double get() = originalAmount + deltaAmount
}
