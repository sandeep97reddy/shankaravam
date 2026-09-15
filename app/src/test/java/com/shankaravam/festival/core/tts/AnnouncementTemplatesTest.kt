package com.shankaravam.festival.core.tts

import com.shankaravam.festival.domain.model.Donation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            "శ్రీ రెడబోతు సందీప్ రెడ్డి గారు వినాయక చవితి సందర్భంగా, వెయ్యి నూట పదహారు రూపాయలు విరాళంగా సమర్పించారు. వారికి ఉత్సవ కమిటీ తరపున హృదయపూర్వక ధన్యవాదాలు. వారి కుటుంబం చల్లగా ఉండాలని కోరుకుంటున్నాము.",
            text
        )
    }

    @Test
    fun single_announcement_ends_with_family_blessing_and_safe_pauses() {
        val cash = buildDonationAnnouncement(cashDonation(), "వినాయక చవితి", AnnouncementLanguage.TELUGU)
        assertTrue("వారి కుటుంబం చల్లగా ఉండాలని కోరుకుంటున్నాము." in cash)
        // TTS cadence: commas/periods only — native te-IN fallback speaks
        // "-" / "—" aloud as డాష్/మైనస్.
        assertFalse("-" in cash)
        assertFalse("—" in cash)
        assertFalse("–" in cash)
        // Non-cash branch shares the same blessing.
        val material = buildDonationAnnouncement(
            cashDonation().copy(
                isNonCash = true, itemDescription = "బియ్యం",
                quantity = 10.0, unit = "కేజీలు"
            ),
            "వినాయక చవితి",
            AnnouncementLanguage.TELUGU
        )
        assertTrue("వారి కుటుంబం చల్లగా ఉండాలని కోరుకుంటున్నాము." in material)
        // Roster stays crisp — no blessing repeated per row.
        val roster = buildRosterItemAnnouncement(cashDonation(), AnnouncementLanguage.TELUGU)
        assertFalse("కుటుంబం" in roster)
    }

    @Test
    fun roster_item_is_crisp_without_repetitive_thanks() {
        val text = buildRosterItemAnnouncement(cashDonation(), AnnouncementLanguage.TELUGU)
        assertEquals("శ్రీ రెడబోతు సందీప్ రెడ్డి గారు, వెయ్యి నూట పదహారు రూపాయలు.", text)
    }

    @Test
    fun opening_announcement_formats_location_and_preset() {
        val text = buildOpeningAnnouncement("కొట్లగడ్డ", "వినాయక చవితి", FestivalPreset.VINAYAKA_CHAVITHI, AnnouncementLanguage.TELUGU)
        assertEquals("మన కొట్లగడ్డ లో వినాయక చవితి ఉత్సవాల సందర్భంగా విరాళాలు సమర్పించిన భక్తుల వివరాలు:", text)
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
        assertTrue("ధన్యవాదాలు" in text)
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
        assertEquals("శ్రీ Sita గారు రాములవారి కళ్యాణం సందర్భంగా, పది కేజీలు బియ్యం విరాళంగా సమర్పించారు. వారికి ఉత్సవ కమిటీ తరపున హృదయపూర్వక ధన్యవాదాలు. వారి కుటుంబం చల్లగా ఉండాలని కోరుకుంటున్నాము.", text)
    }

    @Test
    fun material_announcement_deduplicates_when_qty_and_unit_already_in_description() {
        // Volunteer entered "50 kg rice bag" in itemDescription AND 50 in quantity AND "kg" in unit
        val donation = Donation(
            id = "d3",
            eventId = "e1",
            donorName = "రామారావు",
            isNonCash = true,
            itemDescription = "50 kg rice bag",
            quantity = 50.0,
            unit = "kg",
            addedTime = 3L
        )
        val single = buildDonationAnnouncement(donation, "వినాయక చవితి", AnnouncementLanguage.TELUGU)
        assertTrue("50 kg rice bag విరాళంగా సమర్పించారు" in single)
        assertFalse("యాభై" in single)

        val roster = buildRosterItemAnnouncement(donation, AnnouncementLanguage.TELUGU)
        assertEquals("శ్రీ రామారావు గారు, 50 kg rice bag.", roster)
    }

    @Test
    fun material_announcement_deduplicates_telugu_qty_and_unit() {
        val donation = Donation(
            id = "d4",
            eventId = "e1",
            donorName = "వెంకటేశ్వర్లు",
            isNonCash = true,
            itemDescription = "50 కేజీల బియ్యం బస్తా",
            quantity = 50.0,
            unit = "కేజీలు",
            addedTime = 4L
        )
        val roster = buildRosterItemAnnouncement(donation, AnnouncementLanguage.TELUGU)
        assertEquals("శ్రీ వెంకటేశ్వర్లు గారు, 50 కేజీల బియ్యం బస్తా.", roster)
        assertFalse("యాభై" in roster)
    }

    @Test
    fun material_announcement_natural_description_without_qty_unit_fields() {
        // Simplified flow where quantity and unit are null
        val donation = Donation(
            id = "d5",
            eventId = "e1",
            donorName = "సుబ్బారావు",
            isNonCash = true,
            itemDescription = "2 డబ్బాల ఆవు నెయ్యి",
            quantity = null,
            unit = null,
            addedTime = 5L
        )
        val roster = buildRosterItemAnnouncement(donation, AnnouncementLanguage.TELUGU)
        assertEquals("శ్రీ సుబ్బారావు గారు, 2 డబ్బాల ఆవు నెయ్యి.", roster)
    }

    @Test
    fun honorific_srimati_replaces_sri_everywhere() {
        val donation = cashDonation().copy(
            donorName = "Lakshmamma",
            pronunciationText = "లక్ష్మమ్మ",
            honorific = "శ్రీమతి"
        )
        assertEquals(
            "శ్రీమతి లక్ష్మమ్మ గారు వినాయక చవితి సందర్భంగా, వెయ్యి నూట పదహారు రూపాయలు విరాళంగా సమర్పించారు. వారికి ఉత్సవ కమిటీ తరపున హృదయపూర్వక ధన్యవాదాలు. వారి కుటుంబం చల్లగా ఉండాలని కోరుకుంటున్నాము.",
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
    fun intro_phrase_key_varies_with_language_location_preset_and_event() {
        // M3: the intro text varies with all four — the key must too, or a
        // stale clip in the wrong language replays after a switch.
        val base = introPhraseKey(
            FestivalPreset.VINAYAKA_CHAVITHI, "event-id-123456",
            AnnouncementLanguage.TELUGU, "కొట్లగడ్డ"
        )
        val otherLang = introPhraseKey(
            FestivalPreset.VINAYAKA_CHAVITHI, "event-id-123456",
            AnnouncementLanguage.ENGLISH, "కొట్లగడ్డ"
        )
        val otherLoc = introPhraseKey(
            FestivalPreset.VINAYAKA_CHAVITHI, "event-id-123456",
            AnnouncementLanguage.TELUGU, "హైదరాబాద్"
        )
        val otherPreset = introPhraseKey(
            FestivalPreset.HANUMAN_JAYANTHI, "event-id-123456",
            AnnouncementLanguage.TELUGU, "కొట్లగడ్డ"
        )
        val otherEvent = introPhraseKey(
            FestivalPreset.VINAYAKA_CHAVITHI, "event-id-999999",
            AnnouncementLanguage.TELUGU, "కొట్లగడ్డ"
        )
        assertTrue(otherLang != base)
        assertTrue(otherLoc != base)
        assertTrue(otherPreset != base)
        assertTrue(otherEvent != base)
        // Stable for identical inputs; null/blank event degrades to "loc".
        assertEquals(
            base,
            introPhraseKey(
                FestivalPreset.VINAYAKA_CHAVITHI, "event-id-123456",
                AnnouncementLanguage.TELUGU, "కొట్లగడ్డ"
            )
        )
        assertTrue(
            introPhraseKey(FestivalPreset.VINAYAKA_CHAVITHI, null, AnnouncementLanguage.TELUGU, "x")
                .contains("_loc_")
        )
    }

    @Test
    fun blank_location_does_not_produce_mana_mana_stutter() {
        val blankText = buildOpeningAnnouncement("", "వినాయక చవితి", FestivalPreset.VINAYAKA_CHAVITHI, AnnouncementLanguage.TELUGU)
        assertTrue("మన ప్రాంతంలో" in blankText)
        assertTrue("మన మన" !in blankText)

        val whitespaceText = buildOpeningAnnouncement("   ", "వినాయక చవితి", FestivalPreset.VINAYAKA_CHAVITHI, AnnouncementLanguage.TELUGU)
        assertTrue("మన ప్రాంతంలో" in whitespaceText)
        assertTrue("మన మన" !in whitespaceText)
    }

    @Test
    fun intro_phrase_key_includes_v2_version() {
        val key = introPhraseKey(FestivalPreset.VINAYAKA_CHAVITHI, "event-1", AnnouncementLanguage.TELUGU, "కొట్లగడ్డ")
        assertTrue(key.startsWith("intro_v2_vinayaka_chavithi_"))
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

        // New presets & collision resistance
        assertEquals(FestivalPreset.GRAMA_DEVATHA_JATARA, presetForEventName("గ్రామ దేవత పోలేరమ్మ జాతర"))
        assertEquals(FestivalPreset.GRAMA_DEVATHA_JATARA, presetForEventName("Gangamma Tirunalla"))
        assertEquals(FestivalPreset.GRAMA_DEVATHA_JATARA, presetForEventName("Grama Jatara 2026")) // Must not match "rama"
        assertEquals(FestivalPreset.BONALU_BATHUKAMMA, presetForEventName("శ్రీ మహంకాళి బోనాలు 2026"))
        assertEquals(FestivalPreset.BONALU_BATHUKAMMA, presetForEventName("Bathukamma Celebrations"))
        assertEquals(FestivalPreset.AYYAPPA_MANDALAM, presetForEventName("శ్రీ అయ్యప్ప స్వామి పడిపూజ"))
        assertEquals(FestivalPreset.AYYAPPA_MANDALAM, presetForEventName("Ayyappa Mandala Pooja")) // Must not match generic annadanam
        assertEquals(FestivalPreset.AYYAPPA_MANDALAM, presetForEventName("Mandala Pooja 2026"))
        assertEquals(FestivalPreset.AYYAPPA_MANDALAM, presetForEventName("మండల దీక్ష ముగింపు"))
        assertEquals(FestivalPreset.KRISHNA_JANMASHTAMI, presetForEventName("శ్రీ కృష్ణ జన్మాష్టమి వేడుకలు"))
        assertEquals(FestivalPreset.KRISHNA_JANMASHTAMI, presetForEventName("Gokulashtami Utlotsav"))

        // Administrative / non-religious events must NOT match Ayyappa or other presets
        assertEquals(null, presetForEventName("Mandal Parishad Meeting"))
        assertEquals(null, presetForEventName("మండల రెవెన్యూ కార్యాలయం"))
        assertEquals(null, presetForEventName("Village Fair"))
        assertEquals(null, presetForEventName(""))
    }

    @Test
    fun bonalu_title_telugu_contains_no_latin_ampersand() {
        assertFalse("&" in FestivalPreset.BONALU_BATHUKAMMA.titleTe)
        assertTrue("మరియు" in FestivalPreset.BONALU_BATHUKAMMA.titleTe)
    }
}
