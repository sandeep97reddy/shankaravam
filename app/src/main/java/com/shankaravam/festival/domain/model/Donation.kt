package com.shankaravam.festival.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Donation(
    val id: String,
    val eventId: String,
    val donorName: String,
    val pronunciationText: String? = null,
    val amount: Double = 0.0,
    val currency: String = "INR",
    val isNonCash: Boolean = false,
    val itemDescription: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val paymentMethod: String = "Cash",
    val tags: List<String> = emptyList(),
    val status: DonationStatus = DonationStatus.RECEIVED,
    val announcementEnabled: Boolean = true,
    val audioStatus: AudioStatus = AudioStatus.NOT_GENERATED,
    val notes: String? = null,
    val addedBy: String = "",
    val addedTime: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val version: Long = 1L,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD
) {
    /** Only received/confirmed cash counts toward the balance (plan §10, §19). */
    val countsTowardBalance: Boolean
        get() = !isNonCash &&
            (status == DonationStatus.RECEIVED || status == DonationStatus.CONFIRMED)
}
