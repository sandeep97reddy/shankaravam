package com.shankaravam.festival.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun tags_survive_commas_and_telugu_text() {
        val tags = listOf("Cash, UPI", "బియ్యం", "Saree Flowers")
        val roundTripped = converters.stringToTags(converters.tagsToString(tags))
        assertEquals(tags, roundTripped)
    }

    @Test
    fun empty_list_round_trips_to_empty() {
        assertEquals("", converters.tagsToString(emptyList()))
        assertTrue(converters.stringToTags("").isEmpty())
    }

    @Test
    fun single_tag_has_no_separator() {
        assertEquals(listOf("Rice"), converters.stringToTags(converters.tagsToString(listOf("Rice"))))
    }
}
