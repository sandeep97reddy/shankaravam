package com.shankaravam.festival.core.tts

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SarvamCacheNamingTest {

    @Test
    fun full_sentence_and_roster_caches_never_collide() {
        // T0.5: omitted speaker defaults to Shubh (temple default voice).
        assertEquals(
            "donation_abc123_shubh.mp3",
            SarvamTtsClient.cacheFileName("abc123", roster = false)
        )
        assertEquals(
            "donation_abc123_shubh_roster.mp3",
            SarvamTtsClient.cacheFileName("abc123", roster = true)
        )
        assertNotEquals(
            SarvamTtsClient.cacheFileName("abc123", roster = false),
            SarvamTtsClient.cacheFileName("abc123", roster = true)
        )
    }

    @Test
    fun speaker_variants_never_collide_ghost_voice_fix() {
        assertEquals(
            "donation_abc123_shubh.mp3",
            SarvamTtsClient.cacheFileName("abc123", roster = false, speaker = "shubh")
        )
        assertEquals(
            "donation_abc123_kavitha_roster.mp3",
            SarvamTtsClient.cacheFileName("abc123", roster = true, speaker = "kavitha")
        )
        assertNotEquals(
            SarvamTtsClient.cacheFileName("abc123", roster = false, speaker = "priya"),
            SarvamTtsClient.cacheFileName("abc123", roster = false, speaker = "shubh")
        )
        // Legacy aliases resolve through normalization too.
        assertEquals(
            SarvamTtsClient.cacheFileName("abc123", roster = false, speaker = "kavitha"),
            SarvamTtsClient.cacheFileName("abc123", roster = false, speaker = "meera")
        )
    }

    @Test
    fun legacy_slot_is_speaker_agnostic_and_distinct_from_sarvam() {
        assertEquals(
            "donation_abc123.mp3",
            SarvamTtsClient.legacyCacheFileName("abc123", roster = false)
        )
        assertEquals(
            "donation_abc123_roster.mp3",
            SarvamTtsClient.legacyCacheFileName("abc123", roster = true)
        )
        assertNotEquals(
            SarvamTtsClient.legacyCacheFileName("abc123", roster = false),
            SarvamTtsClient.cacheFileName("abc123", roster = false, speaker = "priya")
        )
    }

    @Test
    fun migrateLegacyToSpeaker_attributes_legacy_files() {
        val dir = java.nio.file.Files.createTempDirectory("audio-migrate").toFile()
        try {
            java.io.File(dir, "donation_id1.mp3").writeBytes(byteArrayOf(1))
            java.io.File(dir, "donation_id1_roster.mp3").writeBytes(byteArrayOf(2))
            java.io.File(dir, "donation_id2_shubh.mp3").writeBytes(byteArrayOf(3))
            java.io.File(dir, "temple_chime.wav").writeBytes(byteArrayOf(4))
            val done = SarvamTtsClient.migrateLegacyToSpeaker(dir, "shubh")
            kotlin.test.assertEquals(2, done)
            kotlin.test.assertTrue(java.io.File(dir, "donation_id1_shubh.mp3").exists())
            kotlin.test.assertTrue(java.io.File(dir, "donation_id1_shubh_roster.mp3").exists())
            kotlin.test.assertTrue(java.io.File(dir, "donation_id2_shubh.mp3").exists())
            kotlin.test.assertTrue(java.io.File(dir, "temple_chime.wav").exists())
            kotlin.test.assertTrue(!java.io.File(dir, "donation_id1.mp3").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun deleteDonationFiles_removes_all_variants_only() {
        val dir = java.nio.file.Files.createTempDirectory("audio-delete").toFile()
        try {
            java.io.File(dir, "donation_id1_priya.mp3").writeBytes(byteArrayOf(1))
            java.io.File(dir, "donation_id1_shubh_roster.mp3").writeBytes(byteArrayOf(2))
            java.io.File(dir, "donation_id1.mp3").writeBytes(byteArrayOf(3))
            java.io.File(dir, "donation_other_priya.mp3").writeBytes(byteArrayOf(4))
            java.io.File(dir, "temple_chime.wav").writeBytes(byteArrayOf(5))
            val deleted = SarvamTtsClient.deleteDonationFiles(dir, "id1")
            kotlin.test.assertEquals(3, deleted)
            kotlin.test.assertTrue(java.io.File(dir, "donation_other_priya.mp3").exists())
            kotlin.test.assertTrue(java.io.File(dir, "temple_chime.wav").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cas_slots_are_hash_namespaced_and_round_trip() {
        assertEquals(
            "audio_27b47756fd423f48871ac61ae97d6e2e6457a5852535da434528308f94d4eb1d.mp3",
            SarvamTtsClient.casFileName("27b47756fd423f48871ac61ae97d6e2e6457a5852535da434528308f94d4eb1d")
        )
        kotlin.test.assertTrue(SarvamTtsClient.isCasHash("a".repeat(64)))
        // Donation ids, short hex, and uppercase never qualify (never write garbage names).
        kotlin.test.assertFalse(SarvamTtsClient.isCasHash("abc123"))
        kotlin.test.assertFalse(SarvamTtsClient.isCasHash("A".repeat(64)))
        kotlin.test.assertFalse(SarvamTtsClient.isCasHash("donation_abc123_shubh.mp3"))
    }

    @Test
    fun normalizeSarvamSpeaker_maps_legacy_and_defaults_correctly() {
        assertEquals("kavitha", normalizeSarvamSpeaker("meera"))
        assertEquals("aditya", normalizeSarvamSpeaker("arvind"))
        // T0.5: stored choices pass through (never force-migrated)…
        assertEquals("priya", normalizeSarvamSpeaker("priya"))
        assertEquals("shubh", normalizeSarvamSpeaker("shubh"))
        assertEquals("pooja", normalizeSarvamSpeaker("pooja"))
        assertEquals("ratan", normalizeSarvamSpeaker("ratan"))
        assertEquals("kavitha", normalizeSarvamSpeaker("kavitha"))
        // …while unset/corrupt values land on Shubh (temple default).
        assertEquals("shubh", normalizeSarvamSpeaker(null))
        assertEquals("shubh", normalizeSarvamSpeaker("  "))
        assertEquals("shubh", normalizeSarvamSpeaker("invalid_speaker"))
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
