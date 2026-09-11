package com.shankaravam.festival.domain.usecase

import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.newRecordId
import com.shankaravam.festival.domain.model.ActivityActions
import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.model.HONORIFIC_SRI
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.SyncStatus
import com.shankaravam.festival.domain.repository.ActivityRepository
import com.shankaravam.festival.domain.repository.DonationRepository

/**
 * Counter-hot-path use case: validate -> Room insert -> activity log.
 * Never touches network; TTS generation (G4) observes the saved row.
 */
class SaveDonationUseCase(
    private val donations: DonationRepository,
    private val activity: ActivityRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        eventId: String,
        donorName: String,
        amount: Double = 0.0,
        isNonCash: Boolean = false,
        itemDescription: String? = null,
        quantity: Double? = null,
        unit: String? = null,
        pronunciationText: String? = null,
        honorific: String = HONORIFIC_SRI,
        paymentMethod: String = "Cash",
        tags: List<String> = emptyList(),
        status: DonationStatus = DonationStatus.RECEIVED,
        announcementEnabled: Boolean = true,
        notes: String? = null,
        addedBy: String = ""
    ): Outcome<Donation> {
        if (donorName.isBlank()) return Outcome.Err("Donor name is required")
        if (amount < 0) return Outcome.Err("Amount cannot be negative")
        if (isNonCash && itemDescription.isNullOrBlank()) {
            return Outcome.Err("Item description is required for non-cash donations")
        }
        if (!isNonCash && amount == 0.0 && itemDescription.isNullOrBlank()) {
            return Outcome.Err("Enter an amount or an item description")
        }

        val now = clock()
        val donation = Donation(
            id = newRecordId(),
            eventId = eventId,
            donorName = donorName.trim(),
            pronunciationText = pronunciationText?.trim()?.ifEmpty { null },
            honorific = honorific.trim().ifEmpty { HONORIFIC_SRI },
            amount = amount,
            isNonCash = isNonCash,
            itemDescription = itemDescription?.trim()?.ifEmpty { null },
            quantity = quantity,
            unit = unit?.trim()?.ifEmpty { null },
            paymentMethod = paymentMethod,
            tags = tags,
            status = status,
            announcementEnabled = announcementEnabled,
            audioStatus = AudioStatus.NOT_GENERATED,
            notes = notes?.trim()?.ifEmpty { null },
            addedBy = addedBy,
            addedTime = now,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        donations.save(donation)
        activity.log(
            ActivityRecord(
                id = newRecordId(),
                eventId = eventId,
                actionType = ActivityActions.DONATION_ADDED,
                details = donation.id,
                actorId = addedBy,
                timestamp = now
            )
        )
        return Outcome.Ok(donation)
    }
}
