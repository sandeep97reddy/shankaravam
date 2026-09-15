package com.shankaravam.festival.presentation.announcement

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.audio.AudioRoute
import com.shankaravam.festival.core.tts.AnnouncementLanguage
import com.shankaravam.festival.core.tts.ClipDonor
import com.shankaravam.festival.core.tts.FestivalPreset
import com.shankaravam.festival.core.tts.buildClosingAnnouncement
import com.shankaravam.festival.core.tts.buildOpeningAnnouncement
import com.shankaravam.festival.core.tts.matchRosterClips
import com.shankaravam.festival.core.tts.presetForEventName
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.model.effectiveDonationAmount
import com.shankaravam.festival.domain.model.groupCorrectionsByTarget
import com.shankaravam.festival.presentation.donation.DonationListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Prefetch scope cap (worker-invocation guard): background prefetch warms
 * only the newest N rows. Older rows resolve on demand at play time (JIT),
 * so opening the announcement screen on a 200-row history costs ~N×2 hits
 * instead of ~200×2 per phone per pass. JIT playback is unaffected.
 */
private const val PREFETCH_MAX_ROWS = 15

/**
 * Announcement queue (plan §11): playlist honors the current sort/filter,
 * defaults to received+confirmed with pledges excluded. Playback runs on a
 * single cancellable job — stop/skip always wins over completion callbacks.
 * Supports Pandal Roster Mode (Intro once → Name + Amount → Outro).
 */
