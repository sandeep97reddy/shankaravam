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
        roster: Boolean = false
    ): File? {
        sarvam.cachedFile(donation.id, roster)?.let { return it }
        if (apiKey.isBlank()) return null
        onStatus(AudioStatus.PREPARING)
        return try {
            val text = if (roster) {
                buildRosterItemAnnouncement(donation, language)
            } else {
                buildDonationAnnouncement(donation, eventName, language)
            }
            val file = sarvam.getOrGenerateAudio(donation.id, text, apiKey, roster = roster)
            onStatus(AudioStatus.READY)
            file
        } catch (_: Exception) {
            onStatus(AudioStatus.FAILED)
            null
        }
    }

    /** Instant offline speech (zero network, zero latency). */
    fun speakNative(text: String, onDone: () -> Unit, onError: () -> Unit) {
        audioFocus.request()
        native.speak(
            text,
            onDone = { audioFocus.abandon(); onDone() },
            onError = { audioFocus.abandon(); onError() }
        )
    }

    /** Highest quality for this row: cached Sarvam mp3, else native TTS. */
    fun playBest(
        donation: Donation,
        eventName: String,
        language: AnnouncementLanguage,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        val cached = sarvam.cachedFile(donation.id)
        if (cached != null) {
            playFile(cached, onDone, onError)
        } else {
            speakNative(buildDonationAnnouncement(donation, eventName, language), onDone, onError)
        }
    }

    /** Plays a crisp roster line: cloud recording when cached, else native TTS. */
    fun playRosterItem(
        donation: Donation,
        language: AnnouncementLanguage,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        val cached = sarvam.cachedFile(donation.id, roster = true)
        if (cached != null) {
            playFile(cached, onDone, onError)
        } else {
            speakNative(buildRosterItemAnnouncement(donation, language), onDone, onError)
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
        stopPlayback()
        audioFocus.request()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(audioFocus.attributes)
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    audioFocus.abandon()
                    onDone()
                }
                setOnErrorListener { _, _, _ ->
                    audioFocus.abandon()
                    onError()
                    true
                }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {
            audioFocus.abandon()
            onError()
        }
    }

    fun pausePlayback(): Boolean {
        val active = player
        return if (active?.isPlaying == true) {
            active.pause()
            true
        } else false
    }

    /** True while a cached file is audibly playing (used by queue resume-poll). */
    fun isFilePlaying(): Boolean =
        runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun resumePlayback(): Boolean {
        val paused = player
        return if (paused != null && !paused.isPlaying) {
            audioFocus.request()
            paused.start()
            true
        } else false
    }

    fun stopAll() {
        stopPlayback()
        native.stop()
        audioFocus.abandon()
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
