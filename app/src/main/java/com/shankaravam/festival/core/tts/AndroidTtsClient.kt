package com.shankaravam.festival.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline voice: Android native TTS, te-IN, unlimited + free (plan §13).
 * AudioAttributes (not the deprecated stream-type param) route speech to
 * STREAM_MUSIC so Bluetooth amplifiers just work.
 */
class AndroidTtsClient(
    context: Context,
    /**
     * Saved voice settings, applied once the engine finishes init so a
     * chosen voice/speed survives app restarts (not just live changes).
     * Returns (voiceNameOrNull, speechRate).
     */
    private val savedSettings: (() -> Pair<String?, Float>)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private data class Pending(val onDone: () -> Unit, val onError: () -> Unit)
    private val pending = ConcurrentHashMap<String, Pending>()

    init {
        tts = runCatching { TextToSpeech(context.applicationContext, this) }.getOrNull()
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            val teLocale = Locale.Builder().setLanguage("te").setRegion("IN").build()
            val result = runCatching { engine.setLanguage(teLocale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // If Telugu voice pack is not pre-installed on this device yet, fall back to default so audio still works
                val fallbackResult = runCatching { engine.setLanguage(Locale.getDefault()) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                _ready.value = fallbackResult != TextToSpeech.LANG_MISSING_DATA && fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                _ready.value = true
            }

            // Re-apply the user's saved voice + speed (Admin choices persist here).
            runCatching {
                savedSettings?.invoke()?.let { (voiceName, rate) ->
                    setSpeechRate(rate)
                    if (voiceName != null) setVoiceByName(voiceName)
                }
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { pending.remove(it)?.onDone?.invoke() }
                }

                @Deprecated("Legacy callback")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { pending.remove(it)?.onError?.invoke() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onError(utteranceId)
                }
            })
        }
    }

    /** Fire-and-forget speak. Returns false when offline TTS is unavailable. Never throws. */
    fun speak(
        text: String,
        onDone: () -> Unit = {},
        onError: () -> Unit = {}
    ): Boolean {
        val engine = tts ?: run { onError(); return false }
        if (!_ready.value) {
            onError()
            return false
        }
        val utteranceId = UUID.randomUUID().toString()
        pending[utteranceId] = Pending(onDone, onError)
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        if (result != TextToSpeech.SUCCESS) {
            pending.remove(utteranceId)
            onError()
            return false
        }
        return true
    }

    fun stop() {
        runCatching { tts?.stop() }
        pending.clear()
    }

    /** Returns all available Telugu voices installed on the device (e.g. Google/Samsung TTS). */
    fun getAvailableTeluguVoices(): List<String> {
        val engine = tts ?: return emptyList()
        return runCatching {
            engine.voices
                ?.filter { it.locale.language == "te" || it.name.contains("te-in", ignoreCase = true) }
                ?.map { it.name }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Sets the active native voice by name. */
    fun setVoiceByName(voiceName: String): Boolean {
        val engine = tts ?: return false
        return runCatching {
            val voice = engine.voices?.firstOrNull { it.name == voiceName } ?: return false
            engine.voice = voice
            true
        }.getOrDefault(false)
    }

    fun setSpeechRate(rate: Float) {
        runCatching { tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f)) }
    }

    fun setPitch(pitch: Float) {
        runCatching { tts?.setPitch(pitch.coerceIn(0.5f, 2.0f)) }
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
