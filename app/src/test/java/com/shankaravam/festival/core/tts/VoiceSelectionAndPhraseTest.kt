package com.shankaravam.festival.core.tts

import com.shankaravam.festival.domain.model.NativeVoiceInfo
import com.shankaravam.festival.domain.model.pickBestTeluguVoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class VoiceSelectionAndPhraseTest {

    @Test
    fun pickBestTeluguVoice_returns_null_when_list_empty() {
        assertNull(pickBestTeluguVoice(emptyList()))
    }

    @Test
    fun pickBestTeluguVoice_prefers_network_voice_over_embedded() {
        val embedded = NativeVoiceInfo(name = "te-in-x-embedded-network", isNetwork = false, quality = 400)
        val network = NativeVoiceInfo(name = "te-in-x-network-network", isNetwork = true, quality = 300)

        val picked = pickBestTeluguVoice(listOf(embedded, network))
        assertEquals(network, picked)
    }

    @Test
    fun pickBestTeluguVoice_picks_highest_quality_network_voice() {
        val lowNet = NativeVoiceInfo(name = "te-in-net-low", isNetwork = true, quality = 200)
        val highNet = NativeVoiceInfo(name = "te-in-net-high", isNetwork = true, quality = 500)
        val midNet = NativeVoiceInfo(name = "te-in-net-mid", isNetwork = true, quality = 300)

        val picked = pickBestTeluguVoice(listOf(lowNet, highNet, midNet))
        assertEquals(highNet, picked)
    }

    @Test
    fun pickBestTeluguVoice_falls_back_to_highest_quality_embedded_when_no_network() {
        val lowEmb = NativeVoiceInfo(name = "te-in-emb-low", isNetwork = false, quality = 100)
        val highEmb = NativeVoiceInfo(name = "te-in-emb-high", isNetwork = false, quality = 400)

        val picked = pickBestTeluguVoice(listOf(lowEmb, highEmb))
        assertEquals(highEmb, picked)
    }

    @Test
    fun nativeVoiceInfo_displayName_and_badgeLabel() {
        val netVoice = NativeVoiceInfo(name = "te-in-x-tfe-network", isNetwork = true, quality = 400)
        assertEquals("network", netVoice.displayName)
        assertEquals("🌐 Network", netVoice.badgeLabel)

        val embVoice = NativeVoiceInfo(name = "te-in-x-tfe-local", isNetwork = false, quality = 300)
        assertEquals("local", embVoice.displayName)
        assertEquals("💾 Offline", embVoice.badgeLabel)
    }

    @Test
    fun phraseCacheFileName_formats_safe_key_and_normalized_speaker() {
        val filename1 = SarvamTtsClient.phraseCacheFileName("intro_event123", "priya")
        assertEquals("phrase_intro_event123_priya.mp3", filename1)

        val filename2 = SarvamTtsClient.phraseCacheFileName("outro_event123", "shubh")
        assertEquals("phrase_outro_event123_shubh.mp3", filename2)
    }

    @Test
    fun phraseCacheFileName_sanitizes_special_characters_and_case() {
        val filename = SarvamTtsClient.phraseCacheFileName("Intro Phrase: #1 @Event!", "meera")
        // "meera" normalizes to "kavitha"
        // special chars become underscores
        assertEquals("phrase_intro_phrase___1__event__kavitha.mp3", filename)
    }

    @Test
    fun phraseCacheFileName_truncates_long_keys() {
        val longKey = "a".repeat(100)
        val filename = SarvamTtsClient.phraseCacheFileName(longKey, "ratan")
        val expectedKey = "a".repeat(50)
        assertEquals("phrase_${expectedKey}_ratan.mp3", filename)
    }

    @Test
    fun phraseCacheFileName_never_collides_across_speakers_or_keys() {
        val file1 = SarvamTtsClient.phraseCacheFileName("intro_1", "priya")
        val file2 = SarvamTtsClient.phraseCacheFileName("intro_1", "shubh")
        val file3 = SarvamTtsClient.phraseCacheFileName("outro_1", "priya")

        assertNotEquals(file1, file2)
        assertNotEquals(file1, file3)
        assertNotEquals(file2, file3)
    }
}
