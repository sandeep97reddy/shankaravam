package com.shankaravam.festival.core.tts

import com.shankaravam.festival.domain.model.Donation
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the Worker `MAX_TEXT_LENGTH` cap from measured template output
 * (R2 plan §4.2: the 300-char bid is validated here, not assumed).
 *
 * Names are unbounded free text, so any fixed cap is an abuse guard, not a
 * correctness bound — but it must clear generous REALISTIC announcements or
 * the gateway 400s legitimate donations.
 */
class MaxTemplateLengthTest {

    private fun worstCaseDonation() = Donation(
        id = "d1",
        eventId = "e1",
        // 50-char display + 50-char pronunciation: long but plausible.
        donorName = "Redabothu Venkata Sandeep Reddy Kumar Chowdary Jr",
        pronunciationText = "రెడబోతు వెంకట సందీప్ రెడ్డి కుమార్ చౌదరి జూనియర్",
        // Lakh-scale: longest Telugu amount words in normal use.
        amount = 9_999_999.0,
        addedTime = 1L
    )

    private val worstEvent = "శ్రీ సీతారామచంద్రస్వామి కళ్యాణ మహోత్సవము 2026"

    @Test
    fun worst_realistic_bilingual_fits_worker_cap() {
        val full = buildDonationAnnouncement(
            worstCaseDonation(), worstEvent, AnnouncementLanguage.BILINGUAL, effectiveAmount = 9_999_999.0
        )
        val roster = buildRosterItemAnnouncement(
            worstCaseDonation(), AnnouncementLanguage.BILINGUAL, effectiveAmount = 9_999_999.0
        )
        println("MEASURED worst full bilingual=${full.length} roster bilingual=${roster.length}")
        assertTrue(full.length <= 500, "full bilingual ${full.length} chars exceeds 500: $full")
        assertTrue(roster.length <= 500, "roster bilingual ${roster.length} chars exceeds 500: $roster")
    }

    @Test
    fun typical_bilingual_fits_comfortably() {
        val d = Donation(
            id = "d1", eventId = "e1", donorName = "Ramesh",
            pronunciationText = "రమేష్", amount = 5000.0, addedTime = 1L
        )
        val full = buildDonationAnnouncement(d, "వినాయక చవితి", AnnouncementLanguage.BILINGUAL)
        assertTrue(full.length <= 300, "typical bilingual ${full.length} chars: $full")
    }
}
