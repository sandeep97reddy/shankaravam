package com.shankaravam.festival.core.tts

import kotlin.test.Test
import kotlin.test.assertEquals

class TeluguNumberFormatterTest {

    @Test
    fun plan_examples_match_byte_for_byte() {
        assertEquals("ఐదు వేల", TeluguNumberFormatter.wordsForNumber(5000))
        assertEquals("పది వేల పదహారు", TeluguNumberFormatter.wordsForNumber(10016))
    }

    @Test
    fun ones_teens_tens() {
        assertEquals("సున్నా", TeluguNumberFormatter.wordsForNumber(0))
        assertEquals("ఒకటి", TeluguNumberFormatter.wordsForNumber(1))
        assertEquals("పది", TeluguNumberFormatter.wordsForNumber(10))
        assertEquals("పందొమ్మిది", TeluguNumberFormatter.wordsForNumber(19))
        assertEquals("ఇరవై", TeluguNumberFormatter.wordsForNumber(20))
        assertEquals("ఇరవై ఒకటి", TeluguNumberFormatter.wordsForNumber(21))
        assertEquals("తొంభై తొమ్మిది", TeluguNumberFormatter.wordsForNumber(99))
    }

    @Test
    fun natural_noota_hundreds_and_thousands() {
        assertEquals("వంద", TeluguNumberFormatter.wordsForNumber(100))
        assertEquals("నూట ఒకటి", TeluguNumberFormatter.wordsForNumber(101))
        assertEquals("నూట పదహారు", TeluguNumberFormatter.wordsForNumber(116))
        assertEquals("రెండు వందల", TeluguNumberFormatter.wordsForNumber(200))
        assertEquals("రెండు వందల పదహారు", TeluguNumberFormatter.wordsForNumber(216))
        assertEquals("వెయ్యి", TeluguNumberFormatter.wordsForNumber(1000))
        assertEquals("వెయ్యి నూట పదహారు", TeluguNumberFormatter.wordsForNumber(1116))
        assertEquals("రెండు వేల", TeluguNumberFormatter.wordsForNumber(2000))
        assertEquals("రెండు వేల నూట పదహారు", TeluguNumberFormatter.wordsForNumber(2116))
        assertEquals("ఐదు వేల నూట పదహారు", TeluguNumberFormatter.wordsForNumber(5116))
        assertEquals("ఒక లక్ష", TeluguNumberFormatter.wordsForNumber(100_000))
        assertEquals("రెండు లక్షల", TeluguNumberFormatter.wordsForNumber(200_000))
        assertEquals("ఒక కోటి", TeluguNumberFormatter.wordsForNumber(10_000_000))
        assertEquals("రెండు కోట్ల", TeluguNumberFormatter.wordsForNumber(20_000_000))
    }

    @Test
    fun amount_appends_rupees_and_optional_paise() {
        assertEquals("ఐదు వేల రూపాయలు", TeluguNumberFormatter.wordsForAmount(5000.0))
        assertEquals("నూట ఒక రూపాయి", TeluguNumberFormatter.wordsForAmount(101.0))
        assertEquals("ఐదు వందల ఒక రూపాయి", TeluguNumberFormatter.wordsForAmount(501.0))
        assertEquals("వెయ్యి నూట పదహారు రూపాయలు", TeluguNumberFormatter.wordsForAmount(1116.0))
        assertEquals("రెండు వేల నూట పదహారు రూపాయలు", TeluguNumberFormatter.wordsForAmount(2116.0))
        assertEquals("ఐదు వేల నూట పదహారు రూపాయలు", TeluguNumberFormatter.wordsForAmount(5116.0))
        assertEquals("వంద రూపాయలు యాభై పైసలు", TeluguNumberFormatter.wordsForAmount(100.50))
        assertEquals("యాభై పైసలు", TeluguNumberFormatter.wordsForAmount(0.50))
        assertEquals("సున్నా రూపాయలు", TeluguNumberFormatter.wordsForAmount(0.0))
    }
}
