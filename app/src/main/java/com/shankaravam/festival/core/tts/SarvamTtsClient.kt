package com.shankaravam.festival.core.tts

import android.content.Context
import android.util.Base64
import com.shankaravam.festival.data.remote.SarvamApiService
import com.shankaravam.festival.data.remote.SarvamErrorParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Normalizes speaker names to valid Sarvam bulbul:v3 speakers.
 * Maps legacy "meera" -> "kavitha" and "arvind" -> "aditya".
 * Defaults to "priya" (official recommended female speaker for Telugu).
 */
fun normalizeSarvamSpeaker(raw: String?): String = when (raw?.lowercase()?.trim()) {
    "priya" -> "priya"
    "shubh" -> "shubh"
    "kavitha", "meera" -> "kavitha"
    "aditya", "arvind" -> "aditya"
    "ratan" -> "ratan"
    "neha" -> "neha"
    "ishita" -> "ishita"
    "mani" -> "mani"
    "vijay" -> "vijay"
    "ritu" -> "ritu"
    "roopa" -> "roopa"
    "suhani" -> "suhani"
    "pooja" -> "pooja"
    "ashutosh" -> "ashutosh"
    "rehan" -> "rehan"
    "rohan" -> "rohan"
    "varun" -> "varun"
    else -> "priya"
}

/**
 * Cloud voice (plan §13). Audio lands ONLY in local disk cache —
 * never Firebase Storage (AGENTS.md Rule #2). Throws on any failure so the
 * DualTtsEngine can fall back to native TTS silently.
 */
class SarvamTtsClient(
    private val context: Context,
    private val api: SarvamApiService = SarvamApiService.create()
) {
    /**
     * Full-sentence and roster recordings are cached separately
     * (`donation_{id}.mp3` vs `donation_{id}_roster.mp3`) so switching
     * announcement style never plays the wrong recording.
     */
    fun cachedFile(donationId: String, roster: Boolean = false): File? {
        val file = File(audioDir(), cacheFileName(donationId, roster))
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun getOrGenerateAudio(
        donationId: String,
        text: String,
        apiKey: String,
        speaker: String = "priya",
        roster: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        cachedFile(donationId, roster)?.let { return@withContext it }

        val normSpeaker = normalizeSarvamSpeaker(speaker)
        val payload = JSONObject()
            .put("text", text)
            .put("language_code", "te-IN")
            .put("speaker", normSpeaker)
            .put("model", "bulbul:v3")
            .put("output_audio_codec", "mp3")
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val raw = try {
            api.synthesize(apiKey.trim(), payload).string()
        } catch (e: Throwable) {
            throw IOException(SarvamErrorParser.parse(e), e)
        }

        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IOException("Unexpected Sarvam response (not JSON): ${raw.take(100)}")

        val audioBase64 = runCatching {
            if (json.has("audios")) {
                json.getJSONArray("audios").getString(0)
            } else if (json.has("audio")) {
                json.getString("audio")
            } else null
        }.getOrNull() ?: throw IOException("Missing audio in Sarvam response: ${raw.take(100)}")

        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        if (bytes.isEmpty()) throw IOException("Empty audio from Sarvam")

        val file = File(audioDir(), cacheFileName(donationId, roster))
        file.writeBytes(bytes)
        file
    }

    private fun audioDir(): File =
        File(context.cacheDir, "audio").apply { if (!exists()) mkdirs() }

    /**
     * P4 cache ceiling: drops clips older than [maxAgeDays], then the oldest
     * beyond [maxFiles]. Only our `donation_*.mp3` files; never throws.
     * Returns the deleted count.
     */
    fun pruneCache(excludeIds: Set<String>, maxFiles: Int, maxAgeDays: Int): Int = runCatching {
        val cutoff = System.currentTimeMillis() - maxAgeDays * 24L * 60L * 60L * 1000L
        val excluded = excludeIds.flatMap {
            setOf(cacheFileName(it, false), cacheFileName(it, true))
        }.toSet()
        val ours = audioDir().listFiles()
            ?.filter { it.isFile && it.name.startsWith("donation_") && it.name.endsWith(".mp3") }
            .orEmpty()
        var deleted = 0
        selectPruneVictims(ours, maxFiles, cutoff, excluded).forEach {
            if (runCatching { it.delete() }.getOrDefault(false)) deleted++
        }
        deleted
    }.getOrDefault(0)

    companion object {
        /** Pure filename mapping — unit-tested (no Android needed). */
        fun cacheFileName(donationId: String, roster: Boolean): String =
            if (roster) "donation_${donationId}_roster.mp3" else "donation_${donationId}.mp3"

        /** Pure victim selection — unit-tested (no Android needed). */
        fun selectPruneVictims(
            files: List<File>,
            maxFiles: Int,
            cutoffMillis: Long,
            excludeNames: Set<String>
        ): List<File> {
            val eligible = files.filter { it.name !in excludeNames }
            val aged = eligible.filter { it.lastModified() < cutoffMillis }
            val rest = (eligible - aged.toSet()).sortedBy { it.lastModified() }
            val overCount = (rest.size - maxFiles).coerceAtLeast(0)
            return aged + rest.take(overCount)
        }
    }
}
