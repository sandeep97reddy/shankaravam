package com.shankaravam.festival.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T0.5 voice lineup: Shubh default, Pooja secondary. Pure JVM.
 *
 * Fresh installs and corrupt values land on Shubh; stored user choices
 * (including Priya) are never force-migrated — that half is structural
 * (SessionPrefs normalizes but never rewrites stored values; normalize
 * passes known speakers through), pinned here at the pure-function level.
 */
class VoiceLineupTest {

    @Test
    fun picker_order_is_shubh_pooja_priya_kavitha_ratan() {
        assertEquals(
            listOf("shubh", "pooja", "priya", "kavitha", "ratan"),
            SARVAM_SPEAKER_ORDER
        )
    }

    @Test
    fun picker_labels_are_distinct_and_stable() {
        val labels = SARVAM_SPEAKER_ORDER.map(::sarvamPickerLabel)
        assertEquals(SARVAM_SPEAKER_ORDER.size, labels.toSet().size)
        assertEquals("🎙️ Shubh (Male)", sarvamPickerLabel("shubh"))
        assertEquals("🌸 Pooja (Female)", sarvamPickerLabel("pooja"))
        assertEquals("🌸 Priya (Female)", sarvamPickerLabel("priya"))
        assertEquals("🌸 Kavitha (Female)", sarvamPickerLabel("kavitha"))
        assertEquals("🎙️ Ratan (Male)", sarvamPickerLabel("ratan"))
    }

    @Test
    fun pooja_renders_hd_label_like_the_other_leads() {
        val label = VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "pooja", null, true).displayLabel()
        assertEquals("🌸 Pooja (Sarvam Cloud HD)", label)
    }

    @Test
    fun stored_priya_choice_is_preserved_never_rewritten_to_shubh() {
        // normalizeSarvamSpeaker is the single funnel for stored values
        // (SessionPrefs getter + setter, VoiceConfig label, engine lookups).
        assertEquals(
            "priya",
            com.shankaravam.festival.core.tts.normalizeSarvamSpeaker("priya")
        )
        assertTrue(
            VoiceConfig(VoiceEngineMode.SARVAM_CLOUD, "priya", null, true)
                .displayLabel().startsWith("🌸 Priya")
        )
    }
}
