package com.shankaravam.festival.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Sarvam AI TTS (plan §13). No converter factory on purpose — the single
 * endpoint is hand-serialized (JSONObject) and hand-parsed (audios[0] base64),
 * keeping the dependency footprint to bare Retrofit.
 */
interface SarvamApiService {

    @POST("text-to-speech")
    suspend fun synthesize(
        @Header("api-subscription-key") apiKey: String,
        @Body body: RequestBody
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://api.sarvam.ai/"

        fun create(): SarvamApiService =
            Retrofit.Builder().baseUrl(BASE_URL).build().create(SarvamApiService::class.java)
    }
}

/**
 * Extracts descriptive error messages from Sarvam AI HTTP error responses.
 */
object SarvamErrorParser {
    private val messageRegex = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
    private val codeRegex = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"")

    fun parse(e: Throwable): String {
        if (e is retrofit2.HttpException) {
            val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            if (!raw.isNullOrBlank()) {
                val message = messageRegex.find(raw)?.groupValues?.get(1)
                    ?: codeRegex.find(raw)?.groupValues?.get(1)
                if (!message.isNullOrBlank()) {
                    return "HTTP ${e.code()}: $message"
                }
            }
            return "HTTP ${e.code()} (${e.message()})"
        }
        return e.message ?: "Network error"
    }
}
