package com.shankaravam.festival.presentation.donation

import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DuplicateGuardTest {

    private val now = 1_700_000_000_000L

    private fun recent(
        name: String = "Ramesh",
        amount: Double = 5000.0,
        ageMillis: Long = 25_000L,
        status: DonationStatus = DonationStatus.RECEIVED,
        nonCash: Boolean = false,
        item: String? = null
    ) = Donation(
        id = "d1", eventId = "e1", donorName = name, amount = amount,
        isNonCash = nonCash, itemDescription = item, status = status,
        addedBy = "c", addedTime = now - ageMillis, createdAt = now - ageMillis
    )

    @Test
    fun identical_name_and_amount_within_window_flags() {
        val dup = findDuplicateCandidate(
            recent(), "ramesh  ", 5000.0, null, false, DonationStatus.RECEIVED, now
        )
        assertNotNull(dup)
        assertEquals("Ramesh", dup.donorName)
        assertEquals(5000.0, dup.amount)
        assertEquals(25L, dup.secondsAgo)
    }

    @Test
    fun different_amount_status_or_stale_entry_passes() {
        assertNull(findDuplicateCandidate(recent(), "Ramesh", 5001.0, null, false, DonationStatus.RECEIVED, now))
        // PLEDGED followed by its RECEIVED fulfillment is a normal flow, not a dup.
        assertNull(findDuplicateCandidate(recent(), "Ramesh", 5000.0, null, false, DonationStatus.PLEDGED, now))
        assertNull(findDuplicateCandidate(recent(status = DonationStatus.PLEDGED), "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now))
        assertNull(findDuplicateCandidate(recent(ageMillis = 60_000L), "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now))
        assertNull(findDuplicateCandidate(recent(ageMillis = 3600_000L), "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now))
        assertNull(findDuplicateCandidate(recent(name = "Suresh"), "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now))
        assertNull(findDuplicateCandidate(null, "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now))
        assertNull(findDuplicateCandidate(recent(), "   ", 5000.0, null, false, DonationStatus.RECEIVED, now))
    }

    @Test
    fun clock_skew_never_flags_and_never_prints_negative() {
        // Clock moved backwards after the save: negative age is not "recent".
        assertNull(
            findDuplicateCandidate(recent(ageMillis = -3_000L), "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now)
        )
        // Future-stamped row: must not flag indefinitely until time catches up.
        assertNull(
            findDuplicateCandidate(recent(ageMillis = -3_600_000L), "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now)
        )
        // Boundary: exactly at the window edge is outside.
        assertNull(
            findDuplicateCandidate(recent(ageMillis = DUPLICATE_WINDOW_MILLIS), "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now)
        )
        // Just inside still flags with a sane non-negative count.
        val dup = findDuplicateCandidate(
            recent(ageMillis = DUPLICATE_WINDOW_MILLIS - 1), "Ramesh", 5000.0, null, false, DonationStatus.RECEIVED, now
        )
        assertNotNull(dup)
        assertTrue(dup.secondsAgo >= 0)
    }

    @Test
    fun non_cash_matches_on_item_not_amount() {
        val row = recent(nonCash = true, item = "Rice bag")
        val dup = findDuplicateCandidate(row, "Ramesh", 0.0, "rice BAG", true, DonationStatus.RECEIVED, now)
        assertNotNull(dup)
        assertEquals("rice BAG", dup.itemLabel)
        assertNull(findDuplicateCandidate(row, "Ramesh", 0.0, "Wheat bag", true, DonationStatus.RECEIVED, now))
        // Cash row vs kind entry never matches.
        assertNull(findDuplicateCandidate(recent(), "Ramesh", 0.0, "Rice bag", true, DonationStatus.RECEIVED, now))
    }
}
