package com.shankaravam.festival.core.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SarvamCacheNamingTest {

    @Test
    fun full_sentence_and_roster_caches_never_collide() {
        assertEquals(
            "donation_abc123.mp3",
            SarvamTtsClient.cacheFileName("abc123", roster = false)
        )
        assertEquals(
            "donation_abc123_roster.mp3",
            SarvamTtsClient.cacheFileName("abc123", roster = true)
        )
        assertNotEquals(
            SarvamTtsClient.cacheFileName("abc123", roster = false),
            SarvamTtsClient.cacheFileName("abc123", roster = true)
        )
    }
}
