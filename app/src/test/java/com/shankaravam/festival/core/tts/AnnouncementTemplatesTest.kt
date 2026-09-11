package com.shankaravam.festival.core.tts

import com.shankaravam.festival.domain.model.Donation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnouncementTemplatesTest {

    private fun cashDonation() = Donation(
        id = "d1",
        eventId = "e1",
        donorName = "Ramesh",
        pronunciationText = "రమేష్",
        amount = 5000.0,
        addedTime = 1L
    )

    @Test
    fun telugu_cash_uses_pronunciation_and_word_amount() {
        val text = buildDonationAnnouncement(cashDonation(), "వినాయక చవితి", AnnouncementLanguage.TELUGU)
        assertEquals(
            "శ్రీ రమేష్ గారు వినాయక చవితి కోసం ఐదు వేల రూపాయలు విరాళంగా అందించారు. ధన్యవాదాలు!",
            text
        )
    }

    @Test
    fun telugu_falls_back_to_display_name_without_pronunciation() {
        val text = buildDonationAnnouncement(
            cashDonation().copy(pronunciationText = null),
            "వినాయక చవితి",
            AnnouncementLanguage.TELUGU
        )
        assertTrue(text.startsWith("శ్రీ Ramesh గారు"))
    }

    @Test
    fun english_cash_uses_digits_and_display_name() {
        val text = buildDonationAnnouncement(cashDonation(), "Vinayaka Chavithi", AnnouncementLanguage.ENGLISH)
        assertTrue("Ramesh" in text)
        assertTrue("Thank you!" in text)
    }

    @Test
    fun bilingual_contains_both_halves() {
        val text = buildDonationAnnouncement(cashDonation(), "వినాయక చవితి", AnnouncementLanguage.BILINGUAL)
        assertTrue("ధన్యవాదాలు!" in text)
        assertTrue("Thank you!" in text)
    }

    @Test
    fun material_announcement_names_quantity_and_item() {
        val donation = Donation(
            id = "d2",
            eventId = "e1",
            donorName = "Sita",
            isNonCash = true,
            itemDescription = "బియ్యం",
            quantity = 10.0,
            unit = "కిలోలు",
            addedTime = 1L
        )
        val text = buildDonationAnnouncement(donation, "వినాయక చవితి", AnnouncementLanguage.TELUGU)
        assertEquals(
            "శ్రీ Sita గారు వినాయక చవితి కోసం పది కిలోలు బియ్యం విరాళంగా అందించారు. ధన్యవాదాలు!",
            text
        )
    }
}
