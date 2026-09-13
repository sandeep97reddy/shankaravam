package com.shankaravam.festival.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

import android.os.Handler
import android.os.Looper

/**
 * Transient-may-duck focus (skill §4): temple background music dips while an
 * announcement plays. minSdk 26 → framework request, no compat library needed.
 */
class AudioFocusManager(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var activeRequest: AudioFocusRequest? = null

    @Synchronized
    fun request(): Boolean = runCatching {
        if (activeRequest != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ }, mainHandler)
            .build()
        val result = audioManager.requestAudioFocus(request)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            activeRequest = request
            return true
        }
        return false
    }.getOrDefault(false)

    @Synchronized
    fun abandon() {
        runCatching {
            activeRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                activeRequest = null
            }
        }
    }
}
