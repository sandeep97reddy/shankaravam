package com.durgamma.festival.presentation.announcement

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durgamma.festival.core.audio.AudioRoute
import com.durgamma.festival.core.tts.AnnouncementLanguage
import com.durgamma.festival.data.local.SessionPrefs
import com.durgamma.festival.di.AppContainer
import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.DonationStatus
import com.durgamma.festival.domain.model.Event
import com.durgamma.festival.presentation.donation.DonationListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Announcement queue (plan §11): playlist honors the current sort/filter,
 * defaults to received+confirmed with pledges excluded. Playback runs on a
 * single cancellable job — stop/skip always wins over completion callbacks.
 */
@Immutable
data class QueueUiState(
    val eventName: String = "",
    val queue: List<Donation> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val repeat: Boolean = false,
    val gapSeconds: Int = 5,
    val sort: String = SessionPrefs.SORT_NEWEST,
    val language: String = SessionPrefs.LANG_TELUGU,
    val showPledged: Boolean = false,
    val tagFilter: String? = null,
    val availableTags: List<String> = emptyList(),
    val route: AudioRoute = AudioRoute.SPEAKER,
    val nativeReady: Boolean = false,
    val testingAudio: Boolean = false,
    val prefetchRemaining: Int = 0,
    val hasEvent: Boolean = false
) {
    val current: Donation? get() = queue.getOrNull(index)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementQueueViewModel(container: AppContainer) : ViewModel() {

    private val prefs: SessionPrefs = container.sessionPrefs
    private val engine = container.ttsEngine
    private val detector = container.routeDetector
    private val donationRepo = container.donationRepository
    private val eventRepo = container.eventRepository

    private val sort = MutableStateFlow(prefs.queueSort)
    private val language = MutableStateFlow(prefs.queueLanguage)
    private val showPledged = MutableStateFlow(false)
    private val tagFilter = MutableStateFlow<String?>(null)
    private val index = MutableStateFlow(-1)
    private val isPlaying = MutableStateFlow(false)
    private val isPaused = MutableStateFlow(false)
    private val repeat = MutableStateFlow(false)
    private val gapSeconds = MutableStateFlow(prefs.queueGapSeconds)
    private val testingAudio = MutableStateFlow(false)
    private val prefetchRemaining = MutableStateFlow(0)

    init {
        detector.start()
        prefetchWhenIdle()
    }

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

    private data class PlayOpts(
        val index: Int,
        val playing: Boolean,
        val paused: Boolean,
        val repeat: Boolean,
        val gap: Int,
        val testing: Boolean,
        val prefetch: Int
    )

    val uiState: StateFlow<QueueUiState> =
        prefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(QueueUiState())
            } else {
                // Only ≤3-flow combines: wider overloads don't resolve on this toolchain.
                val filters = combine(
                    combine(sort, language) { s: String, l: String -> s to l },
                    combine(showPledged, tagFilter) { p: Boolean, t: String? -> p to t }
                ) { sl: Pair<String, String>, pt: Pair<Boolean, String?> ->
                    FilterOpts(sl.first, sl.second, pt.first, pt.second)
                }
                val play = combine(
                    combine(index, isPlaying, isPaused) { i: Int, playing: Boolean, paused: Boolean ->
                        Triple(i, playing, paused)
                    },
                    combine(repeat, gapSeconds) { rep: Boolean, gap: Int -> rep to gap },
                    combine(testingAudio, prefetchRemaining) { testing: Boolean, prefetch: Int ->
                        testing to prefetch
                    }
                ) { a: Triple<Int, Boolean, Boolean>, b: Pair<Boolean, Int>, c: Pair<Boolean, Int> ->
                    PlayOpts(a.first, a.second, a.third, b.first, b.second, c.first, c.second)
                }
                val core = combine(
                    eventRepo.observeEvent(eventId),
                    donationRepo.observeForEvent(eventId),
                    filters
                ) { event: Event?,
                    donations: List<Donation>,
                    opts: FilterOpts -> Triple(event, donations, opts) }
                val withPlay = combine(core, play) { c: Triple<Event?, List<Donation>, FilterOpts>, p: PlayOpts -> c to p }
                val withRoute = combine(withPlay, detector.route) { cp, route: AudioRoute -> Triple(cp.first, cp.second, route) }
                combine(withRoute, engine.nativeReady) { cr: Triple<Triple<Event?, List<Donation>, FilterOpts>, PlayOpts, AudioRoute>, ready: Boolean ->
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
            queue = queue,
            index = play.index.coerceIn(-1, queue.size - 1),
            isPlaying = play.playing,
            isPaused = play.paused,
            repeat = play.repeat,
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
            hasEvent = true
        )
    }

    // ---- transport ----

    fun play() {
        val state = uiState.value
        if (state.queue.isEmpty() || state.isPlaying && !state.isPaused) return
        if (state.isPaused) {
            resume()
            return
        }
        playAt(if (state.index in state.queue.indices) state.index else 0)
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        // MediaPlayer can truly pause; native TTS can only stop (resume restarts the row).
        engine.pausePlayback()
        engine.native.stop()
        isPaused.value = true
        isPlaying.value = false
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        engine.stopAll()
        isPlaying.value = false
        isPaused.value = false
    }

    fun next() {
        val state = uiState.value
        if (state.queue.isEmpty()) return
        val nextIndex = state.index + 1
        if (nextIndex > state.queue.lastIndex) {
            if (state.repeat) playAt(0) else stop()
        } else {
            playAt(nextIndex)
        }
    }

    fun previous() {
        val state = uiState.value
        if (state.queue.isEmpty()) return
        playAt((state.index - 1).coerceAtLeast(0))
    }

    fun replay() {
        val state = uiState.value
        if (state.index in state.queue.indices) playAt(state.index) else play()
    }

    fun jumpTo(position: Int) {
        if (position in uiState.value.queue.indices) playAt(position)
    }

    fun toggleRepeat() {
        repeat.value = !repeat.value
    }

    fun testAudio() {
        if (uiState.value.isPlaying || uiState.value.testingAudio) return
        testingAudio.value = true
        engine.playTestLine(
            onDone = { testingAudio.value = false },
            onError = { testingAudio.value = false }
        )
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
        playJob = viewModelScope.launch {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                engine.playBest(
                    item,
                    state.eventName,
                    languageOf(),
                    onDone = { if (cont.isActive) cont.resume(Unit) {} },
                    onError = { if (cont.isActive) cont.resume(Unit) {} }
                )
                cont.invokeOnCancellation { engine.stopAll() }
            }
            delay(gapSeconds.value * 1000L)
            advance()
        }
    }

    private fun resume() {
        // If the mp3 was paused mid-row, continue it; otherwise restart the row.
        if (engine.resumePlayback()) {
            isPlaying.value = true
            isPaused.value = false
            playJob = viewModelScope.launch {
                // Remaining duration unknown — poll until the player idles.
                while (engine.isFilePlaying()) delay(500)
                delay(gapSeconds.value * 1000L)
                advance()
            }
        } else {
            val at = uiState.value.index
            if (at in uiState.value.queue.indices) playAt(at) else play()
        }
    }

    private fun advance() {
        val state = uiState.value
        if (state.index >= state.queue.lastIndex) {
            if (state.repeat && state.queue.isNotEmpty()) playAt(0) else stop()
        } else {
            playAt(state.index + 1)
        }
    }

    /** Background Sarvam prefetch for rows missing cache (silent; never blocks UI). */
    private fun prefetchWhenIdle() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Wait for the first queue snapshot, then fill caches one by one.
            var lastIds: Set<String> = emptySet()
            uiState.collect { state ->
                val key = prefs.sarvamApiKey
                if (key.isBlank() || !state.hasEvent) return@collect
                val missing = state.queue
                    .filter { it.id !in lastIds || engine.cachedFile(it.id) == null }
                    .filter { engine.cachedFile(it.id) == null }
                    .take(50)
                if (missing.isEmpty()) return@collect
                lastIds = state.queue.map { it.id }.toSet()
                prefetchRemaining.value = missing.size
                val lang = languageOf()
                for (donation in missing) {
                    runCatching {
                        engine.ensureCached(donation, state.eventName, lang, key) { status ->
                            donationRepo.updateAudioStatus(donation.id, status)
                        }
                    }
                    prefetchRemaining.value = (prefetchRemaining.value - 1).coerceAtLeast(0)
                }
            }
        }
    }
}
