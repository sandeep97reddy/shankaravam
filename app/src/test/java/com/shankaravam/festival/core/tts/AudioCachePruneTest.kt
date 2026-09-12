package com.shankaravam.festival.core.tts

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
