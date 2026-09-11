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
    fun cachedFile(donationId: String): File? {
        val file = File(audioDir(), "donation_${donationId}.mp3")
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun getOrGenerateAudio(
        donationId: String,
        text: String,
        apiKey: String,
        speaker: String = "meera"
    ): File = withContext(Dispatchers.IO) {
        cachedFile(donationId)?.let { return@withContext it }

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

        val file = File(audioDir(), "donation_${donationId}.mp3")
        file.writeBytes(bytes)
        file
    }

    private fun audioDir(): File =
        File(context.cacheDir, "audio").apply { if (!exists()) mkdirs() }
}
