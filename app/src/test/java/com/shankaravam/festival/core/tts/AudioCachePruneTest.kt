package com.shankaravam.festival.core.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class AudioCachePruneTest {

    private fun clip(dir: File, name: String, ageDays: Long): File {
        val file = File(dir, name)
        file.writeBytes(byteArrayOf(1, 2, 3))
        file.setLastModified(System.currentTimeMillis() - ageDays * 24L * 60L * 60L * 1000L)
        return file
    }

    @Test
    fun aged_files_go_first_then_oldest_beyond_cap_and_excluded_survive() {
        val dir = Files.createTempDirectory("audio-prune").toFile()
        try {
            clip(dir, "donation_old.mp3", 30)
            clip(dir, "donation_live.mp3", 30)
            clip(dir, "donation_mid1.mp3", 2)
            clip(dir, "donation_mid2.mp3", 1)
            clip(dir, "donation_fresh.mp3", 0)
            // Caller (pruneCache) pre-filters to donation_*.mp3; pure fn takes mp3s.
            val mp3s = dir.listFiles()!!
                .filter { it.name.startsWith("donation_") && it.name.endsWith(".mp3") }

            val cutoff = System.currentTimeMillis() - 20L * 24L * 60L * 60L * 1000L
            val victims = SarvamTtsClient.selectPruneVictims(
                files = mp3s,
                maxFiles = 2,
                cutoffMillis = cutoff,
                excludeNames = setOf("donation_live.mp3")
            ).map { it.name }.toSet()

            // Aged non-excluded clip always goes; excluded clip survives any age.
            assertTrue("donation_old.mp3" in victims)
            assertTrue("donation_live.mp3" !in victims)
            // 3 fresh remain vs cap 2 → oldest of the rest joins.
            assertTrue("donation_mid1.mp3" in victims)
            assertTrue("donation_mid2.mp3" !in victims)
            assertTrue("donation_fresh.mp3" !in victims)
            assertEquals(2, victims.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun prunable_filter_covers_phrases_and_spares_backups_chime_and_test() {
        // T0.4: phrase clips join the ceiling; everything else keeps its exemption.
        assertTrue(SarvamTtsClient.isPrunableCacheFile("donation_abc_priya.mp3"))
        assertTrue(SarvamTtsClient.isPrunableCacheFile("donation_abc.mp3"))
        assertTrue(SarvamTtsClient.isPrunableCacheFile("phrase_intro_vinayaka_shubh.mp3"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("donation_abc.mp3.bak-1700000000000"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("phrase_intro_shubh.mp3.bak-1"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("temple_chime.wav"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("audio_test_sample.mp3"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("random_voice.mp3"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("donation_abc.wav"))
    }

    @Test
    fun aged_phrase_clips_are_pruned_live_donation_clips_survive() {
        val dir = Files.createTempDirectory("audio-prune-phrase").toFile()
        try {
            clip(dir, "phrase_intro_old_shubh.mp3", 30)
            clip(dir, "phrase_outro_old_priya.mp3", 30)
            clip(dir, "donation_live_priya.mp3", 30)
            clip(dir, "temple_chime.wav", 30)
            val candidates = dir.listFiles()!!
                .filter { SarvamTtsClient.isPrunableCacheFile(it.name) }

            val cutoff = System.currentTimeMillis() - 20L * 24L * 60L * 60L * 1000L
            val victims = SarvamTtsClient.selectPruneVictims(
                files = candidates,
                maxFiles = 10,
                cutoffMillis = cutoff,
                excludeNames = setOf("donation_live_priya.mp3")
            ).map { it.name }.toSet()

            assertTrue("phrase_intro_old_shubh.mp3" in victims)
            assertTrue("phrase_outro_old_priya.mp3" in victims)
            assertTrue("donation_live_priya.mp3" !in victims)
            assertEquals(2, victims.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun prunable_filter_covers_cas_slots_but_spares_test_sample_and_malformed() {
        // H1: Phase-3 CAS clips join the ceiling; the levels-check file and
        // malformed audio_* names stay exempt.
        val good = "audio_" + "a".repeat(64) + ".mp3"
        assertTrue(SarvamTtsClient.isPrunableCacheFile(good))
        assertTrue(SarvamTtsClient.isPrunableCacheFile("audio_" + "0123456789abcdef".repeat(4) + ".mp3"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("audio_test_sample.mp3"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("audio_short.mp3"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("audio_" + "z".repeat(64) + ".mp3"))
        assertFalse(SarvamTtsClient.isPrunableCacheFile("audio_" + "a".repeat(64) + ".mp3.bak-1"))
    }

    @Test
    fun aged_cas_clips_are_pruned_live_hashes_survive_via_exclusion() {
        // H1/H2: stale CAS goes under age pressure; live-queue hashes passed
        // as excludeHashes survive (exact-name match — prefix scan can't map
        // a hash back to a donation id).
        val liveHash = "b".repeat(64)
        val staleHash = "c".repeat(64)
        val liveName = SarvamTtsClient.casFileName(liveHash)
        val staleName = SarvamTtsClient.casFileName(staleHash)
        val dir = Files.createTempDirectory("audio-prune-cas").toFile()
        try {
            clip(dir, staleName, 30)
            clip(dir, liveName, 30)
            clip(dir, "donation_live_priya.mp3", 30)
            val candidates = dir.listFiles()!!
                .filter { SarvamTtsClient.isPrunableCacheFile(it.name) }
            assertEquals(3, candidates.size)

            val cutoff = System.currentTimeMillis() - 20L * 24L * 60L * 60L * 1000L
            val victims = SarvamTtsClient.selectPruneVictims(
                files = candidates,
                maxFiles = 10,
                cutoffMillis = cutoff,
                excludeNames = setOf(liveName, "donation_live_priya.mp3")
            ).map { it.name }.toSet()

            assertTrue(staleName in victims)
            assertTrue(liveName !in victims)
            assertEquals(1, victims.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun per_donation_scrub_never_touches_shared_phrases() {
        val dir = Files.createTempDirectory("audio-scrub-phrase").toFile()
        try {
            java.io.File(dir, "donation_id1_priya.mp3").writeBytes(byteArrayOf(1))
            java.io.File(dir, "phrase_intro_old_shubh.mp3").writeBytes(byteArrayOf(2))
            val deleted = SarvamTtsClient.deleteDonationFiles(dir, "id1")
            assertEquals(1, deleted)
            assertTrue(java.io.File(dir, "phrase_intro_old_shubh.mp3").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
