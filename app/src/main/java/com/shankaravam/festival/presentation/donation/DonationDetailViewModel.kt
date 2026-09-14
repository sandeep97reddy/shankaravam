package com.shankaravam.festival.presentation.donation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.CorrectionTargetType
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.SyncStatus
import com.shankaravam.festival.domain.model.AccessPolicy
import com.shankaravam.festival.domain.model.effectiveDonationAmount
import com.shankaravam.festival.domain.model.memberStatusOf
import com.shankaravam.festival.domain.model.roleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class DonationDetailViewModel(
    private val container: AppContainer,
    donationId: String
) : ViewModel() {
    val corrections: StateFlow<List<Correction>> =
        container.correctionRepository.observeForTarget(donationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Live row: audioStatus flips to READY when background TTS finishes, so
     * observers (e.g. the share-audio button) appear without reopening.
     */
    val donation: StateFlow<Donation?> =
        container.donationRepository.observeById(donationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
        // P3: role alone is not enough — pending/revoked fail the rules'
        // active requirement, so refuse with sync-matching copy.
        AccessPolicy.writeBlockedReason(
            roleOf(container.sessionPrefs.myRole(donation.eventId)),
            memberStatusOf(container.sessionPrefs.myStatus(donation.eventId))
        )?.let { return Outcome.Err(it) }
        val who = actor.ifBlank { container.sessionPrefs.attributionName() }
        // T0.2 writer fix: chain off the current EFFECTIVE figure so each new
        // delta is incremental and the audit trail reads original → effective.
        // (Pre-fix rows carry base-original; the latest-wins reader in
        // EffectiveAmounts.kt stays correct for both — no backfill.)
        val base = runCatching {
            withContext(Dispatchers.IO) {
                effectiveDonationAmount(
                    donation,
                    container.correctionRepository.observeForTarget(donation.id).first()
                )
            }
        }.getOrDefault(donation.amount)
        val result = container.correctRecord(
            eventId = donation.eventId,
            targetRecordId = donation.id,
            targetType = CorrectionTargetType.DONATION,
            originalAmount = base,
            addedTimeMillis = donation.addedTime,
            newAmount = newAmount,
            reason = reason,
            correctedBy = who
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
            // T0.1 grace-edit audio invalidation (see [invalidateAudio]).
            runCatching {
                withContext(Dispatchers.IO) { invalidateAudio(donation.id, now) }
            }
        }
        // F1: post-grace corrections append a Correction row WITHOUT touching
        // the donation — but any legacy `donation_{id}_{speaker}.mp3` still
        // speaks the old figure, and prefetch treats its presence as cached
        // (so CAS for the corrected figure would never generate). Purge here
        // too, so both paths converge on regeneration.
        if (result is Outcome.Ok && result.value != null) {
            runCatching {
                withContext(Dispatchers.IO) {
                    invalidateAudio(donation.id, System.currentTimeMillis())
                }
            }
        }
        return result
    }

    /**
     * Drops stale audio for the corrected figure: deletes regenerable Sarvam
     * clips, quarantines human imports to `.bak-<timestamp>` (recoverable —
     * a recording of the old amount is equally wrong to play), resets status
     * so the queue regenerates. Best-effort — never fails the ledger edit.
     */
    private suspend fun invalidateAudio(donationId: String, now: Long) {
        runCatching { container.ttsEngine.invalidateDonationAudio(donationId, now) }
        runCatching {
            container.donationRepository.updateAudioStatus(
                donationId,
                AudioStatus.NOT_GENERATED
            )
        }
    }
}
