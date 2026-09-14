package com.shankaravam.festival.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.shankaravam.festival.domain.model.NativeVoiceInfo
import com.shankaravam.festival.domain.model.pickBestTeluguVoice
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

    /** Locale that init settled on — resetVoice() returns here. */
    @Volatile private var defaultLocale: Locale? = null

    @Volatile private var activeVoiceIsNetwork = false

    private data class Pending(
        val onDone: () -> Unit,
        val onError: () -> Unit,
        val text: String = "",
        val isRetry: Boolean = false
    )
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
                // If Telugu voice pack is not pre-installed on this device yet, fall back to system default
                val fallbackResult = runCatching { engine.setLanguage(Locale.getDefault()) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (fallbackResult != TextToSpeech.LANG_MISSING_DATA && fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                    defaultLocale = Locale.getDefault()
                    _ready.value = true
                } else {
                    // Fallback to English (US) which is guaranteed on all Android devices
                    val enResult = runCatching { engine.setLanguage(Locale.US) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                    if (enResult != TextToSpeech.LANG_MISSING_DATA && enResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                        defaultLocale = Locale.US
                    }
                    _ready.value = enResult != TextToSpeech.LANG_MISSING_DATA && enResult != TextToSpeech.LANG_NOT_SUPPORTED
                }
            } else {
                defaultLocale = teLocale
                _ready.value = true
            }

            // Re-apply the user's saved voice + speed (Admin choices persist here).
            runCatching {
                val (savedVoice, rate) = savedSettings?.invoke() ?: (null to 1.0f)
                setSpeechRate(rate)
                if (!savedVoice.isNullOrBlank()) {
                    setVoiceByName(savedVoice)
                } else {
                    // F6: pick best Telugu voice (network first, then embedded) if user never picked
                    val best = pickBestTeluguVoice(getAvailableTeluguVoiceInfos())
                    if (best != null) {
                        setVoiceByName(best.name)
                    }
                }
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { pending.remove(it)?.onDone?.invoke() }
                }

                @Deprecated("Legacy callback")
                override fun onError(utteranceId: String?) {
                    handleSpeakError(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    handleSpeakError(utteranceId)
                }
            })
        }
    }

    private fun handleSpeakError(utteranceId: String?) {
        val p = utteranceId?.let { pending.remove(it) } ?: return
        if (activeVoiceIsNetwork && !p.isRetry && p.text.isNotBlank()) {
            val engine = tts
            if (engine != null) {
                // Fall back to embedded voice for this utterance
                val fallback = getAvailableTeluguVoiceInfos().firstOrNull { !it.isNetwork }
                if (fallback != null) setVoiceByName(fallback.name) else resetVoice()
                val retryId = UUID.randomUUID().toString()
                pending[retryId] = Pending(p.onDone, p.onError, p.text, isRetry = true)
                val retryResult = runCatching {
                    engine.speak(p.text, TextToSpeech.QUEUE_FLUSH, null, retryId)
                }.getOrDefault(TextToSpeech.ERROR)
                if (retryResult == TextToSpeech.SUCCESS) return
                pending.remove(retryId)
            }
        }
        p.onError()
    }

    /** Fire-and-forget speak. Returns false when offline TTS is unavailable. Never throws. */
    fun speak(
        text: String,
        onDone: () -> Unit = {},
        onError: () -> Unit = {}
    ): Boolean {
        val engine = tts ?: run { onError(); return false }
        if (!_ready.value) {
            // Emergency fallback: try setting English if Telugu was missing
            val recovered = runCatching {
                val res = engine.setLanguage(Locale.US)
                res != TextToSpeech.LANG_NOT_SUPPORTED && res != TextToSpeech.LANG_MISSING_DATA
            }.getOrDefault(false)
            if (recovered) {
                _ready.value = true
            } else {
                onError()
                return false
            }
        }
        val utteranceId = UUID.randomUUID().toString()
        pending[utteranceId] = Pending(onDone, onError, text, isRetry = false)
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        if (result != TextToSpeech.SUCCESS) {
            pending.remove(utteranceId)
            if (activeVoiceIsNetwork) {
                val fallback = getAvailableTeluguVoiceInfos().firstOrNull { !it.isNetwork }
                if (fallback != null) setVoiceByName(fallback.name) else resetVoice()
                val retryId = UUID.randomUUID().toString()
                pending[retryId] = Pending(onDone, onError, text, isRetry = true)
                val retryResult = runCatching {
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, retryId)
                }.getOrDefault(TextToSpeech.ERROR)
                if (retryResult == TextToSpeech.SUCCESS) return true
                pending.remove(retryId)
            }
            onError()
            return false
        }
        return true
    }

    fun stop() {
        runCatching { tts?.stop() }
        pending.clear()
    }

    /** F6: Returns structured voice info (network vs embedded, quality) for badges and smart selection. */
    fun getAvailableTeluguVoiceInfos(): List<NativeVoiceInfo> {
        val engine = tts ?: return emptyList()
        return runCatching {
            engine.voices
                ?.filter { it.locale.language == "te" || it.name.contains("te-in", ignoreCase = true) }
                ?.map { v ->
                    NativeVoiceInfo(
                        name = v.name,
                        isNetwork = v.isNetworkConnectionRequired,
                        quality = v.quality
                    )
                }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Returns all available Telugu voices installed on the device (e.g. Google/Samsung TTS). */
    fun getAvailableTeluguVoices(): List<String> =
        getAvailableTeluguVoiceInfos().map { it.name }

    /** Sets the active native voice by name. Blank names reset to default. */
    fun setVoiceByName(voiceName: String): Boolean {
        if (voiceName.isBlank()) return resetVoice()
        val engine = tts ?: return false
        return runCatching {
            val voice = engine.voices?.firstOrNull { it.name == voiceName } ?: return false
            engine.voice = voice
            activeVoiceIsNetwork = voice.isNetworkConnectionRequired
            true
        }.getOrDefault(false)
    }

    /**
     * Phase 2 live-reset (RC3 companion fix). Re-applies the init-settled
     * default locale, clearing any previously selected voice immediately —
     * "System Default" takes effect without an app restart. Never throws.
     */
    fun resetVoice(): Boolean {
        activeVoiceIsNetwork = false
        val engine = tts ?: return false
        return runCatching {
            val result = engine.setLanguage(
                defaultLocale ?: Locale.Builder().setLanguage("te").setRegion("IN").build()
            )
            result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
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
