package com.shankaravam.festival.core.tts

import com.shankaravam.festival.domain.model.Donation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnouncementTemplatesTest {

    private fun cashDonation() = Donation(
        id = "d1",
        eventId = "e1",
        donorName = "Redabothu Sandeep Reddy",
        pronunciationText = "రెడబోతు సందీప్ రెడ్డి",
        amount = 1116.0,
        addedTime = 1L
    )

    @Test
    fun telugu_cash_single_announcement_uses_pronunciation_and_natural_noota() {
        val text = buildDonationAnnouncement(cashDonation(), "వినాయక చవితి", AnnouncementLanguage.TELUGU)
        assertEquals(
            "శ్రీ రెడబోతు సందీప్ రెడ్డి గారు వినాయక చవితి కోసం వెయ్యి నూట పదహారు రూపాయలు విరాళంగా అందించారు. ధన్యవాదాలు!",
            text
        )
    }

    @Test
    fun roster_item_is_crisp_without_repetitive_thanks() {
        val text = buildRosterItemAnnouncement(cashDonation(), AnnouncementLanguage.TELUGU)
        assertEquals("శ్రీ రెడబోతు సందీప్ రెడ్డి గారు, వెయ్యి నూట పదహారు రూపాయలు.", text)
    }

    @Test
    fun opening_announcement_formats_location_and_preset() {
        val text = buildOpeningAnnouncement("కొట్లగడ్డ", "వినాయక చవితి", FestivalPreset.VINAYAKA_CHAVITHI, AnnouncementLanguage.TELUGU)
        assertEquals("మన కొట్లగడ్డ లో వినాయక చవితి ఉత్సవాల సందర్భంగా విరాళాలు అందించిన దాతల వివరాలు:", text)
    }

    @Test
    fun closing_announcement_has_devotional_outro() {
        val text = buildClosingAnnouncement(AnnouncementLanguage.TELUGU)
        assertTrue("హృదయపూర్వక ధన్యవాదాలు" in text)
    }

    @Test
    fun telugu_falls_back_to_display_name_without_pronunciation() {
        val text = buildDonationAnnouncement(
            cashDonation().copy(pronunciationText = null),
            "వినాయక చవితి",
            AnnouncementLanguage.TELUGU
        )
        assertTrue(text.startsWith("శ్రీ Redabothu Sandeep Reddy గారు"))
    }

    @Test
    fun english_cash_uses_digits_and_display_name() {
        val text = buildDonationAnnouncement(cashDonation(), "Vinayaka Chavithi", AnnouncementLanguage.ENGLISH)
        assertTrue("Redabothu Sandeep Reddy" in text)
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
            unit = "కేజీలు",
            addedTime = 2L
        )
        val text = buildDonationAnnouncement(donation, "రాములవారి కళ్యాణం", AnnouncementLanguage.TELUGU)
        assertEquals("శ్రీ Sita గారు రాములవారి కళ్యాణం కోసం పది కేజీలు బియ్యం విరాళంగా అందించారు. ధన్యవాదాలు!", text)
    }

    @Test
    fun honorific_srimati_replaces_sri_everywhere() {
        val donation = cashDonation().copy(
            donorName = "Lakshmamma",
            pronunciationText = "లక్ష్మమ్మ",
            honorific = "శ్రీమతి"
        )
        assertEquals(
            "శ్రీమతి లక్ష్మమ్మ గారు వినాయక చవితి కోసం వెయ్యి నూట పదహారు రూపాయలు విరాళంగా అందించారు. ధన్యవాదాలు!",
            buildDonationAnnouncement(donation, "వినాయక చవితి", AnnouncementLanguage.TELUGU)
        )
        assertEquals(
            "శ్రీమతి లక్ష్మమ్మ గారు, వెయ్యి నూట పదహారు రూపాయలు.",
            buildRosterItemAnnouncement(donation, AnnouncementLanguage.TELUGU)
        )
        assertTrue(
            buildDonationAnnouncement(donation, "Vinayaka Chavithi", AnnouncementLanguage.ENGLISH)
                .startsWith("Smt. Lakshmamma donated")
        )
    }

    @Test
    fun honorific_kumari_maps_to_english_kumari() {
        val donation = cashDonation().copy(honorific = "కుమారి")
        assertTrue(
            buildDonationAnnouncement(donation, "వినాయక చవితి", AnnouncementLanguage.TELUGU)
                .startsWith("కుమారి రెడబోతు సందీప్ రెడ్డి గారు")
        )
        assertTrue(
            buildDonationAnnouncement(donation, "event", AnnouncementLanguage.ENGLISH)
                .startsWith("Kumari Redabothu Sandeep Reddy donated")
        )
    }

    @Test
    fun preset_auto_links_from_event_name() {
        assertEquals(FestivalPreset.HANUMAN_JAYANTHI, presetForEventName("హనుమాన్ జయంతి 2026"))
        assertEquals(FestivalPreset.HANUMAN_JAYANTHI, presetForEventName("Sri Hanuman Jayanthi"))
        assertEquals(FestivalPreset.MAHA_SHIVARATRI, presetForEventName("మహా శివరాత్రి జాగరణ"))
        assertEquals(FestivalPreset.MAHA_SHIVARATRI, presetForEventName("Maha Shivaratri 2026"))
        assertEquals(FestivalPreset.SRI_RAMA_NAVAMI, presetForEventName("శ్రీరామనవమి కళ్యాణం"))
        assertEquals(FestivalPreset.SRI_RAMA_NAVAMI, presetForEventName("Sri Rama Navami"))
        assertEquals(FestivalPreset.KANAKA_DURGAMMA, presetForEventName("దేవీ నవరాత్రులు"))
        assertEquals(FestivalPreset.KANAKA_DURGAMMA, presetForEventName("Devi Navaratri"))
        assertEquals(FestivalPreset.TEMPLE_ANNADANAM, presetForEventName("ఆలయ అన్నదానం"))
        assertEquals(FestivalPreset.VINAYAKA_CHAVITHI, presetForEventName("వినాయక చవితి 2026"))
        assertEquals(null, presetForEventName("Village Fair"))
        assertEquals(null, presetForEventName(""))
    }
}
