package com.shankaravam.festival.core.tts

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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

    @Test
    fun normalizeSarvamSpeaker_maps_legacy_and_defaults_correctly() {
        assertEquals("kavitha", normalizeSarvamSpeaker("meera"))
        assertEquals("aditya", normalizeSarvamSpeaker("arvind"))
        assertEquals("priya", normalizeSarvamSpeaker("priya"))
        assertEquals("shubh", normalizeSarvamSpeaker("shubh"))
        assertEquals("ratan", normalizeSarvamSpeaker("ratan"))
        assertEquals("kavitha", normalizeSarvamSpeaker("kavitha"))
        assertEquals("priya", normalizeSarvamSpeaker(null))
        assertEquals("priya", normalizeSarvamSpeaker("invalid_speaker"))
    }

    @Test
    fun sarvamErrorParser_extracts_json_error_message() {
        val json400 = "{\"error\":{\"message\":\"Field 'text' is required\",\"code\":\"invalid_request_error\"}}"
        val body400 = json400.toResponseBody("application/json".toMediaType())
        val resp400 = retrofit2.Response.error<okhttp3.ResponseBody>(400, body400)
        val ex400 = retrofit2.HttpException(resp400)

        val parsed400 = com.shankaravam.festival.data.remote.SarvamErrorParser.parse(ex400)
        assertEquals("HTTP 400: Field 'text' is required", parsed400)

        val json403 = "{\"error\":{\"message\":\"Invalid or missing authentication credentials\",\"code\":\"invalid_api_key_error\"}}"
        val body403 = json403.toResponseBody("application/json".toMediaType())
        val resp403 = retrofit2.Response.error<okhttp3.ResponseBody>(403, body403)
        val ex403 = retrofit2.HttpException(resp403)

        val parsed403 = com.shankaravam.festival.data.remote.SarvamErrorParser.parse(ex403)
        assertEquals("HTTP 403: Invalid or missing authentication credentials", parsed403)

        val plainEx = java.io.IOException("Connection reset")
        assertEquals("Connection reset", com.shankaravam.festival.data.remote.SarvamErrorParser.parse(plainEx))
    }
}
