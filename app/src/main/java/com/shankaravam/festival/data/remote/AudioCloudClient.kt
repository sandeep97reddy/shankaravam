package com.shankaravam.festival.data.remote

import com.shankaravam.festival.core.tts.TTS_TEMPLATE_VERSION
import com.shankaravam.festival.data.local.SessionPrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Phase-3 temple-gateway client (OkHttp only — no S3 SDK, no APK growth).
 *
 * Every call is best-effort and NEVER throws: null/Unavailable means "fall
 * back" (native TTS / local file). Playback paths never await this — only
 * background prefetch and workers call it. No `ListObjects` anywhere.
 */
class AudioCloudClient(
    private val prefs: SessionPrefs,
    private val client: OkHttpClient = defaultClient()
) {
    /** Resolve outcome: bytes, explicit server-quota (distinct pill), or fallback. */
    sealed interface AudioResolve {
        data class Ready(val hash: String, val bytes: ByteArray, val cached: Boolean) : AudioResolve
        data object ServerQuota : AudioResolve
        data object Unavailable : AudioResolve
    }

    /** Receipt PUT outcome: path to store, retry (429/5xx/timeout), or give up. */
    sealed interface ReceiptUpload {
        data class Done(val path: String) : ReceiptUpload
        data object Retry : ReceiptUpload
        data object GiveUp : ReceiptUpload
    }

    companion object {
        /** Gateway audio objects cap (~5 min of MP3 — far above a 30 KB clip). */
        const val MAX_AUDIO_BYTES = 5L * 1024L * 1024L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Null when sharing is unconfigured (blank gateway URL) — caller falls back. */
    fun baseUrl(): String? =
        prefs.gatewayBaseUrl.takeIf { it.isNotBlank() }

    /** Absolute display/download URL for a stored gateway path. Null when unconfigured. */
    fun absoluteUrl(path: String): String? =
        baseUrl()?.let { "${it}/${path.trimStart('/')}" }

    /**
     * `POST /v1/audio/resolve` then `GET` the returned URL (same-domain,
     * auth-gated). The [hash] MUST be `audioHashFor` over the client-built
     * effective-amount text — the Worker recomputes and rejects mismatches.
     */
    suspend fun resolveAudio(
        idToken: String?,
        eventId: String,
        donationId: String,
        text: String,
        language: String,
        speaker: String,
        roster: Boolean,
        hash: String
    ): AudioResolve {
        val base = baseUrl() ?: return AudioResolve.Unavailable
        if (idToken.isNullOrBlank()) return AudioResolve.Unavailable
        return runCatching {
            val body = JSONObject()
                .put("eventId", eventId)
                .put("donationId", donationId)
                .put("text", text)
                .put("language", language)
                .put("speaker", speaker)
                .put("roster", roster)
                .put("hash", hash)
                .put("templateVersion", TTS_TEMPLATE_VERSION)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val resolveReq = Request.Builder()
                .url("$base/v1/audio/resolve")
                .header("Authorization", "Bearer $idToken")
                .post(body)
                .build()
            val resolved = client.newCall(resolveReq).execute().use { resp ->
                when (resp.code) {
                    200 -> {
                        val json = JSONObject(resp.body?.string() ?: "")
                        Triple(
                            json.optString("hash", ""),
                            json.optString("url", ""),
                            json.optBoolean("cached", false)
                        )
                    }
                    429 -> return AudioResolve.ServerQuota
                    else -> return AudioResolve.Unavailable
                }
            }
            val (retHash, url, cached) = resolved
            if (retHash != hash || url.isBlank()) return AudioResolve.Unavailable
            val getReq = Request.Builder()
                .url("$base$url?eventId=$eventId")
                .header("Authorization", "Bearer $idToken")
                .get()
                .build()
            val bytes = client.newCall(getReq).execute().use { resp ->
                if (resp.code != 200) return AudioResolve.Unavailable
                resp.body?.bytes() ?: return AudioResolve.Unavailable
            }
            if (bytes.isEmpty() || bytes.size > MAX_AUDIO_BYTES) return AudioResolve.Unavailable
            AudioResolve.Ready(retHash, bytes, cached)
        }.getOrDefault(AudioResolve.Unavailable)
    }

    /**
     * `PUT /v1/receipts/{eventId}/{expenseId}.webp`. Returns the canonical
     * gateway path to store (NOT bytes, NOT the local path).
     */
    suspend fun uploadReceipt(
        idToken: String?,
        eventId: String,
        expenseId: String,
        webpBytes: ByteArray
    ): ReceiptUpload {
        val base = baseUrl() ?: return ReceiptUpload.GiveUp
        if (idToken.isNullOrBlank()) return ReceiptUpload.Retry
        if (webpBytes.isEmpty()) return ReceiptUpload.GiveUp
        return runCatching {
            val req = Request.Builder()
                .url("$base/v1/receipts/$eventId/$expenseId.webp")
                .header("Authorization", "Bearer $idToken")
                .put(webpBytes.toRequestBody("image/webp".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    200 -> {
                        val path = JSONObject(resp.body?.string() ?: "").optString("url", "")
                        if (path.isBlank()) ReceiptUpload.GiveUp else ReceiptUpload.Done(path)
                    }
                    429, 502, 503 -> ReceiptUpload.Retry
                    in 500..599 -> ReceiptUpload.Retry
                    else -> ReceiptUpload.GiveUp
                }
            }
        }.getOrDefault(ReceiptUpload.Retry)
    }
}
