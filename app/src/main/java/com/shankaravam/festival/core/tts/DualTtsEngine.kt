package com.shankaravam.festival.core.tts

import android.content.Context
import android.media.MediaPlayer
import com.shankaravam.festival.core.audio.AudioFocusManager
import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.model.Donation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dual engine (skill §1): Sarvam mp3 when a key exists and the file can be
 * produced, Android native TTS otherwise — the caller never branches.
 * Donation entry never waits on this: [ensureCached] is suspend/IO and the
 * queue prefetches in the background.
 */
class DualTtsEngine(
    context: Context,
    val native: AndroidTtsClient,
    private val sarvam: SarvamTtsClient,
    private val audioFocus: AudioFocusManager
) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    val nativeReady: StateFlow<Boolean> = native.ready

    fun cachedFile(donationId: String, roster: Boolean = false): File? =
        sarvam.cachedFile(donationId, roster)

    /** P4 ceiling enforcement; runs on the caller's (IO) thread. Never throws. */
    fun pruneCache(excludeIds: Set<String>, maxFiles: Int, maxAgeDays: Int): Int =
        runCatching { sarvam.pruneCache(excludeIds, maxFiles, maxAgeDays) }.getOrDefault(0)

    /**
     * Best-effort background caching. Reports PREPARING/READY/FAILED through
     * [onStatus] (persisted by the caller); returns the file or null.
     * [roster] selects the roster-line recording vs the full sentence.
     */
    suspend fun ensureCached(
        donation: Donation,
        eventName: String,
        language: AnnouncementLanguage,
        apiKey: String,
        onStatus: suspend (AudioStatus) -> Unit,
        roster: Boolean = false,
        speaker: String = "meera",
        onError: (Throwable) -> Unit = {}
    ): File? {
        sarvam.cachedFile(donation.id, roster)?.let { return it }
        if (apiKey.isBlank()) return null
        runCatching { onStatus(AudioStatus.PREPARING) }
        return try {
            val text = if (roster) {
                buildRosterItemAnnouncement(donation, language)
            } else {
                buildDonationAnnouncement(donation, eventName, language)
            }
            val file = sarvam.getOrGenerateAudio(donation.id, text, apiKey, speaker = speaker, roster = roster)
            onStatus(AudioStatus.READY)
            file
        } catch (e: Exception) {
            runCatching { onError(e) }
            runCatching { onStatus(AudioStatus.FAILED) }
            null
        }
    }

    /** Instant offline speech (zero network, zero latency). Never throws. */
    fun speakNative(text: String, onDone: () -> Unit, onError: () -> Unit) {
        val safeText = text.ifBlank { AUDIO_TEST_LINE }
        runCatching { audioFocus.request() }
        runCatching {
            native.speak(
                safeText,
                onDone = { runCatching { audioFocus.abandon() }; runCatching { onDone() } },
                onError = { runCatching { audioFocus.abandon() }; runCatching { onError() } }
            )
        }.onFailure { runCatching { audioFocus.abandon() }; runCatching { onError() } }
    }

    /** Highest quality for this row: cached Sarvam mp3, else native TTS. Never throws. */
    fun playBest(
        donation: Donation,
        eventName: String,
        language: AnnouncementLanguage,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        runCatching {
            val cached = sarvam.cachedFile(donation.id)
            if (cached != null) {
                playFile(cached, onDone, onError)
            } else {
                speakNative(buildDonationAnnouncement(donation, eventName, language), onDone, onError)
            }
        }.onFailure {
            runCatching { speakNative(AUDIO_TEST_LINE, onDone, onError) }
                .onFailure { runCatching { onError() } }
        }
    }

    /**
     * Plays a crisp roster line: cloud/imported roster recording when cached,
     * else the imported full clip for that row, else native TTS. Never throws.
     */
    fun playRosterItem(
        donation: Donation,
        language: AnnouncementLanguage,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        runCatching {
            sarvam.cachedFile(donation.id, roster = true)?.let {
                playFile(it, onDone, onError)
                return
            }
            // Imported full clip (e.g. WhatsApp share) doubles as the roster line.
            sarvam.cachedFile(donation.id)?.let {
                playFile(it, onDone, onError)
                return
            }
            speakNative(buildRosterItemAnnouncement(donation, language), onDone, onError)
        }.onFailure {
            runCatching { speakNative(AUDIO_TEST_LINE, onDone, onError) }
                .onFailure { runCatching { onError() } }
        }
    }

    /** Speaks opening intro or closing outro phrase over the audio focus channel. */
    fun speakPhrase(
        text: String,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        speakNative(text, onDone, onError)
    }

    fun playFile(file: File, onDone: () -> Unit, onError: () -> Unit) {
        runCatching { stopPlayback() }
        runCatching { audioFocus.request() }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(audioFocus.attributes)
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    runCatching { audioFocus.abandon() }
                    runCatching { onDone() }
                }
                setOnErrorListener { _, _, _ ->
                    runCatching { audioFocus.abandon() }
                    runCatching { onError() }
                    true
                }
                setOnPreparedListener { runCatching { it.start() }.onFailure { runCatching { onError() } } }
                prepareAsync()
            }
        } catch (_: Exception) {
            runCatching { audioFocus.abandon() }
            runCatching { onError() }
        }
    }

    fun pausePlayback(): Boolean =
        runCatching {
            val active = player
            if (active?.isPlaying == true) {
                active.pause()
                true
            } else false
        }.getOrDefault(false)

    /** True while a cached file is audibly playing (used by queue resume-poll). */
    fun isFilePlaying(): Boolean =
        runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun resumePlayback(): Boolean {
        return runCatching {
            val paused = player
            if (paused != null && runCatching { !paused.isPlaying }.getOrDefault(false)) {
                audioFocus.request()
                paused.start()
                true
            } else false
        }.getOrDefault(false)
    }

    fun stopAll() {
        runCatching { stopPlayback() }
        runCatching { native.stop() }
        runCatching { audioFocus.abandon() }
    }

    fun release() {
        stopAll()
        native.shutdown()
    }

    private fun stopPlayback() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    /** Test-audio line for the pre-announcement levels check (plan §11). */
    fun playTestLine(onDone: () -> Unit, onError: () -> Unit) {
        speakNative(AUDIO_TEST_LINE, onDone, onError)
    }
}
