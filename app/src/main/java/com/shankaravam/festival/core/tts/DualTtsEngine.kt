package com.shankaravam.festival.core.tts

import android.content.Context
import android.media.MediaPlayer
import com.shankaravam.festival.core.audio.AudioFocusManager
import com.shankaravam.festival.data.remote.AudioCloudClient
import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.model.Donation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    private val chimeEnabled: () -> Boolean = { true },
    /**
     * Active Sarvam voice (Phase 1 ghost-voice fix). Read live at each call so
     * a speaker switch takes effect without rebuilding the engine. Wired to
     * `SessionPrefs.sarvamSpeaker` in [com.shankaravam.festival.di.AppContainer].
     */
    private val speakerProvider: () -> String = { "shubh" },
    /**
     * Phase 2 explicit engine mode (RC5). Read live at each call; wired to
     * `SessionPrefs.voiceEngineMode`. OFFLINE_NATIVE skips Sarvam files so a
     * user conserving quota never hears cloud audio; human imports still play
     * (they are on-device recordings, not cloud).
     */
    private val engineModeProvider: () -> com.shankaravam.festival.domain.model.VoiceEngineMode =
        { com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD },
    /**
     * Phase-3 gateway client (wired to `AppContainer.audioCloud`). Null =
     * sharing unconfigured → cloud branch skipped silently. Read live.
     */
    private val cloudClientProvider: () -> AudioCloudClient? = { null },
    /**
     * Phase-3 Firebase ID token for gateway calls. Null/blank = signed out →
     * cloud branch skipped (native fallback). Never throws out of here.
     */
    private val idTokenProvider: suspend () -> String? = { null },
    /**
     * Optional direct Sarvam API key for testing and fallback synthesis.
     */
    private val keyProvider: () -> String? = { null }
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    /** Managed scope for test-line synthesis; cancelled in [release]. */
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: MediaPlayer? = null
    /** True while the 800 ms chime owns the player — pause() won't grab it. */
    @Volatile private var inChime = false

    val nativeReady: StateFlow<Boolean> = native.ready

    fun cachedFile(
        donationId: String,
        roster: Boolean = false,
        speaker: String? = null
    ): File? = sarvam.cachedFile(
        donationId, roster, normalizeSarvamSpeaker(speaker ?: runCatching { speakerProvider() }.getOrDefault("shubh"))
    )

    /** Human-import slot (WhatsApp/file picker) for one row. Never auto-generated. */
    fun importedFile(donationId: String, roster: Boolean = false): File? =
        sarvam.importedFile(donationId, roster)

    /** Phase-3 CAS lookup (`audio_{hash}.mp3`). Null for malformed hashes. Never throws. */
    fun cachedCas(hash: String): File? =
        runCatching { sarvam.cachedCasFile(hash) }.getOrNull()

    /**
     * Phase-3 CAS hash for this rendering (effective-amount text + voice
     * identity). Pure — prefetch uses it to check disk before queueing work.
     */
    fun casHashFor(
        donation: Donation,
        eventName: String,
        language: AnnouncementLanguage,
        speaker: String,
        roster: Boolean,
        effectiveAmount: Double? = null
    ): String {
        val norm = normalizeSarvamSpeaker(speaker)
        val text = if (roster) buildRosterItemAnnouncement(donation, language, effectiveAmount)
        else buildDonationAnnouncement(donation, eventName, language, effectiveAmount)
        return audioHashFor(text, language.name, norm, roster)
    }

    /**
     * P4 ceiling enforcement; runs on the caller's (IO) thread. Never throws.
     * [excludeHashes] are live-queue CAS hashes (bare hex) that survive count
     * pressure (H1 — a pruned live clip would just regenerate, so skip it).
     */
    fun pruneCache(
        excludeIds: Set<String>,
        maxFiles: Int,
        maxAgeDays: Int,
        excludeHashes: Set<String> = emptySet()
    ): Int =
        runCatching { sarvam.pruneCache(excludeIds, maxFiles, maxAgeDays, excludeHashes) }.getOrDefault(0)

    /**
     * T0.1 grace-edit invalidation (best-effort, never throws): deletes
     * regenerable Sarvam clips, quarantines human imports to `.bak-<timestamp>`,
     * so the next play regenerates from the corrected figure. Runs on the
     * caller's (IO) thread.
     */
    fun invalidateDonationAudio(
        donationId: String,
        now: Long = System.currentTimeMillis()
    ): SarvamTtsClient.Companion.AudioInvalidation =
        runCatching { sarvam.invalidateDonationAudio(donationId, now) }
            .getOrDefault(SarvamTtsClient.Companion.AudioInvalidation(0, 0))

    /** True if a quarantined human backup exists for [donationId]. Never throws. */
    fun hasAudioBackup(donationId: String): Boolean =
        runCatching { sarvam.hasAudioBackup(donationId) }.getOrDefault(false)

    /**
     * One-time Phase 1 migration: attributes pre-speaker legacy files to
     * [speaker]. Runs on the caller's (IO) thread; never throws.
     */
    fun migrateLegacy(speaker: String): Int =
        runCatching { sarvam.migrateLegacyCache(speaker) }.getOrDefault(0)

    /**
     * Best-effort background caching. Reports PREPARING/READY/FAILED through
     * [onStatus] (persisted by the caller); returns the file or null.
     * [roster] selects the roster-line recording vs the full sentence.
     *
     * Phase-3 order: disk CAS → cloud resolve (gateway JIT-once, shared)
     * → legacy direct Sarvam (local key, CAS slot) → null (caller speaks
     * native). [onServerQuota] fires on gateway 429 so the caller can pill.
     *
     * Quota discipline (M1/M2): the device slot ([takeSlot]) is consumed ONLY
     * immediately before direct-key synthesis — gateway hits/misses never
     * touch the device budget. [onDeviceQuota] fires on slot denial (caller
     * pills, no status change — mirrors the old pre-take break). [attemptGateway]
     * lets a pass skip gateway calls after the first 429 (temple budget spent;
     * remaining rows go direct-key or native instead of hammering 429s).
     */
    suspend fun ensureCached(
        donation: Donation,
        eventName: String,
        language: AnnouncementLanguage,
        apiKey: String,
        onStatus: suspend (AudioStatus) -> Unit,
        roster: Boolean = false,
        speaker: String = "shubh",
        onError: (Throwable) -> Unit = {},
        /** T0.2: post-correction spoken figure; null keeps the raw row. */
        effectiveAmount: Double? = null,
        onServerQuota: () -> Unit = {},
        /** M1: device-budget gate, invoked only before direct synthesis. */
        takeSlot: () -> Boolean = { true },
        /** M1: fires when [takeSlot] denies (caller pills + breaks). */
        onDeviceQuota: () -> Unit = {},
        /** M2: false skips the gateway branch (server-capped pass). */
        attemptGateway: Boolean = true
    ): File? {
        val normSpeaker = normalizeSarvamSpeaker(speaker)
        val text = if (roster) {
            buildRosterItemAnnouncement(donation, language, effectiveAmount)
        } else {
            buildDonationAnnouncement(donation, eventName, language, effectiveAmount)
        }
        val hash = audioHashFor(text, language.name, normSpeaker, roster)
        sarvam.cachedCasFile(hash)?.let { return it }
        val offlineOnly = runCatching { engineModeProvider() }
            .getOrDefault(com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD) ==
            com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
        // F2: tracks whether cloud work was attempted. The blank-key early
        // return below must close an attempted pass with FAILED — but a row
        // nothing was ever tried for stays NOT_GENERATED (no spurious FAILED
        // on keyless offline installs).
        var cloudAttempted = false
        if (!offlineOnly && attemptGateway) {
            val cloud = runCatching { cloudClientProvider() }.getOrNull()
            // Signed-out (blank token) is "not attempted", not failed — skip
            // before PREPARING so signed-out rows keep NOT_GENERATED instead
            // of flickering PREPARING→FAILED with zero network spent.
            val token = runCatching { idTokenProvider() }.getOrNull()
            if (cloud != null && !token.isNullOrBlank()) {
                cloudAttempted = true
                runCatching { onStatus(AudioStatus.PREPARING) }
                when (val r = cloud.resolveAudio(
                    token, donation.eventId, donation.id,
                    text, language.name, normSpeaker, roster, hash
                )) {
                    is AudioCloudClient.AudioResolve.Ready -> {
                        val file = sarvam.putCasFile(r.hash, r.bytes)
                        if (file != null) {
                            onStatus(AudioStatus.READY)
                            return file
                        }
                    }
                    is AudioCloudClient.AudioResolve.ServerQuota ->
                        runCatching { onServerQuota() }
                    is AudioCloudClient.AudioResolve.Unavailable -> Unit
                }
            }
        }
        if (apiKey.isBlank()) {
            if (cloudAttempted) runCatching { onStatus(AudioStatus.FAILED) }
            return null
        }
        // M1: the device slot gates direct synthesis only — gateway-served
        // rows above never spent budget. Denial pills via onDeviceQuota and
        // returns null (caller's circuit-breaker stops the pass, as before).
        if (!runCatching { takeSlot() }.getOrDefault(false)) {
            runCatching { onDeviceQuota() }
            return null
        }
        runCatching { onStatus(AudioStatus.PREPARING) }
        return try {
            val file = sarvam.getOrGenerateCasAudio(hash, text, apiKey, speaker = normSpeaker)
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

    /**
     * Highest quality for this row: the human-imported full clip first (a
     * deliberate 1-tap import is an intentional override — Fix-B2: checking
     * Sarvam first made imports appear broken whenever prefetch had already
     * generated audio), else the CAS clip for this exact rendering
     * (`audio_{hash}.mp3` — correct by construction across corrections), else
     * the legacy speaker file (one-release dual-read; offline fallback —
     * residual edge: a pre-transition file can predate a post-grace
     * correction, in which case native-effective would be righter),
     * else native TTS.
     * Never throws. Phase 2: OFFLINE_NATIVE mode skips Sarvam files entirely
     * (quota conservation); human imports still play — they are local recordings.
     */
    fun playBest(
        donation: Donation,
        eventName: String,
        language: AnnouncementLanguage,
        onDone: () -> Unit,
        onError: () -> Unit,
        speaker: String? = null,
        /** T0.2: post-correction spoken figure; null keeps the raw row. */
        effectiveAmount: Double? = null
    ) {
        withChime(onDone, onError) {
            runCatching {
                val offlineOnly = runCatching { engineModeProvider() }
                    .getOrDefault(com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD) ==
                    com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
                // Human full-clip import (e.g. WhatsApp share) is an
                // intentional override — plays under any speaker and mode.
                sarvam.importedFile(donation.id)?.let {
                    playFile(it, onDone, onError)
                    return@withChime
                }
                val active = normalizeSarvamSpeaker(speaker ?: runCatching { speakerProvider() }.getOrDefault("shubh"))
                val text = buildDonationAnnouncement(donation, eventName, language, effectiveAmount)
                if (!offlineOnly) {
                    sarvam.cachedCasFile(audioHashFor(text, language.name, active, false))?.let {
                        playFile(it, onDone, onError)
                        return@withChime
                    }
                    // F1: a corrected row never plays a donation-keyed legacy
                    // clip (it speaks the old figure) — CAS or native instead.
                    if (com.shankaravam.festival.domain.model.legacyCacheCovers(donation.amount, effectiveAmount)) {
                        sarvam.cachedFile(donation.id, speaker = active)?.let {
                            playFile(it, onDone, onError)
                            return@withChime
                        }
                    }
                }
                speakNative(text, onDone, onError)
            }.onFailure {
                runCatching { speakNative(AUDIO_TEST_LINE, onDone, onError) }
                    .onFailure { runCatching { onError() } }
            }
        }
    }

    /**
     * Plays a crisp roster line: human-imported roster recording first
     * (intentional override), then the CAS clip for this exact rendering,
     * then the human full clip for that row, then the legacy Sarvam files
     * (one-release dual-read — see [playBest] for the staleness caveat),
     * else native TTS.
     * Phase 2: OFFLINE_NATIVE mode skips Sarvam lookups. Never throws.
     */
    fun playRosterItem(
        donation: Donation,
        language: AnnouncementLanguage,
        onDone: () -> Unit,
        onError: () -> Unit,
        speaker: String? = null,
        /** T0.2: post-correction spoken figure; null keeps the raw row. */
        effectiveAmount: Double? = null
    ) {
        withChime(onDone, onError, chime = false) {
            runCatching {
                val offlineOnly = runCatching { engineModeProvider() }
                    .getOrDefault(com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD) ==
                    com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
                // Intentional human roster clip wins over any cloud voice.
                sarvam.importedFile(donation.id, roster = true)?.let {
                    playFile(it, onDone, onError)
                    return@withChime
                }
                val active = normalizeSarvamSpeaker(speaker ?: runCatching { speakerProvider() }.getOrDefault("shubh"))
                val text = buildRosterItemAnnouncement(donation, language, effectiveAmount)
                val legacyTrusted =
                    com.shankaravam.festival.domain.model.legacyCacheCovers(donation.amount, effectiveAmount)
                if (!offlineOnly) {
                    sarvam.cachedCasFile(audioHashFor(text, language.name, active, true))?.let {
                        playFile(it, onDone, onError)
                        return@withChime
                    }
                    // F1: see playBest — corrected rows skip legacy clips.
                    if (legacyTrusted) {
                        sarvam.cachedFile(donation.id, roster = true, speaker = active)?.let {
                            playFile(it, onDone, onError)
                            return@withChime
                        }
                    }
                }
                // Imported full clip (e.g. WhatsApp share) doubles as the roster line.
                sarvam.importedFile(donation.id)?.let {
                    playFile(it, onDone, onError)
                    return@withChime
                }
                if (!offlineOnly && legacyTrusted) {
                    sarvam.cachedFile(donation.id, speaker = active)?.let {
                        playFile(it, onDone, onError)
                        return@withChime
                    }
                }
                speakNative(text, onDone, onError)
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

    /** F6: Checks if a pre-generated phrase (intro/outro) is cached for [speaker]. */
    fun cachedPhrase(key: String, speaker: String? = null): File? =
        sarvam.cachedPhraseFile(
            key,
            normalizeSarvamSpeaker(speaker ?: runCatching { speakerProvider() }.getOrDefault("shubh"))
        )

    /** F6: Prefetch helper to synthesize and cache an intro or outro phrase. */
    suspend fun ensurePhraseCached(
        key: String,
        text: String,
        apiKey: String,
        speaker: String = "shubh"
    ): File? {
        if (apiKey.isBlank()) return null
        return runCatching {
            sarvam.getOrGeneratePhraseAudio(key, text, apiKey, speaker)
        }.getOrNull()
    }

    /**
     * F6: Speaks opening intro or closing outro phrase in the active Sarvam voice
     * if cached, otherwise falls back to Android native TTS. Single voice queue by construction.
     */
    fun playPhraseBest(
        text: String,
        cacheKey: String,
        onDone: () -> Unit,
        onError: () -> Unit,
        speaker: String? = null
    ) {
        withChime(onDone, onError) {
            val offlineOnly = engineModeProvider() == com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
            if (!offlineOnly) {
                val activeSpeaker = normalizeSarvamSpeaker(speaker ?: runCatching { speakerProvider() }.getOrDefault("shubh"))
                cachedPhrase(cacheKey, activeSpeaker)?.let {
                    playFile(it, onDone, onError)
                    return@withChime
                }
            }
            speakNative(text, onDone, onError)
        }
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
        runCatching { engineScope.cancel() }
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

    /**
     * Test-audio line for the pre-announcement levels check. Uses cloud voice if enabled.
     * @param eventId real festival id so the gateway seat check passes; null/blank keeps
     * the legacy dummy id (gateway 403s → native fallback, same as before). The CAS hash
     * covers only (language, speaker, roster, text), so varying this never poisons the cache.
     */
    fun playTestLine(onDone: () -> Unit, onError: () -> Unit, eventId: String? = null) {
        val offlineOnly = runCatching { engineModeProvider() }
            .getOrDefault(com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD) ==
            com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
        if (!offlineOnly) {
            val speaker = normalizeSarvamSpeaker(runCatching { speakerProvider() }.getOrDefault("shubh"))
            // L6: shared with testAudio's quota pre-check hash — same sentence.
            val text = AUDIO_TEST_SYNTH_LINE
            val hash = audioHashFor(text, "TELUGU", speaker, false)
            sarvam.cachedCasFile(hash)?.let {
                playFile(it, onDone, onError)
                return
            }
            engineScope.launch {
                // Gateway first (requires configured URL + signed-in token;
                // auth failures fall through to direct key, never native yet).
                val cloud = runCatching { cloudClientProvider() }.getOrNull()
                if (cloud?.baseUrl() != null) {
                    val token = runCatching { idTokenProvider() }.getOrNull()
                    if (!token.isNullOrBlank()) {
                        // Real event id lets the worker seat check pass; dummy
                        // id 403s (gateway-only Test Voice would never verify).
                        val resolveEventId = if (!eventId.isNullOrBlank()) eventId else "test_event"
                        val r = cloud.resolveAudio(
                            token, resolveEventId, "test_sample", text, "TELUGU", speaker, false, hash
                        )
                        if (r is AudioCloudClient.AudioResolve.Ready) {
                            val file = sarvam.putCasFile(r.hash, r.bytes)
                            if (file != null) {
                                playFile(file, onDone, onError)
                                return@launch
                            }
                        }
                    }
                }
                // Direct Sarvam key fallback (no gateway / gateway miss).
                val directKey = runCatching { keyProvider() }.getOrNull()?.trim().orEmpty()
                if (directKey.isNotBlank()) {
                    runCatching {
                        val file = sarvam.getOrGenerateCasAudio(hash, text, directKey, speaker)
                        playFile(file, onDone, onError)
                    }.onFailure {
                        speakNative(AUDIO_TEST_LINE, onDone, onError)
                    }
                    return@launch
                }
                speakNative(AUDIO_TEST_LINE, onDone, onError)
            }
            return
        }
        speakNative(AUDIO_TEST_LINE, onDone, onError)
    }
}
