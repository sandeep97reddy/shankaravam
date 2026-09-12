package com.shankaravam.festival.core.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Temple bell chime (feature #4): 800 ms, 22050 Hz mono 16-bit WAV. */
const val CHIME_SAMPLE_RATE = 22050
const val CHIME_DURATION_MILLIS = 800L
const val CHIME_FILE_NAME = "temple_chime.wav"

/**
 * Bell partials as (frequency Hz, gain, decay per second): a bright E5
 * fundamental with an inharmonic upper stack like a small temple bell.
 * Higher partials die faster, leaving a warm hum at the tail.
 */
private val CHIME_PARTIALS = listOf(
    Triple(659.25, 1.00, 3.2),
    Triple(1318.5, 0.55, 4.5),
    Triple(1807.0, 0.32, 6.0),
    Triple(2480.0, 0.20, 7.5),
    Triple(3560.0, 0.10, 9.5)
)

/**
 * Pure synthesis: returns a complete WAV file's bytes. Zero assets, zero
 * latency at play time — the bytes are baked once into cacheDir and then
 * ride the existing MediaPlayer path (focus, routing, ducking all free).
 */
fun synthesizeBellWav(): ByteArray {
    val samples = (CHIME_SAMPLE_RATE * CHIME_DURATION_MILLIS / 1000).toInt()
    val pcm = ShortArray(samples) { i ->
        val t = i.toDouble() / CHIME_SAMPLE_RATE
        var v = 0.0
        for ((freq, gain, decay) in CHIME_PARTIALS) {
            v += gain * sin(2.0 * PI * freq * t) * exp(-decay * t)
        }
        // 2 ms attack avoids a DC click; 120 ms tail fades to zero so the
        // MediaPlayer handoff into speech has no audible seam.
        val attack = (i / (0.002 * CHIME_SAMPLE_RATE)).coerceIn(0.0, 1.0)
        val tailMs = CHIME_DURATION_MILLIS - i * 1000.0 / CHIME_SAMPLE_RATE
        val release = (tailMs / 120.0).coerceIn(0.0, 1.0)
        (v * 0.22 * attack * release * Short.MAX_VALUE).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    val dataBytes = pcm.size * 2
    val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray())
    buf.putInt(36 + dataBytes)
    buf.put("WAVE".toByteArray())
    buf.put("fmt ".toByteArray())
    buf.putInt(16)
    buf.putShort(1) // PCM
    buf.putShort(1) // mono
    buf.putInt(CHIME_SAMPLE_RATE)
    buf.putInt(CHIME_SAMPLE_RATE * 2) // byte rate
    buf.putShort(2) // block align
    buf.putShort(16) // bits per sample
    buf.put("data".toByteArray())
    buf.putInt(dataBytes)
    pcm.forEach { buf.putShort(it) }
    return buf.array()
}

/**
 * Thin I/O: bakes the chime into [dir] once, returns the file. Never throws —
 * null means "play speech without the chime".
 */
fun ensureChimeFile(dir: File): File? = runCatching {
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, CHIME_FILE_NAME)
    if (!file.exists() || file.length() == 0L) {
        file.writeBytes(synthesizeBellWav())
    }
    if (file.length() > 0L) file else null
}.getOrNull()
