package com.shankaravam.festival.core.tts

import android.content.Context
import android.util.Base64
import com.shankaravam.festival.data.remote.SarvamApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

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
        speaker: String = "meera",
        roster: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        cachedFile(donationId, roster)?.let { return@withContext it }

        val payload = JSONObject()
            .put("inputs", JSONArray().put(text))
            .put("target_language_code", "te-IN")
            .put("speaker", speaker)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val raw = api.synthesize(apiKey.trim(), payload).string()
        val audioBase64 = runCatching {
            JSONObject(raw).getJSONArray("audios").getString(0)
        }.getOrNull() ?: throw IOException("Unexpected Sarvam response")
        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        if (bytes.isEmpty()) throw IOException("Empty audio from Sarvam")

        val file = File(audioDir(), cacheFileName(donationId, roster))
        file.writeBytes(bytes)
        file
    }

    private fun audioDir(): File =
        File(context.cacheDir, "audio").apply { if (!exists()) mkdirs() }

    companion object {
        /** Pure filename mapping — unit-tested (no Android needed). */
        fun cacheFileName(donationId: String, roster: Boolean): String =
            if (roster) "donation_${donationId}_roster.mp3" else "donation_${donationId}.mp3"
    }
}
