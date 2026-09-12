package com.shankaravam.festival.core.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AudioImportTest {

    @Test
    fun hash_is_deterministic_and_input_sensitive() {
        val a = audioHashFor("hello", "TELUGU", "meera", true)
        assertEquals(a, audioHashFor("hello", "TELUGU", "meera", true))
        assertNotEquals(a, audioHashFor("hello!", "TELUGU", "meera", true))
        assertNotEquals(a, audioHashFor("hello", "ENGLISH", "meera", true))
        assertNotEquals(a, audioHashFor("hello", "TELUGU", "arvind", true))
        assertNotEquals(a, audioHashFor("hello", "TELUGU", "meera", false))
        assertTrue(a.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun normalize_strips_numbers_punctuation_but_keeps_telugu() {
        assertEquals("01ramesh", normalizeClipName("01 Ramesh.mp3"))
        assertEquals("రమేష్", normalizeClipName("రమేష్.mp3"))
    }

    @Test
    fun matcher_prefers_exact_then_containment_and_reports_leftovers() {
        val donors = listOf(
            ClipDonor("d1", "Ramesh", "రమేష్"),
            ClipDonor("d2", "Sita"),
            ClipDonor("d3", "Lakshmi")
        )
        val result = matchRosterClips(
            fileNames = listOf("01 Ramesh.mp3", "sita-garu.mp3", "unknown.mp3", "LAKSHMI.mp3"),
            donors = donors
        )
        assertEquals(mapOf(0 to "d1", 1 to "d2", 3 to "d3"), result.matched)
        assertEquals(listOf(2), result.unmatched)
    }

    @Test
    fun matcher_matches_telugu_pronunciation_and_never_double_books() {
        val donors = listOf(
            ClipDonor("d1", "Ramesh", "రమేష్"),
            ClipDonor("d2", "Ramesh Kumar")
        )
        val result = matchRosterClips(
            fileNames = listOf("రమేష్.mp3", "ramesh.mp3"),
            donors = donors
        )
        // First file takes d1 (pronunciation hit); second file must not reuse d1.
        assertEquals("d1", result.matched[0])
        assertEquals("d2", result.matched[1])
        assertTrue(result.unmatched.isEmpty())
    }
}
