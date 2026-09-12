package com.shankaravam.festival.core.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TempleChimeTest {

    private fun wav(): ByteArray = synthesizeBellWav()

    @Test
    fun wav_has_valid_header_and_expected_size() {
        val bytes = wav()
        val samples = (CHIME_SAMPLE_RATE * CHIME_DURATION_MILLIS / 1000).toInt()
        assertEquals(44 + samples * 2, bytes.size)
        assertEquals("RIFF", bytes.sliceArray(0..3).toString(Charsets.US_ASCII))
        assertEquals("WAVE", bytes.sliceArray(8..11).toString(Charsets.US_ASCII))
        assertEquals("fmt ", bytes.sliceArray(12..15).toString(Charsets.US_ASCII))
        assertEquals("data", bytes.sliceArray(36..39).toString(Charsets.US_ASCII))
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, buf.getShort(20).toInt()) // PCM
        assertEquals(1, buf.getShort(22).toInt()) // mono
        assertEquals(CHIME_SAMPLE_RATE, buf.getInt(24))
        assertEquals(16, buf.getShort(34).toInt()) // bits per sample
    }

    @Test
    fun bell_is_audible_and_decays_to_silence() {
        val bytes = wav()
        val pcm = ShortArray((bytes.size - 44) / 2) {
            ByteBuffer.wrap(bytes, 44 + it * 2, 2).order(ByteOrder.LITTLE_ENDIAN).short
        }
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak > 10_000, "bell must be clearly audible (peak=$peak)")
        // First 100 ms carry far more energy than the last 100 ms (decay).
        val head = pcm.take(CHIME_SAMPLE_RATE / 10).sumOf { it.toLong() * it }
        val tail = pcm.takeLast(CHIME_SAMPLE_RATE / 10).sumOf { it.toLong() * it }
        assertTrue(head > tail * 10, "bell must decay (head=$head tail=$tail)")
        assertTrue(abs(pcm.last().toInt()) < 500, "tail must fade to ~zero for a clean handoff")
    }

    @Test
    fun ensure_bakes_once_and_reuses() {
        val dir = createTempDirectory("chime").toFile()
        val first = ensureChimeFile(dir)
        assertNotNull(first)
        assertEquals(CHIME_FILE_NAME, first.name)
        val stamped = first.lastModified()
        Thread.sleep(1100)
        val second = ensureChimeFile(dir)
        assertEquals(first.absolutePath, second?.absolutePath)
        assertEquals(stamped, second?.lastModified())
    }

    @Test
    fun ensure_degrades_to_null_instead_of_throwing() {
        // A regular file passed as the dir: every write fails deterministically.
        // The engine must get null (speech plays chime-free), never a crash.
        val notADir = kotlin.io.path.createTempFile("chime-blocker").toFile()
        assertEquals(null, ensureChimeFile(notADir))
    }
}
