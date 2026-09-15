package com.shankaravam.festival.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeluguTransliteratorTest {

    @Test
    fun null_and_blank_return_empty_string() {
        assertEquals("", TeluguTransliterator.transliterate(null))
        assertEquals("", TeluguTransliterator.transliterate(""))
        assertEquals("", TeluguTransliterator.transliterate("   "))
    }

    @Test
    fun pure_telugu_passes_through_unchanged() {
        assertEquals("సందీప్ రెడ్డి", TeluguTransliterator.transliterate("సందీప్ రెడ్డి"))
        assertEquals("ఆర్. సందీప్ రెడ్డి", TeluguTransliterator.transliterate("ఆర్. సందీప్ రెడ్డి"))
        assertEquals("శ్రీ సీతారాముల కళ్యాణం", TeluguTransliterator.transliterate("శ్రీ సీతారాముల కళ్యాణం"))
    }

    @Test
    fun initials_handled_with_and_without_dots() {
        assertEquals("ఆర్. సందీప్", TeluguTransliterator.transliterate("R. Sandeep"))
        assertEquals("ఆర్. సందీప్", TeluguTransliterator.transliterate("R Sandeep"))
        assertEquals("ఆర్. సందీప్", TeluguTransliterator.transliterate("r sandeep"))
        assertEquals("కే. వి. రావు", TeluguTransliterator.transliterate("K. V. Rao"))
        assertEquals("కే. వి. రావు", TeluguTransliterator.transliterate("K V Rao"))
        assertEquals("కే. వి. రావు", TeluguTransliterator.transliterate("K.V. Rao"))
        assertEquals("ఎం. శ్రీనివాస్", TeluguTransliterator.transliterate("M. Srinivas"))
        assertEquals("పి. వెంకటేష్", TeluguTransliterator.transliterate("P. Venkatesh"))
        assertEquals("టి. కృష్ణ", TeluguTransliterator.transliterate("T. Krishna"))
    }

    @Test
    fun common_telugu_surnames_and_vocabulary() {
        assertEquals("రెడ్డి", TeluguTransliterator.transliterate("Reddy"))
        assertEquals("రెడ్డి", TeluguTransliterator.transliterate("reddi"))
        assertEquals("చౌదరి", TeluguTransliterator.transliterate("Chowdary"))
        assertEquals("చౌదరి", TeluguTransliterator.transliterate("Choudary"))
        assertEquals("చౌదరి", TeluguTransliterator.transliterate("Chaudary"))
        assertEquals("నాయుడు", TeluguTransliterator.transliterate("Naidu"))
        assertEquals("గౌడ్", TeluguTransliterator.transliterate("Goud"))
        assertEquals("గౌడ్", TeluguTransliterator.transliterate("Gowd"))
        assertEquals("రాజు", TeluguTransliterator.transliterate("Raju"))
        assertEquals("రావు", TeluguTransliterator.transliterate("Rao"))
        assertEquals("శ్రీ", TeluguTransliterator.transliterate("Sri"))
        assertEquals("లక్ష్మి", TeluguTransliterator.transliterate("Lakshmi"))
        assertEquals("లక్ష్మి", TeluguTransliterator.transliterate("Laxmi"))
        assertEquals("వర్మ", TeluguTransliterator.transliterate("Varma"))
        assertEquals("శర్మ", TeluguTransliterator.transliterate("Sharma"))
        assertEquals("గుప్తా", TeluguTransliterator.transliterate("Gupta"))
        assertEquals("కుమార్", TeluguTransliterator.transliterate("Kumar"))
        assertEquals("కుమారి", TeluguTransliterator.transliterate("Kumari"))
        assertEquals("ప్రసాద్", TeluguTransliterator.transliterate("Prasad"))
    }

    @Test
    fun hyphens_and_dashes_are_converted_to_spaces_zero_dashes() {
        val asciiHyphen = TeluguTransliterator.transliterate("Mary-Jane")
        assertFalse("-" in asciiHyphen || "–" in asciiHyphen || "—" in asciiHyphen)
        assertTrue(" " in asciiHyphen)

        val enDash = TeluguTransliterator.transliterate("Mary–Jane")
        assertFalse("-" in enDash || "–" in enDash || "—" in enDash)
        assertTrue(" " in enDash)

        val emDash = TeluguTransliterator.transliterate("Mary—Jane")
        assertFalse("-" in emDash || "–" in emDash || "—" in emDash)
        assertTrue(" " in emDash)

        // Telugu compound names with hyphens and en/em dashes
        assertEquals("సందీప్ కుమార్", TeluguTransliterator.transliterate("Sandeep-Kumar"))
        assertEquals("సందీప్ కుమార్", TeluguTransliterator.transliterate("Sandeep–Kumar"))
        assertEquals("సందీప్ కుమార్", TeluguTransliterator.transliterate("Sandeep—Kumar"))

        val teluguWithDash = TeluguTransliterator.transliterate("రమేష్—కుమార్")
        assertFalse("-" in teluguWithDash || "–" in teluguWithDash || "—" in teluguWithDash)
        assertEquals("రమేష్ కుమార్", teluguWithDash)
    }

    @Test
    fun mixed_telugu_and_english_passthrough() {
        val result = TeluguTransliterator.transliterate("ఆర్. Sandeep Reddy")
        assertTrue(result.startsWith("ఆర్."))
        assertTrue("రెడ్డి" in result)
    }

    @Test
    fun full_name_flow() {
        val name = TeluguTransliterator.transliterate("R. Sandeep Reddy")
        assertEquals("ఆర్. సందీప్ రెడ్డి", name)
        assertEquals("కే. వి. రావు", TeluguTransliterator.transliterate("K. V. Rao"))
        assertEquals("ఎం. రమేష్ కుమార్", TeluguTransliterator.transliterate("M. Ramesh Kumar"))
    }

    @Test
    fun ravinder_and_indian_nder_names() {
        assertEquals("రవీందర్", TeluguTransliterator.transliterate("ravinder"))
        assertEquals("రవీందర్", TeluguTransliterator.transliterate("Ravinder"))
        assertEquals("రవీంద్ర", TeluguTransliterator.transliterate("ravindra"))
        assertEquals("సురేందర్", TeluguTransliterator.transliterate("surender"))
        assertEquals("సురేంద్ర", TeluguTransliterator.transliterate("surendra"))
        assertEquals("నరేందర్", TeluguTransliterator.transliterate("narender"))
        assertEquals("నరేంద్ర", TeluguTransliterator.transliterate("narendra"))
        assertEquals("మహేందర్", TeluguTransliterator.transliterate("mahender"))
        assertEquals("దేవేందర్", TeluguTransliterator.transliterate("devender"))
        assertEquals("ఉపేందర్", TeluguTransliterator.transliterate("upender"))
        assertEquals("రాజేందర్", TeluguTransliterator.transliterate("rajender"))
        assertEquals("రాఘవేంద్ర", TeluguTransliterator.transliterate("raghavendra"))
        assertEquals("జోగిందర్", TeluguTransliterator.transliterate("joginder"))
    }

    @Test
    fun compound_family_caste_and_surname_names() {
        // As requested by user:
        assertEquals("నర్సిరెడ్డి", TeluguTransliterator.transliterate("narsireddy"))
        assertEquals("వెంకట్ రెడ్డి", TeluguTransliterator.transliterate("venkatreddy"))
        assertEquals("వెంకట్ రెడ్డి", TeluguTransliterator.transliterate("venkat reddy"))

        // Consonant-ending prefixes with surnames get natural space
        assertEquals("రామ్ రెడ్డి", TeluguTransliterator.transliterate("ramreddy"))
        assertEquals("విజయ్ కుమార్", TeluguTransliterator.transliterate("vijaykumar"))
        assertEquals("ఆనంద్ రెడ్డి", TeluguTransliterator.transliterate("anandreddy"))
        assertEquals("నర్సిగౌడ్", TeluguTransliterator.transliterate("narsigoud"))

        // Vowel-ending prefixes blend directly
        assertEquals("శివప్రసాద్", TeluguTransliterator.transliterate("sivaprasad"))
        assertEquals("సుబ్బారావు", TeluguTransliterator.transliterate("subbarao"))
        assertEquals("రాంబాబు", TeluguTransliterator.transliterate("rambabu"))
        assertEquals("రమాదేవి", TeluguTransliterator.transliterate("ramadevi"))
        assertEquals("శ్రీదేవి", TeluguTransliterator.transliterate("sridevi"))
        assertEquals("లక్ష్మీదేవి", TeluguTransliterator.transliterate("laxmidevi"))

        // Kinship / Honorific blending
        assertEquals("రామయ్య", TeluguTransliterator.transliterate("ramaiah"))
        assertEquals("రామయ్య", TeluguTransliterator.transliterate("ramayya"))
        assertEquals("వెంకటయ్య", TeluguTransliterator.transliterate("venkataiah"))
        assertEquals("నర్సయ్య", TeluguTransliterator.transliterate("narsaiah"))
        assertEquals("వెంకటమ్మ", TeluguTransliterator.transliterate("venkatamma"))
        assertEquals("రాములమ్మ", TeluguTransliterator.transliterate("ramulamma"))
    }

    @Test
    fun geminate_consonants_and_final_y_vowel() {
        assertEquals("రెడ్డి", TeluguTransliterator.transliterate("reddy"))
        assertEquals("స్వామి", TeluguTransliterator.transliterate("swamy"))
        assertEquals("చౌదరి", TeluguTransliterator.transliterate("chowdary"))
        assertEquals("అన్న", TeluguTransliterator.transliterate("anna"))
        assertEquals("అమ్మ", TeluguTransliterator.transliterate("amma"))
        assertEquals("అప్ప", TeluguTransliterator.transliterate("appa"))
    }
}
