package com.shankaravam.festival.core.tts

import android.content.Context
import android.media.MediaPlayer
import com.shankaravam.festival.core.audio.AudioFocusManager
import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.model.Donation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileInputStream

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
    private val audioFocus: AudioFocusManager,
    /** Device-local toggle (Settings & Voice); default on for immersion. */
    private val chimeEnabled: () -> Boolean = { true }
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    /** True while the 800 ms chime owns the player — pause() won't grab it. */
    @Volatile private var inChime = false

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

    /**
     * Temple chime gate (feature #4): plays the baked bell WAV first, then
     * [action]. Same player, same focus, same routing — and stopAll() kills a
     * mid-chime announcement like any other. Falls straight through to
     * [action] when the toggle is off, [chime] is false, or the bake failed.
     * Never throws. playTestLine deliberately bypasses this (dry voice), and
     * roster items pass chime=false — a bell every ~3 s would fatigue a pandal;
     * the opening intro keeps the immersion.
     *
     * Focus is held across the handoff (P2 fix): the chime stage plays with
     * abandonOnDone=false, so background music never un-ducks for a beat
     * between bell and speech. request() is idempotent, so the speech stage
     * re-request is a no-op that keeps ownership continuous.
     */
    private fun withChime(
        onDone: () -> Unit,
        onError: () -> Unit,
        chime: Boolean = true,
        action: () -> Unit
    ) {
        val chimeFile = if (chime && runCatching { chimeEnabled() }.getOrDefault(true)) {
            ensureChimeFile(File(appContext.cacheDir, "audio"))
        } else null
        if (chimeFile == null) {
            mainHandler.post {
                runCatching { action() }.onFailure { runCatching { onError() } }
            }
            return
        }
        inChime = true
        playFileInternal(
            chimeFile,
            abandonOnDone = false,
            onDone = {
                inChime = false
                mainHandler.post {
                    runCatching { action() }.onFailure { runCatching { onError() } }
                }
            },
            onError = {
                inChime = false
                // Chime failure is non-fatal: proceed to announcement speech anyway!
                mainHandler.post {
                    runCatching { action() }.onFailure { runCatching { onError() } }
                }
            }
        )
    }

    /** Highest quality for this row: cached Sarvam mp3, else native TTS. Never throws. */
    fun playBest(
        donation: Donation,
        eventName: String,
        language: AnnouncementLanguage,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        withChime(onDone, onError) {
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
        withChime(onDone, onError, chime = false) {
            runCatching {
                sarvam.cachedFile(donation.id, roster = true)?.let {
                    playFile(it, onDone, onError)
                    return@withChime
                }
                // Imported full clip (e.g. WhatsApp share) doubles as the roster line.
                sarvam.cachedFile(donation.id)?.let {
                    playFile(it, onDone, onError)
                    return@withChime
                }
                speakNative(buildRosterItemAnnouncement(donation, language), onDone, onError)
            }.onFailure {
                runCatching { speakNative(AUDIO_TEST_LINE, onDone, onError) }
                    .onFailure { runCatching { onError() } }
            }
        }
    }

    /** Speaks opening intro or closing outro phrase over the audio focus channel. */
    fun speakPhrase(
        text: String,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        withChime(onDone, onError) { speakNative(text, onDone, onError) }
    }

    fun playFile(file: File, onDone: () -> Unit, onError: () -> Unit) {
        playFileInternal(file, abandonOnDone = true, onDone = onDone, onError = onError)
    }

    /**
     * P2 fix: [abandonOnDone]=false keeps focus held when a chained stage
     * follows (chime → speech). Errors ALWAYS abandon — a dead stage must
     * never strand focus. The public [playFile] keeps classic behavior.
     */
    private fun playFileInternal(
        file: File,
        abandonOnDone: Boolean,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        stopPlayback()
        runCatching { audioFocus.request() }
        try {
            if (!file.exists() || file.length() == 0L) {
                if (abandonOnDone) runCatching { audioFocus.abandon() }
                mainHandler.post { runCatching { onError() } }
                return
            }
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(audioFocus.attributes)
            // Use FileDescriptor to guarantee readability for mediaserver across all Android sandbox boundaries
            FileInputStream(file).use { fis ->
                mp.setDataSource(fis.fd, 0, file.length())
            }
            mp.setOnCompletionListener { activeMp ->
                // Post to Main Looper so native C++ notify() completely unwinds before release/next stage
                mainHandler.post {
                    if (player === activeMp) {
                        stopPlayback()
                    }
                    if (abandonOnDone) runCatching { audioFocus.abandon() }
                    runCatching { onDone() }
                }
            }
            mp.setOnErrorListener { activeMp, _, _ ->
                mainHandler.post {
                    if (player === activeMp) {
                        stopPlayback()
                    }
                    if (abandonOnDone) runCatching { audioFocus.abandon() }
                    runCatching { onError() }
                }
                true
            }
            mp.setOnPreparedListener { activeMp ->
                mainHandler.post {
                    runCatching {
                        if (player === activeMp) {
                            activeMp.start()
                        }
                    }.onFailure {
                        stopPlayback()
                        if (abandonOnDone) runCatching { audioFocus.abandon() }
                        runCatching { onError() }
                    }
                }
            }
            mp.prepareAsync()
        } catch (_: Throwable) {
            stopPlayback()
            if (abandonOnDone) runCatching { audioFocus.abandon() }
            mainHandler.post { runCatching { onError() } }
        }
    }

    fun pausePlayback(): Boolean =
        runCatching {
            // The 800 ms chime is never grabbed mid-ring: pausing it would
            // strand the queued speech (resume-poll sees a dead player and
            // skips ahead). The VM restarts the item instead.
            if (inChime) return false
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
        inChime = false
        stopPlayback()
        runCatching { native.stop() }
        runCatching { audioFocus.abandon() }
    }

    fun release() {
        stopAll()
        native.shutdown()
    }

    private fun stopPlayback() {
        val active = player ?: return
        player = null
        runCatching {
            if (runCatching { active.isPlaying }.getOrDefault(false)) {
                active.stop()
            }
            active.reset()
            active.release()
        }
    }

    /** Test-audio line for the pre-announcement levels check (plan §11). */
    fun playTestLine(onDone: () -> Unit, onError: () -> Unit) {
        speakNative(AUDIO_TEST_LINE, onDone, onError)
    }
}
