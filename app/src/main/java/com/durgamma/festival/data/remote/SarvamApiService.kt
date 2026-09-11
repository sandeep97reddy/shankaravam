package com.durgamma.festival.data.remote

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
