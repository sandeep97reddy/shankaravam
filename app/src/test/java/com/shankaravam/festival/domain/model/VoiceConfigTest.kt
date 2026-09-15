package com.shankaravam.festival.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceConfigTest {

    @Test
    fun cloud_label_shows_speaker_when_key_present() {
        assertEquals(
            "🌸 Priya (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "priya", null, true).displayLabel()
        )
        assertEquals(
            "🎙️ Shubh (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "shubh", null, true).displayLabel()
        )
        assertEquals(
            "🌸 Kavitha (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "kavitha", null, true).displayLabel()
        )
        assertEquals(
            "🎙️ Ratan (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "ratan", null, true).displayLabel()
        )
    }

    @Test
    fun native_label_wins_over_key_in_offline_mode_rc3_regression() {
        // The old when{} checked hasKey first, so this stuck on Priya.
        assertEquals(
            "📱 Android System Voice (Offline)",
            VoiceConfig(VoiceEngineMode.OFFLINE_NATIVE, "priya", null, true).displayLabel()
        )
        assertEquals(
            "📱 Android Voice (local)",
            VoiceConfig(
                VoiceEngineMode.OFFLINE_NATIVE, "priya",
                "te-in-x-tem-local", true
            ).displayLabel()
        )
    }

    @Test
    fun cloud_without_key_warns_instead_of_showing_hd() {
        val label = VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "priya", null, false).displayLabel()
        assertTrue(label.startsWith("⚠️"))
    }

    @Test
    fun legacy_speaker_aliases_resolve_in_label() {
        assertEquals(
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "kavitha", null, true).displayLabel(),
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "meera", null, true).displayLabel()
        )
    }

    @Test
    fun status_line_is_mode_first() {
        assertTrue(
            VoiceConfig(VoiceEngineMode.OFFLINE_NATIVE, "priya", null, true)
                .statusLine().startsWith("Offline Android voice")
        )
        assertTrue(
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "priya", null, true)
                .statusLine().startsWith("✓")
        )
        assertTrue(
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "priya", null, false)
                .statusLine().contains("Settings")
        )
    }

    @Test
    fun cloud_label_shows_speaker_when_gateway_present_without_key() {
        assertEquals(
            "🎙️ Shubh (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "shubh", null, hasSarvamKey = false, hasGateway = true).displayLabel()
        )
        assertEquals(
            "🌸 Pooja (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "pooja", null, hasSarvamKey = false, hasGateway = true).displayLabel()
        )
    }

    @Test
    fun status_line_shows_gateway_active() {
        assertTrue(
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "shubh", null, hasSarvamKey = false, hasGateway = true)
                .statusLine().contains("Temple media gateway active")
        )
    }

    @Test
    fun gateway_quota_pill_is_distinct_from_device_pill() {
        val server = gatewayQuotaPillText()
        val device = quotaPillText(used = 20, max = 20, resetAt = 1_700_000_000_000L)
        kotlin.test.assertTrue(server.contains("Temple voice budget"))
        kotlin.test.assertTrue(device.contains("Cloud quota reached"))
        kotlin.test.assertNotEquals(server, device)
    }

    @Test
    fun voiceModeOf_defaults_to_cloud_and_parses_offline() {
        assertEquals(VoiceEngineMode.SARVAM_CLOUD, voiceModeOf(null))
        assertEquals(VoiceEngineMode.SARVAM_CLOUD, voiceModeOf("bogus"))
        assertEquals(VoiceEngineMode.OFFLINE_NATIVE, voiceModeOf("OFFLINE_NATIVE"))
        assertEquals(VoiceEngineMode.OFFLINE_NATIVE, voiceModeOf("offline_native"))
    }

    @Test
    fun quota_pill_names_usage_and_offline_cover() {
        val pill = quotaPillText(used = 20, max = 20, resetAt = 1_700_000_000_000L)
        assertTrue(pill.startsWith("⚠️"))
        assertTrue(pill.contains("20/20"))
        assertTrue(pill.contains("offline voice"))
    }

    @Test
    fun quota_pill_reflects_partial_usage() {
        val pill = quotaPillText(used = 7, max = 20, resetAt = 1_700_000_000_000L)
        assertTrue(pill.contains("7/20"))
    }

    @Test
    fun all_five_sarvam_speakers_render_hd_label_when_key_present() {
        val speakers = listOf(
            "shubh" to "🎙️ Shubh",
            "pooja" to "🌸 Pooja",
            "priya" to "🌸 Priya",
            "kavitha" to "🌸 Kavitha",
            "ratan" to "🎙️ Ratan"
        )
        for ((speaker, prefix) in speakers) {
            val config = VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, speaker, null, true)
            assertTrue(config.displayLabel().startsWith(prefix), speaker)
            assertTrue(config.displayLabel().contains("Sarvam Cloud HD"), speaker)
        }
    }

    @Test
    fun default_voice_config_speaks_shubh() {
        assertEquals("shubh", VoiceConfig().sarvamSpeaker)
        assertEquals(
            "🎙️ Shubh (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, VoiceConfig().sarvamSpeaker, null, true).displayLabel()
        )
    }

    @Test
    fun gateway_without_sign_in_warns_instead_of_promising_hd() {
        // Signed-out gateway must never promise HD — the engine needs a token.
        val label = VoiceConfig(
            VoiceEngineMode.SARVAM_CLOUD, "shubh", null,
            hasSarvamKey = false, hasGateway = true, hasSignIn = false
        ).displayLabel()
        assertTrue(label.startsWith("⚠️"))
        assertTrue(label.contains("sign in"))
        assertTrue(
            VoiceConfig(
                VoiceEngineMode.SARVAM_CLOUD, "shubh", null,
                hasSarvamKey = false, hasGateway = true, hasSignIn = false
            ).statusLine().contains("sign in")
        )
    }

    @Test
    fun speaker_names_are_case_and_whitespace_tolerant() {
        assertEquals(
            "🌸 Priya (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, " Priya ", null, true).displayLabel()
        )
        assertEquals(
            "🎙️ Shubh (Sarvam Cloud HD)",
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "SHUBH", null, true).displayLabel()
        )
    }
}
