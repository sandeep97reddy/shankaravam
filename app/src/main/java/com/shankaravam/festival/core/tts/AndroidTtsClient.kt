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
class AndroidTtsClient(context: Context) : TextToSpeech.OnInitListener {

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
            val result = engine.setLanguage(Locale.Builder().setLanguage("te").setRegion("IN").build())
            _ready.value = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
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

    /** Fire-and-forget speak. Returns false when offline TTS is unavailable. */
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
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            pending.remove(utteranceId)
            onError()
            return false
        }
        return true
    }

    fun stop() {
        tts?.stop()
        pending.clear()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
