package com.durgamma.festival.presentation.donation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durgamma.festival.core.util.Outcome
import com.durgamma.festival.di.AppContainer
import com.durgamma.festival.domain.model.Correction
import com.durgamma.festival.domain.model.CorrectionTargetType
import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.SyncStatus
import com.durgamma.festival.domain.model.AccessPolicy
import com.durgamma.festival.domain.model.roleOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DonationDetailViewModel(
    private val container: AppContainer,
    donationId: String
) : ViewModel() {
    val corrections: StateFlow<List<Correction>> =
        container.correctionRepository.observeForTarget(donationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Amount fix for this row: grace-window direct edit, otherwise an appended
     * Correction (the row itself is never rewritten outside the window).
     * Returns the appended correction, or null for a grace-window edit.
     */
    suspend fun correct(
        donation: Donation,
        newAmount: Double,
        reason: String,
        actor: String = ""
    ): Outcome<Correction?> {
        if (!AccessPolicy.canCorrect(roleOf(container.sessionPrefs.myRole(donation.eventId)))) {
            return Outcome.Err("Fixing entries needs a collector role.")
        }
        return container.correctRecord(
            eventId = donation.eventId,
            targetRecordId = donation.id,
            targetType = CorrectionTargetType.DONATION,
            originalAmount = donation.amount,
            addedTimeMillis = donation.addedTime,
            newAmount = newAmount,
            reason = reason,
            correctedBy = actor
        ) {
            val now = System.currentTimeMillis()
            container.donationRepository.save(
                donation.copy(
                    amount = newAmount,
                    updatedAt = now,
                    version = donation.version + 1,
                    syncStatus = SyncStatus.PENDING_UPLOAD
                )
            )
        }
    }
}
