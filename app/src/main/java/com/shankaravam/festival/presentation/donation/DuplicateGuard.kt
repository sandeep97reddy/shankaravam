package com.shankaravam.festival.presentation.donation

import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus

/** Duplicate-entry window (feature #6): accidental re-taps land inside 60 s. */
const val DUPLICATE_WINDOW_MILLIS = 60_000L

/**
 * What the confirmation dialog shows. [itemLabel] is set for non-cash
 * rows (amount comparison is meaningless for kind gifts).
 */
data class DuplicateInfo(
    val donorName: String,
    val amount: Double,
    val secondsAgo: Long,
    val itemLabel: String? = null
)

/**
 * Pure duplicate detector (feature #6): flags a save when the event's latest
 * row matches the new entry inside the window. Deliberately narrow —
 * same donor + same gift + same receipt status — so legitimate repeats
 * (a PLEDGED row followed by its RECEIVED fulfillment, a second family
 * gift a minute later) never prompt. Case/whitespace-insensitive.
 */
fun findDuplicateCandidate(
    recent: Donation?,
    donorName: String,
    amount: Double,
    itemDescription: String?,
    isNonCash: Boolean,
    status: DonationStatus,
    now: Long
): DuplicateInfo? {
    if (recent == null) return null
    if (recent.status != status) return null
    // P1 clock-skew guard: a negative age (clock moved back, or a future-
    // stamped row) is NOT "within the window" — and must never render as
    // "just -3s ago".
    val ageMillis = now - recent.createdAt
    if (ageMillis < 0 || ageMillis >= DUPLICATE_WINDOW_MILLIS) return null
    if (!recent.donorName.trim().equals(donorName.trim(), ignoreCase = true)) return null
    if (donorName.isBlank()) return null
    val secondsAgo = ageMillis / 1000
    return if (isNonCash || recent.isNonCash) {
        val want = itemDescription?.trim().orEmpty()
        val got = recent.itemDescription?.trim().orEmpty()
        if (want.isBlank() || !got.equals(want, ignoreCase = true)) null
        else DuplicateInfo(recent.donorName, recent.amount, secondsAgo, want)
    } else {
        if (recent.amount != amount || amount <= 0) null
        else DuplicateInfo(recent.donorName, recent.amount, secondsAgo)
    }
}
