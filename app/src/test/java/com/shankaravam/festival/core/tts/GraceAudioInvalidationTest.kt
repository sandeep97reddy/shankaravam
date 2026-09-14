package com.shankaravam.festival.core.tts

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T0.1 grace-edit invalidation: Sarvam clips are regenerable (delete),
 * human imports are irreplaceable (quarantine to `.bak-<timestamp>`).
 */
class GraceAudioInvalidationTest {

    @Test
    fun deleteSarvamClipFiles_removes_speaker_variants_preserves_human_slots() {
        val dir = Files.createTempDirectory("audio-grace-sarvam").toFile()
        try {
            File(dir, "donation_id1_priya.mp3").writeBytes(byteArrayOf(1))
            File(dir, "donation_id1_shubh_roster.mp3").writeBytes(byteArrayOf(2))
            File(dir, "donation_id1.mp3").writeBytes(byteArrayOf(3))
            File(dir, "donation_id1_roster.mp3").writeBytes(byteArrayOf(4))
            File(dir, "donation_other_priya.mp3").writeBytes(byteArrayOf(5))
            File(dir, "temple_chime.wav").writeBytes(byteArrayOf(6))

            val deleted = SarvamTtsClient.deleteSarvamClipFiles(dir, "id1")

            assertEquals(2, deleted)
            assertFalse(File(dir, "donation_id1_priya.mp3").exists())
            assertFalse(File(dir, "donation_id1_shubh_roster.mp3").exists())
            // Human slots survive for quarantine step.
            assertTrue(File(dir, "donation_id1.mp3").exists())
            assertTrue(File(dir, "donation_id1_roster.mp3").exists())
            assertTrue(File(dir, "donation_other_priya.mp3").exists())
            assertTrue(File(dir, "temple_chime.wav").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quarantineHumanImportFiles_renames_to_bak_preserving_bytes() {
        val dir = Files.createTempDirectory("audio-grace-quarantine").toFile()
        try {
            File(dir, "donation_id1.mp3").writeBytes(byteArrayOf(7, 8, 9))
            File(dir, "donation_id1_roster.mp3").writeBytes(byteArrayOf(10))
            File(dir, "donation_id1_priya.mp3").writeBytes(byteArrayOf(11))

            val now = 1_700_000_000_000L
            val quarantined = SarvamTtsClient.quarantineHumanImportFiles(dir, "id1", now)

            assertEquals(2, quarantined)
            assertFalse(File(dir, "donation_id1.mp3").exists())
            assertFalse(File(dir, "donation_id1_roster.mp3").exists())
            val bakFull = File(dir, "donation_id1.mp3.bak-$now")
            val bakRoster = File(dir, "donation_id1_roster.mp3.bak-$now")
            assertTrue(bakFull.exists())
            assertTrue(bakRoster.exists())
            assertTrue(bakFull.readBytes().contentEquals(byteArrayOf(7, 8, 9)))
            // Sarvam clip untouched by quarantine.
            assertTrue(File(dir, "donation_id1_priya.mp3").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quarantine_skips_missing_and_empty_slots() {
        val dir = Files.createTempDirectory("audio-grace-quarantine-empty").toFile()
        try {
            File(dir, "donation_id1.mp3").writeBytes(byteArrayOf())
            val done = SarvamTtsClient.quarantineHumanImportFiles(dir, "id1", 123L)
            assertEquals(0, done)
            // Empty file left alone (playback treats 0-byte as missing anyway).
            assertTrue(File(dir, "donation_id1.mp3").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun bak_files_are_exempt_from_playback_names_and_prune_filter() {
        // Playback uses exact-name lookup: legacy/cache names never equal a .bak name.
        val legacy = SarvamTtsClient.legacyCacheFileName("id1", false)
        val sarvam = SarvamTtsClient.cacheFileName("id1", false, "priya")
        val bak = "$legacy.bak-1700000000000"
        assertTrue(SarvamTtsClient.isBackupFile(bak))
        assertFalse(SarvamTtsClient.isBackupFile(legacy))
        assertFalse(SarvamTtsClient.isBackupFile(sarvam))
        assertFalse(bak == legacy)
        assertFalse(bak == sarvam)
        // Prune pre-filter (donation_*.mp3, no .bak) skips backups by construction.
        val names = listOf(legacy, sarvam, bak, "temple_chime.wav")
        val pruneCandidates = names.filter { it.startsWith("donation_") && it.endsWith(".mp3") && !SarvamTtsClient.isBackupFile(it) }
        assertTrue(legacy in pruneCandidates)
        assertTrue(sarvam in pruneCandidates)
        assertFalse(bak in pruneCandidates)
    }

    @Test
    fun deleteSarvamClipFiles_never_touches_bak_backups() {
        val dir = Files.createTempDirectory("audio-grace-bak-exempt").toFile()
        try {
            File(dir, "donation_id1.mp3.bak-111").writeBytes(byteArrayOf(1))
            File(dir, "donation_id1_priya.mp3").writeBytes(byteArrayOf(2))
            val deleted = SarvamTtsClient.deleteSarvamClipFiles(dir, "id1")
            assertEquals(1, deleted)
            assertTrue(File(dir, "donation_id1.mp3.bak-111").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun deleteDonationFiles_scrub_removes_bak_too() {
        val dir = Files.createTempDirectory("audio-grace-scrub").toFile()
        try {
            File(dir, "donation_id1_priya.mp3").writeBytes(byteArrayOf(1))
            File(dir, "donation_id1.mp3.bak-111").writeBytes(byteArrayOf(2))
            File(dir, "temple_chime.wav").writeBytes(byteArrayOf(3))
            val deleted = SarvamTtsClient.deleteDonationFiles(dir, "id1")
            assertEquals(2, deleted)
            assertTrue(File(dir, "temple_chime.wav").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun grace_sequence_sarvam_gone_human_bak_kept_replay_regenerates() {
        // Simulates ₹500 → ₹5,000 grace edit: Sarvam stale clips deleted,
        // human quarantined, so next ensureCached/playBest sees no file.
        val dir = Files.createTempDirectory("audio-grace-sequence").toFile()
        try {
            File(dir, "donation_id9_priya.mp3").writeBytes(byteArrayOf(1))
            File(dir, "donation_id9_shubh.mp3").writeBytes(byteArrayOf(2))
            File(dir, "donation_id9.mp3").writeBytes(byteArrayOf(3))
            val now = 1_700_000_000_001L
            val sarvam = SarvamTtsClient.deleteSarvamClipFiles(dir, "id9")
            val human = SarvamTtsClient.quarantineHumanImportFiles(dir, "id9", now)
            assertEquals(2, sarvam)
            assertEquals(1, human)
            // Exact-name playback slots are now empty → regenerates.
            assertFalse(File(dir, SarvamTtsClient.cacheFileName("id9", false, "priya")).exists())
            assertFalse(File(dir, SarvamTtsClient.legacyCacheFileName("id9", false)).exists())
            assertTrue(File(dir, "donation_id9.mp3.bak-$now").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