@Immutable
data class QueueUiState(
    val eventName: String = "",
    val eventLocation: String = "",
    val queue: List<Donation> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val repeat: Boolean = false,
    val rosterMode: Boolean = true,
    val festivalPreset: String = "VINAYAKA_CHAVITHI",
    val festivalPresetAuto: Boolean = false,
    val gapSeconds: Int = 2,
    val sort: String = SessionPrefs.SORT_NEWEST,
    val language: String = SessionPrefs.LANG_TELUGU,
    val showPledged: Boolean = false,
    val tagFilter: String? = null,
    val availableTags: List<String> = emptyList(),
    val route: AudioRoute = AudioRoute.SPEAKER,
    val nativeReady: Boolean = false,
    val testingAudio: Boolean = false,
    val prefetchRemaining: Int = 0,
    /**
     * Phase 3 transparency (RC4): non-blank while the Sarvam quota is
     * exhausted — the transport card shows this instead of silently flipping
     * voices mid-queue. Blank = cloud budget available (or irrelevant).
     */
    val quotaPill: String = "",
    val hasEvent: Boolean = false
) {
    val current: Donation? get() = queue.getOrNull(index)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementQueueViewModel(private val container: AppContainer) : ViewModel() {

    private val prefs: SessionPrefs = container.sessionPrefs
    private val engine = container.ttsEngine
    private val detector = container.routeDetector
    private val donationRepo = container.donationRepository
    private val eventRepo = container.eventRepository
    private val correctionRepo = container.correctionRepository
    private val secureKeys = container.secureKeys

    private val sort = MutableStateFlow(prefs.queueSort)
    private val language = MutableStateFlow(prefs.queueLanguage)
    private val showPledged = MutableStateFlow(false)
    private val tagFilter = MutableStateFlow<String?>(null)
    private val index = MutableStateFlow(-1)
    private val isPlaying = MutableStateFlow(false)
    private val isPaused = MutableStateFlow(false)
    private val repeat = MutableStateFlow(false)
    private val rosterMode = MutableStateFlow(prefs.queueRosterMode)
    private val festivalPreset = MutableStateFlow(prefs.queueFestivalPreset)
    /** Manual pick wins; cleared whenever the current event changes. */
    private val presetOverride = MutableStateFlow<String?>(null)
    private val gapSeconds = MutableStateFlow(prefs.queueGapSeconds)
    private val testingAudio = MutableStateFlow(false)
    private val prefetchRemaining = MutableStateFlow(0)

    /**
     * Phase 3 quota transparency (RC4). Set when prefetch is denied a Sarvam
     * slot; cleared when a slot succeeds or the window rolls (see
     * [refreshQuotaPill]). The transport card renders it as a warning pill.
     */
    private val quotaPill = MutableStateFlow("")

    /**
     * Gateway diagnostics: last human-readable gateway fallback reason
     * (401/403/timeout/wrong-URL/offline-mode/...). Shown in the voice card
     * so "Sarvam not playing" is never a mystery. Blank = no error seen yet.
     */
    private val _gatewayError = MutableStateFlow("")
    val gatewayError: StateFlow<String> = _gatewayError.asStateFlow()
    fun clearGatewayError() { _gatewayError.value = "" }

    private val _importReport = MutableStateFlow<String?>(null)
    val importReport: StateFlow<String?> = _importReport.asStateFlow()
    fun consumeImportReport() { _importReport.value = null }

    /**
     * Last playback failure, shown inline in the transport card. A safety net:
     * NO throwable from the announce path may ever kill the process — it lands
     * here as a visible message instead of "the app closed itself".
     */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    fun clearPlaybackError() { _playbackError.value = null }

    private fun failPlayback(e: Throwable) {
        isPlaying.value = false
        isPaused.value = false
        runCatching { engine.stopAll() }
        _playbackError.value =
            "Announcement stopped (${e.message ?: "audio error"}). Try again — offline voice covers gaps."
    }

    private var playedIntro = false

    init {
        detector.start()
        prefetchWhenIdle()
        observeCorrectionsSnapshot()
        // A new event gets a fresh auto-detected intro; manual picks don't leak across events.
        viewModelScope.launch {
            prefs.currentEventId.collect { presetOverride.value = null }
        }
    }

    /** T0.2: keeps [correctionsSnapshot] fresh for speech text (never blocks UI). */
    private fun observeCorrectionsSnapshot() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            prefs.currentEventId.flatMapLatest { eventId ->
                if (eventId == null) flowOf(emptyList())
                else correctionRepo.observeForEvent(eventId)
            }.collect { correctionsSnapshot.value = groupCorrectionsByTarget(it) }
        }
    }

    /** T0.2: post-correction spoken figure for [donation] (raw row when no log). */
    private fun effectiveOf(donation: Donation): Double =
        effectiveDonationAmount(donation, correctionsSnapshot.value[donation.id].orEmpty())



    override fun onCleared() {
        stop()
        detector.stop()
    }

    private data class FilterOpts(
        val sort: String,
        val language: String,
        val pledged: Boolean,
        val tag: String?
    )

    /**
     * Fix-B1: everything that invalidates the cloud cache — event/donations
     * PLUS voice identity (speaker, engine mode, key version). Any of these
     * changing re-runs the prefetch pass.
     */
    private data class PrefetchInputs(
        val event: Event?,
        val donations: List<Donation>,
        val corrections: List<Correction> = emptyList(),
        val speaker: String = "",
        val mode: com.shankaravam.festival.domain.model.VoiceEngineMode =
            com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD
    )

    /**
     * T0.2 effective-amount snapshot for the imperative playback path
     * ([playSequence] reads `uiState.value`, which carries raw ledger rows).
     * Kept fresh by [observeCorrectionsSnapshot]; prefetch reads its own
     * combined corrections instead so a new correction re-runs the pass.
     */
    private val correctionsSnapshot =
        MutableStateFlow<Map<String, List<Correction>>>(emptyMap())

    private data class PlayOpts(
        val index: Int,
        val playing: Boolean,
        val paused: Boolean,
        val repeat: Boolean,
        val rosterMode: Boolean,
        val preset: String,
        val gap: Int,
        val testing: Boolean,
        val prefetch: Int,
        val quotaPill: String = ""
    )

    val uiState: StateFlow<QueueUiState> =
        prefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(QueueUiState())
            } else {
                val filters = combine(
                    combine(sort, language) { s: String, l: String -> s to l },
                    combine(showPledged, tagFilter) { p: Boolean, t: String? -> p to t }
                ) { sl: Pair<String, String>, pt: Pair<Boolean, String?> ->
                    FilterOpts(sl.first, sl.second, pt.first, pt.second)
                }

                val playBasics = combine(
                    combine(index, isPlaying, isPaused) { i: Int, playing: Boolean, paused: Boolean ->
                        Triple(i, playing, paused)
                    },
                    combine(repeat, rosterMode) { rep: Boolean, rm: Boolean -> rep to rm },
                    festivalPreset
                ) { a, b, preset ->
                    Triple(a, b, preset)
                }

                val play = combine(
                    combine(
                        playBasics,
                        combine(gapSeconds, testingAudio) { gap: Int, testing: Boolean -> gap to testing },
                        prefetchRemaining
                    ) { pb, gt, prefetch ->
                        val a = pb.first
                        val b = pb.second
                        PlayOpts(
                            index = a.first,
                            playing = a.second,
                            paused = a.third,
                            repeat = b.first,
                            rosterMode = b.second,
                            preset = pb.third,
                            gap = gt.first,
                            testing = gt.second,
                            prefetch = prefetch
                        )
                    },
                    quotaPill
                ) { opts, pill -> opts.copy(quotaPill = pill) }

                val core = combine(
                    eventRepo.observeEvent(eventId),
                    donationRepo.observeForEvent(eventId),
                    filters
                ) { event: Event?, donations: List<Donation>, opts: FilterOpts ->
                    Triple(event, donations, opts)
                }

                val withPlay = combine(core, play) { c, p -> c to p }
                val withRoute = combine(withPlay, detector.route) { cp, route: AudioRoute ->
                    Triple(cp.first, cp.second, route)
                }

                combine(withRoute, engine.nativeReady) { cr, ready: Boolean ->
                    val triple = cr.first
                    buildQueueState(
                        triple.first, triple.second, triple.third,
                        cr.second, cr.third, ready
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueUiState())

    private fun buildQueueState(
        event: Event?,
        donations: List<Donation>,
        opts: FilterOpts,
        play: PlayOpts,
        route: AudioRoute,
        ready: Boolean
    ): QueueUiState {
        // Auto-link: "హనుమాన్ జయంతి" opens with the Hanuman intro, not the
        // Vinayaka default — unless the organizer picked one explicitly.
        val autoPreset = presetForEventName(event?.name ?: "")?.name
        val effectivePreset = presetOverride.value ?: autoPreset ?: play.preset
        val queue = donations
            .asSequence()
            .filter { d ->
                d.announcementEnabled &&
                    (opts.tag == null || opts.tag in d.tags) &&
                    if (opts.pledged) {
                        d.status == DonationStatus.PLEDGED ||
                            d.status == DonationStatus.PARTIALLY_RECEIVED
                    } else {
                        d.status == DonationStatus.RECEIVED ||
                            d.status == DonationStatus.CONFIRMED
                    }
            }
            .toList()
            .sortedWith(DonationListViewModel.comparatorFor(opts.sort))

        return QueueUiState(
            eventName = event?.name ?: "",
            eventLocation = event?.location ?: "",
            queue = queue,
            index = play.index.coerceIn(-1, queue.size - 1),
            isPlaying = play.playing,
            isPaused = play.paused,
            repeat = play.repeat,
            rosterMode = play.rosterMode,
            festivalPreset = effectivePreset,
            festivalPresetAuto = presetOverride.value == null && autoPreset != null,
            gapSeconds = play.gap,
            sort = opts.sort,
            language = opts.language,
            showPledged = opts.pledged,
            tagFilter = opts.tag,
            availableTags = donations.flatMap { it.tags }.distinct().sorted(),
            route = route,
            nativeReady = ready,
            testingAudio = play.testing,
            prefetchRemaining = play.prefetch,
            quotaPill = play.quotaPill,
            hasEvent = true
        )
    }

    // ---- transport ----

    fun play() {
        runCatching {
            refreshQuotaPill()
            val state = uiState.value
            if (state.queue.isEmpty() || state.isPlaying && !state.isPaused) return
            clearPlaybackError()
            if (state.isPaused) {
                resume()
                return
            }
            playAt(if (state.index in state.queue.indices) state.index else 0)
        }.onFailure { failPlayback(it) }
    }

    /**
     * Phase 3: clears a stale quota pill once the 30-min window has rolled.
     * Main-safe (plain prefs reads, no IO). Called on user action; the
     * prefetch pass also refreshes it on every queue change.
     * M5: the gateway (temple-budget) pill is DAILY-guarded — it clears only
     * when the UTC day turns over ([SessionPrefs.gatewayQuotaActive]), never
     * on a device-window roll or a single successful device slot.
     */
    private fun refreshQuotaPill() {
        if (quotaPill.value.isBlank()) return
        if (isGatewayPill()) {
            if (!prefs.gatewayQuotaActive()) quotaPill.value = ""
            return
        }
        if (prefs.sarvamQuotaUsed() < SessionPrefs.SARVAM_MAX_CALLS) {
            quotaPill.value = ""
        }
    }

    /** M5: true while the temple-budget (gateway 429) pill is showing. */
    private fun isGatewayPill(): Boolean =
        quotaPill.value.isNotBlank() &&
            quotaPill.value == com.shankaravam.festival.domain.model.gatewayQuotaPillText()

    fun pause() {
        runCatching {
            playJob?.cancel()
            playJob = null
            engine.pausePlayback()
            engine.native.stop()
            isPaused.value = true
            isPlaying.value = false
        }
    }

    fun stop() {
        runCatching {
            playJob?.cancel()
            playJob = null
            engine.stopAll()
            isPlaying.value = false
            isPaused.value = false
            playedIntro = false
            clearPlaybackError()
        }
    }

    fun next() {
        runCatching {
            val state = uiState.value
            if (state.queue.isEmpty()) return
            val nextIndex = state.index + 1
            if (nextIndex > state.queue.lastIndex) {
                if (state.repeat) playAt(0) else stop()
            } else {
                playAt(nextIndex)
            }
        }.onFailure { failPlayback(it) }
    }

    fun previous() {
        runCatching {
            val state = uiState.value
            if (state.queue.isEmpty()) return
            playAt((state.index - 1).coerceAtLeast(0))
        }.onFailure { failPlayback(it) }
    }

    fun replay() {
        runCatching {
            val state = uiState.value
            if (state.index in state.queue.indices) playAt(state.index) else play()
        }.onFailure { failPlayback(it) }
    }

    fun jumpTo(position: Int) {
        runCatching {
            if (position in uiState.value.queue.indices) playAt(position)
        }.onFailure { failPlayback(it) }
    }

    fun toggleRepeat() {
        repeat.value = !repeat.value
    }

    fun toggleRosterMode() {
        val next = !rosterMode.value
        rosterMode.value = next
        prefs.queueRosterMode = next
    }

    fun setFestivalPreset(preset: String) {
        presetOverride.value = preset
        festivalPreset.value = preset
        prefs.queueFestivalPreset = preset
    }

    fun testAudio() {
        if (uiState.value.isPlaying || uiState.value.testingAudio) return
        clearPlaybackError()
        // Fresh attempt, fresh diagnosis — a stale orange box must not linger
        // while the new Test is still in flight (failure re-sets it below).
        clearGatewayError()
        refreshQuotaPill()
        // Quota pre-check: direct-key synthesis burns a client slot; gateway
        // has its own server-side cap. Only gate the uncached direct-key path
        // so gateway users are never blocked by the device budget.
        if (prefs.voiceEngineMode == com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD) {
            val speaker = com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(prefs.sarvamSpeaker)
            // L6: same sentence playTestLine synthesizes — shared constant.
            val hash = com.shankaravam.festival.core.tts.audioHashFor(
                com.shankaravam.festival.core.tts.AUDIO_TEST_SYNTH_LINE, "TELUGU", speaker, false
            )
            val cached = engine.cachedCas(hash) != null
            val hasGateway = prefs.gatewayBaseUrl.isNotBlank()
            val hasDirectKey = secureKeys.getSarvamKey().isNotBlank()
            if (!cached && !hasGateway && hasDirectKey && !prefs.takeSarvamSlot()) {
                quotaPill.value = com.shankaravam.festival.domain.model.quotaPillText(
                    used = prefs.sarvamQuotaUsed(),
                    max = SessionPrefs.SARVAM_MAX_CALLS,
                    resetAt = prefs.sarvamQuotaResetAt()
                )
                // Fall back to native immediately — no network spent.
                testingAudio.value = true
                runCatching {
                    engine.speakNative(
                        com.shankaravam.festival.core.tts.AUDIO_TEST_LINE,
                        onDone = { testingAudio.value = false },
                        onError = { testingAudio.value = false }
                    )
                }.onFailure {
                    testingAudio.value = false
                    failPlayback(it)
                }
                return
            }
        }
        testingAudio.value = true
        runCatching {
            engine.playTestLine(
                onDone = { testingAudio.value = false },
                onError = { testingAudio.value = false },
                // Real event id so the gateway seat check passes; null keeps
                // the legacy dummy id (gateway 403s → native fallback).
                eventId = prefs.currentEventId.value,
                onGatewayError = { reason -> _gatewayError.value = "Gateway: $reason" }
            )
        }.onFailure {
            testingAudio.value = false
            failPlayback(it)
        }
    }

    // ---- queue options (persisted) ----

    fun setGap(seconds: Int) {
        gapSeconds.value = seconds.coerceIn(0, 30)
        prefs.queueGapSeconds = gapSeconds.value
    }

    fun setSort(sortKey: String) {
        sort.value = sortKey
        prefs.queueSort = sortKey
    }

    fun setLanguage(lang: String) {
        language.value = lang
        prefs.queueLanguage = lang
    }

    fun setShowPledged(show: Boolean) {
        stop()
        index.value = -1
        showPledged.value = show
    }

    fun setTag(tag: String?) {
        tagFilter.value = tag
    }

    // ---- internals ----

    private var playJob: Job? = null

    private fun languageOf(): AnnouncementLanguage = when (language.value) {
        SessionPrefs.LANG_ENGLISH -> AnnouncementLanguage.ENGLISH
        SessionPrefs.LANG_BILINGUAL -> AnnouncementLanguage.BILINGUAL
        else -> AnnouncementLanguage.TELUGU
    }

    private fun playAt(position: Int) {
        playJob?.cancel()
        val state = uiState.value
        val item = state.queue.getOrNull(position) ?: return
        index.value = position
        isPlaying.value = true
        isPaused.value = false

        // Safety net: any unexpected throwable becomes an inline error,
        // never an uncaught coroutine exception (process death).
        playJob = viewModelScope.launch {
            runCatching { playSequence(state, item, position) }
                .onFailure { failPlayback(it) }
        }
    }

    private suspend fun playSequence(
        state: QueueUiState,
        item: Donation,
        position: Int
    ) {
            // Opening Announcement: spoken once at start of Roster Mode
            if (state.rosterMode && position == 0 && !playedIntro) {
                playedIntro = true
                val presetObj = runCatching { FestivalPreset.valueOf(state.festivalPreset) }
                    .getOrDefault(FestivalPreset.VINAYAKA_CHAVITHI)
                val introText = buildOpeningAnnouncement(
                    location = state.eventLocation,
                    eventName = state.eventName,
                    preset = presetObj,
                    language = languageOf()
                )
                // M3: key covers preset + event + language + location.
                val introKey = com.shankaravam.festival.core.tts.introPhraseKey(
                    presetObj, prefs.currentEventId.value, languageOf(), state.eventLocation
                )
                val activeSpeaker = prefs.sarvamSpeaker
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    engine.playPhraseBest(
                        text = introText,
                        cacheKey = introKey,
                        onDone = { if (cont.isActive) cont.resume(Unit) {} },
                        onError = { if (cont.isActive) cont.resume(Unit) {} },
                        speaker = activeSpeaker
                    )
                    cont.invokeOnCancellation { engine.stopAll() }
                }
                delay(1000L)
            }

            // Speak the donation row in the ACTIVE speaker (Phase 1: the engine
            // resolves via its provider, but passing explicitly documents the
            // contract and survives any wiring lapse).
            // T0.2: speech uses the effective (post-correction) figure.
            // JIT quota metering: the on-demand fetch inside playBest/
            // playRosterItem spends the same device slot and raises the same
            // pills as the prefetch pass (otherwise JIT taps burn quota
            // silently).
            val activeSpeaker = prefs.sarvamSpeaker
            val effective = effectiveOf(item)
            val jitTakeSlot: () -> Boolean = { prefs.takeSarvamSlot() }
            val jitOnDeviceQuota: () -> Unit = {
                quotaPill.value = com.shankaravam.festival.domain.model.quotaPillText(
                    used = prefs.sarvamQuotaUsed(),
                    max = SessionPrefs.SARVAM_MAX_CALLS,
                    resetAt = prefs.sarvamQuotaResetAt()
                )
            }
            val jitOnServerQuota: () -> Unit = {
                prefs.setGatewayQuotaAt()
                quotaPill.value = com.shankaravam.festival.domain.model.gatewayQuotaPillText()
            }
            val jitOnGatewayError: (String) -> Unit = { reason ->
                _gatewayError.value = "Gateway: $reason"
            }
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                if (state.rosterMode) {
                    engine.playRosterItem(
                        item,
                        languageOf(),
                        onDone = { if (cont.isActive) cont.resume(Unit) {} },
                        onError = { if (cont.isActive) cont.resume(Unit) {} },
                        speaker = activeSpeaker,
                        effectiveAmount = effective,
                        takeSlot = jitTakeSlot,
                        onDeviceQuota = jitOnDeviceQuota,
                        onServerQuota = jitOnServerQuota,
                        onGatewayError = jitOnGatewayError
                    )
                } else {
                    engine.playBest(
                        item,
                        state.eventName,
                        languageOf(),
                        onDone = { if (cont.isActive) cont.resume(Unit) {} },
                        onError = { if (cont.isActive) cont.resume(Unit) {} },
                        speaker = activeSpeaker,
                        effectiveAmount = effective,
                        takeSlot = jitTakeSlot,
                        onDeviceQuota = jitOnDeviceQuota,
                        onServerQuota = jitOnServerQuota,
                        onGatewayError = jitOnGatewayError
                    )
                }
                cont.invokeOnCancellation { engine.stopAll() }
            }

        delay(gapSeconds.value * 1000L)
        advance()
    }

    private fun resume() {
        if (runCatching { engine.resumePlayback() }.getOrDefault(false)) {
            isPlaying.value = true
            isPaused.value = false
            playJob = viewModelScope.launch {
                runCatching {
                    while (engine.isFilePlaying()) delay(500)
                    delay(gapSeconds.value * 1000L)
                    advance()
                }.onFailure { failPlayback(it) }
            }
        } else {
            val at = uiState.value.index
            if (at in uiState.value.queue.indices) playAt(at) else play()
        }
    }

    private fun advance() {
        val state = uiState.value
        if (state.index >= state.queue.lastIndex) {
            if (state.repeat && state.queue.isNotEmpty()) {
                playAt(0)
            } else {
                if (state.rosterMode) {
                    playJob = viewModelScope.launch {
                        runCatching {
                            val outroText = buildClosingAnnouncement(languageOf())
                            val outroKey = "outro_${languageOf().name.lowercase()}"
                            val activeSpeaker = prefs.sarvamSpeaker
                            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                                engine.playPhraseBest(
                                    text = outroText,
                                    cacheKey = outroKey,
                                    onDone = { if (cont.isActive) cont.resume(Unit) {} },
                                    onError = { if (cont.isActive) cont.resume(Unit) {} },
                                    speaker = activeSpeaker
                                )
                                cont.invokeOnCancellation { engine.stopAll() }
                            }
                        }.onFailure { failPlayback(it); return@launch }
                        stop()
                    }
                } else {
                    stop()
                }
            }
        } else {
            playAt(state.index + 1)
        }
    }

    /**
     * Batch roster-clip import (P4): the announcer multi-picks shared clips
     * (WhatsApp, human-recorded or Sarvam — bytes are bytes). Files match rows
     * by donor name; leftovers are reported, never force-attached. The queue
     * then plays them one by one with zero extra wiring.
     */
    fun importRosterClips(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val rows = uiState.value.queue
            if (rows.isEmpty()) {
                _importReport.value = "Queue is empty — nothing to match."
                return@launch
            }
            val names = uris.map { uri -> displayNameOf(uri) ?: "clip" }
            val result = matchRosterClips(
                fileNames = names,
                donors = rows.map {
                    ClipDonor(it.id, it.donorName, it.pronunciationText)
                }
            )
            var ok = 0
            result.matched.forEach { (fileIndex, donationId) ->
                if (copyUriToRosterSlot(uris[fileIndex], donationId)) {
                    runCatching {
                        donationRepo.updateAudioStatus(
                            donationId,
                            com.shankaravam.festival.domain.model.AudioStatus.READY
                        )
                    }
                    ok++
                }
            }
            val missed = result.unmatched.mapNotNull { names.getOrNull(it) }.take(5)
            _importReport.value = buildString {
                append("Matched $ok of ${uris.size} clips.")
                if (missed.isNotEmpty()) append(" Unmatched: ${missed.joinToString(", ")}.")
                if (result.unmatched.size > missed.size) append(" (+${result.unmatched.size - missed.size} more).")
            }
        }
    }

    private fun displayNameOf(uri: android.net.Uri): String? = runCatching {
        container.appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    private fun copyUriToRosterSlot(uri: android.net.Uri, donationId: String): Boolean = runCatching {
        val dir = java.io.File(container.appContext.cacheDir, "audio")
            .apply { if (!exists()) mkdirs() }
        val dest = java.io.File(dir, "donation_${donationId}_roster.mp3")
        container.appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        if (dest.length() == 0L || dest.length() > SessionPrefs.AUDIO_IMPORT_MAX_BYTES) {
            runCatching { dest.delete() }
            return false
        }
        true
    }.getOrDefault(false)

    /** Background Sarvam prefetch for rows missing cache (silent; never blocks UI). */
    private fun prefetchWhenIdle() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            prefs.currentEventId.flatMapLatest { eventId ->
                if (eventId == null) flowOf(null)
                else {
                    // Fix-B1: voice identity is part of the trigger. A speaker
                    // switch, engine-mode switch, or fresh API key re-runs the
                    // pass — otherwise Shubh's rows would never generate after
                    // switching from Priya (or after entering a key) without
                    // an unrelated donation edit or restart.
                    // M4: roster mode + queue language are triggers too — the
                    // pass reads both live, so toggling either must re-run it
                    // (otherwise the new mode's rows stay native-silently).
                    val stableDonations = donationRepo.observeForEvent(eventId).distinctUntilChanged { old, new ->
                        old.size == new.size && old.indices.all { i ->
                            val a = old[i]
                            val b = new[i]
                            a.id == b.id &&
                                a.amount == b.amount &&
                                a.donorName == b.donorName &&
                                a.pronunciationText == b.pronunciationText &&
                                a.announcementEnabled == b.announcementEnabled &&
                                a.status == b.status
                        }
                    }
                    combine(
                        combine(eventRepo.observeEvent(eventId), stableDonations) { e, d -> e to d },
                        combine(prefs.sarvamSpeakerFlow, prefs.voiceEngineModeFlow, secureKeys.keyVersion) { s, m, _ -> s to m },
                        combine(rosterMode, language) { _, _ -> Unit }
                    ) { ed, sm, _ ->
                        PrefetchInputs(ed.first, ed.second, speaker = sm.first, mode = sm.second)
                    }.combine(correctionRepo.observeForEvent(eventId)) { inputs, corrections ->
                        inputs.copy(corrections = corrections)
                    }
                }
                    // Invocation guard: first-sync ingest emits once per row —
                    // conflate bursts into a single pass instead of N passes.
                    }.conflate().collect { inputs ->
                runCatching {
                    val event = inputs?.event
                    val donations = inputs?.donations.orEmpty()
                    if (event == null) {
                        prefetchRemaining.value = 0
                        return@runCatching
                    }
                    // C1: no direct-key gate — gateway-only installs prefetch
                    // through the gateway branch (blank apiKey = gateway-only
                    // inside ensureCached). The key only gates phrase gen.
                    val key = secureKeys.getSarvamKey()
                    val eventName = event.name
                    val roster = prefs.queueRosterMode
                    val speaker = inputs?.speaker ?: prefs.sarvamSpeaker
                    // T0.2: prefetch/hash speak effective figures; a new
                    // correction re-runs this pass via the combined flow.
                    val correctionsByTarget =
                        groupCorrectionsByTarget(inputs?.corrections.orEmpty())
                    fun prefetchEffective(d: Donation): Double =
                        effectiveDonationAmount(d, correctionsByTarget[d.id].orEmpty())
                    // Phase 1 one-time migration: attribute pre-speaker legacy
                    // files to the active voice so they stop shadowing other
                    // speakers. Idempotent, guarded, never throws. Runs for
                    // keyless installs too (their playback reads these files).
                    if (!prefs.audioCacheV2Migrated) {
                        runCatching { engine.migrateLegacy(speaker) }
                        prefs.audioCacheV2Migrated = true
                    }
                    val eligible = donations.filter { it.announcementEnabled }
                    val lang = languageOf()
                    // H2: prune runs for every install (keyless/offline too) —
                    // it is pure disk hygiene, no network, no quota.
                    runCatching {
                        engine.pruneCache(
                            excludeIds = eligible.map { it.id }.toSet(),
                            maxFiles = SessionPrefs.AUDIO_CACHE_MAX_FILES,
                            maxAgeDays = SessionPrefs.AUDIO_CACHE_MAX_AGE_DAYS,
                            // H1: live-queue CAS hashes survive count pressure.
                            excludeHashes = eligible.map { d ->
                                engine.casHashFor(
                                    d, eventName, lang, speaker, roster,
                                    prefetchEffective(d)
                                )
                            }.toSet()
                        )
                    }
                    // Phase 2: explicit offline mode burns zero cloud quota —
                    // playback serves device voice (+ human imports) only.
                    // Fix-B3: leaving cloud also dismisses a stale quota pill.
                    if ((inputs?.mode ?: prefs.voiceEngineMode) == com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE) {
                        prefetchRemaining.value = 0
                        if (quotaPill.value.isNotBlank()) quotaPill.value = ""
                        return@runCatching
                    }

                    // F6: Pre-generate intro and outro phrases in Roster Mode so full queue speaks in active Sarvam voice.
                    // Direct-key only (no gateway phrase route) — skipped keyless.
                    if (roster && key.isNotBlank()) {
                        val presetObj = runCatching { FestivalPreset.valueOf(prefs.queueFestivalPreset) }
                            .getOrDefault(FestivalPreset.VINAYAKA_CHAVITHI)
                        val introKey = com.shankaravam.festival.core.tts.introPhraseKey(
                            presetObj, event.id, lang, event.location
                        )
                        if (engine.cachedPhrase(introKey, speaker) == null) {
                            val introText = buildOpeningAnnouncement(
                                location = event.location,
                                eventName = event.name,
                                preset = presetObj,
                                language = lang
                            )
                            if (prefs.takeSarvamSlot()) {
                                engine.ensurePhraseCached(introKey, introText, key, speaker)
                            }
                        }

                        val outroKey = "outro_${lang.name.lowercase()}"
                        if (engine.cachedPhrase(outroKey, speaker) == null) {
                            val outroText = buildClosingAnnouncement(lang)
                            if (prefs.takeSarvamSlot()) {
                                engine.ensurePhraseCached(outroKey, outroText, key, speaker)
                            }
                        }
                    }
                    // Phase-3 CAS prefetch: rows missing disk CAS (and without a
                    // human override) resolve per missing hash via the engine's
                    // cloud-first branch. The P4.5 Firestore meta N+1 loop and
                    // its T0.4 stopgap are deleted — the bucket HEAD inside
                    // `resolve` is the freshness signal; stamps have no
                    // readers left. Bounded scope: newest PREFETCH_MAX_ROWS
                    // only (older rows resolve on demand at play time, so a
                    // deep history can't burn the worker quota on screen open).
                    // (lang is defined once above, next to the prune call.)
                    val missing = eligible
                        .filter { d ->
                            val eff = prefetchEffective(d)
                            engine.cachedCas(
                                engine.casHashFor(d, eventName, lang, speaker, roster, eff)
                            ) == null &&
                                // F1: a corrected row regenerates CAS even when
                                // a stale legacy clip exists (it speaks the old
                                // figure and is skipped at playback too). This
                                // also heals rows corrected on peer devices.
                                (!com.shankaravam.festival.domain.model.legacyCacheCovers(d.amount, eff) ||
                                    engine.cachedFile(d.id, roster, speaker) == null) &&
                                engine.importedFile(d.id, roster) == null &&
                                engine.importedFile(d.id) == null
                        }
                        .sortedByDescending { it.addedTime }
                        .take(PREFETCH_MAX_ROWS)
                    if (missing.isEmpty()) {
                        prefetchRemaining.value = 0
                        return@runCatching
                    }
                    // Phase 3: a rolled window clears a stale pill before this
                    // pass spends (or is denied) slots.
                    refreshQuotaPill()
                    prefetchRemaining.value = missing.size
                    // M2: after the first gateway 429 this pass stops calling
                    // the gateway (temple budget spent) — remaining rows go
                    // direct-key or native instead of hammering 429s.
                    var serverCapped = false
                    for (donation in missing) {
                        val file = engine.ensureCached(
                            donation = donation,
                            eventName = eventName,
                            language = lang,
                            apiKey = key,
                            onStatus = { status ->
                                if (status == AudioStatus.READY) {
                                    donationRepo.updateAudioStatus(donation.id, status)
                                }
                            },
                            roster = roster,
                            speaker = speaker,
                            effectiveAmount = prefetchEffective(donation),
                            // Gateway 429 gets its own pill line (temple
                            // budget spent vs this phone throttled).
                            onServerQuota = {
                                serverCapped = true
                                prefs.setGatewayQuotaAt()
                                quotaPill.value = com.shankaravam.festival.domain.model.gatewayQuotaPillText()
                            },
                            // M1: the device slot is spent only when direct
                            // synthesis actually happens (inside ensureCached);
                            // gateway hits never touch the device budget.
                            // Denial pills (with reset time) instead of
                            // silently flipping voices — prefetchRemaining
                            // KEEPS its count so waiting rows stay visible.
                            takeSlot = { prefs.takeSarvamSlot() },
                            onDeviceQuota = {
                                quotaPill.value = com.shankaravam.festival.domain.model.quotaPillText(
                                    used = prefs.sarvamQuotaUsed(),
                                    max = SessionPrefs.SARVAM_MAX_CALLS,
                                    resetAt = prefs.sarvamQuotaResetAt()
                                )
                            },
                            attemptGateway = !serverCapped,
                            onGatewayError = { reason ->
                                _gatewayError.value = "Gateway: $reason"
                            }
                        )
                        // One row failing (timeout/401/403) must not abort the
                        // whole pass anymore — the old break hid the first
                        // error and left the rest native forever. Keep going;
                        // JIT at play time retries the missed rows.
                        if (file == null) {
                            prefetchRemaining.value = (prefetchRemaining.value - 1).coerceAtLeast(0)
                            continue
                        }
                        // A row succeeded, so device budget exists — clear the
                        // device pill. M5: the gateway pill survives (daily
                        // server cap, not per-slot device budget). A success
                        // also heals a stale gateway error box.
                        if (quotaPill.value.isNotBlank() && !isGatewayPill()) quotaPill.value = ""
                        if (_gatewayError.value.isNotBlank()) _gatewayError.value = ""
                        prefetchRemaining.value = (prefetchRemaining.value - 1).coerceAtLeast(0)
                    }
                }
            }
        }
    }
}
