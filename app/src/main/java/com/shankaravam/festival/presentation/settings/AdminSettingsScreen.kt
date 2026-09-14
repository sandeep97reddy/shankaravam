package com.shankaravam.festival.presentation.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.theme.CrimsonRose
import com.shankaravam.festival.core.theme.CrimsonWash
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.MaroonWash
import com.shankaravam.festival.core.theme.SaffronWash
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.newRecordId
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.AccessPolicy
import com.shankaravam.festival.domain.model.ActivityActions
import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.AdminConfig
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.model.UserRole
import com.shankaravam.festival.domain.model.roleOf
import com.shankaravam.festival.presentation.common.TempleAppBar
import com.shankaravam.festival.presentation.common.containerViewModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Admin-owned VM: secure voice key, role label, event closure, language, counter identity. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminSettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val event: Event? = null,
        val role: UserRole = UserRole.ORGANIZER,
        val isCloudEvent: Boolean = false,
        val encrypted: Boolean = false,
        val user: com.shankaravam.festival.data.remote.CloudUser? = null,
        val cloudUser: String? = null,
        val cloudEmail: String? = null,
        val isAuthConfigured: Boolean = false,
        val hasSarvamKey: Boolean = false,
        /** F5: last shared-key apply (0 = device-only key or none). */
        val voiceSyncedAt: Long = 0L
    )

    private val keyTick = MutableStateFlow(0)

    val uiState: StateFlow<UiState> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                combine(
                    container.authRepository.user,
                    keyTick
                ) { user, _ ->
                    UiState(
                        encrypted = container.secureKeys.isEncrypted,
                        user = user,
                        cloudUser = user?.uid,
                        cloudEmail = user?.email,
                        isAuthConfigured = container.authRepository.isConfigured,
                        hasSarvamKey = container.secureKeys.getSarvamKey().isNotBlank(),
                        voiceSyncedAt = container.sessionPrefs.lastVoiceSyncAt()
                    )
                }
            } else {
                combine(
                    container.eventRepository.observeEvent(eventId),
                    container.authRepository.user,
                    keyTick
                ) { event: Event?, user: com.shankaravam.festival.data.remote.CloudUser?, _ ->
                    UiState(
                        event = event,
                        role = roleOf(container.sessionPrefs.myRole(eventId)),
                        isCloudEvent = container.sessionPrefs.isCloudEvent(eventId),
                        encrypted = container.secureKeys.isEncrypted,
                        user = user,
                        cloudUser = user?.uid,
                        cloudEmail = user?.email,
                        isAuthConfigured = container.authRepository.isConfigured,
                        hasSarvamKey = container.secureKeys.getSarvamKey().isNotBlank(),
                        voiceSyncedAt = container.sessionPrefs.lastVoiceSyncAt()
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    val isAuthConfigured: Boolean
        get() = container.authRepository.isConfigured

    private val _signInIntent = MutableStateFlow<Intent?>(null)
    val signInIntent: StateFlow<Intent?> = _signInIntent.asStateFlow()

    fun signIn() {
        when (val result = container.authRepository.googleSignInIntent()) {
            is Outcome.Ok -> _signInIntent.value = result.value
            is Outcome.Err -> _notice.value = result.message
        }
    }

    fun consumeSignInIntent() {
        _signInIntent.value = null
    }

    fun completeSignIn(data: Intent?) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = container.authRepository.handleSignInResult(data)) {
                is Outcome.Ok -> {
                    keyTick.value += 1
                    _notice.value = "Signed in as ${result.value.displayName ?: result.value.email}."
                    // F5: fresh login picks up the shared voice immediately.
                    autoPullVoice()
                }
                is Outcome.Err -> _notice.value = result.message
            }
            _busy.value = false
        }
    }

    fun signOut() {
        container.authRepository.signOut()
        keyTick.value += 1
        _notice.value = "Signed out of Google account."
    }

    fun getSavedSarvamKey(): String = container.secureKeys.getSarvamKey()
    fun getSavedSarvamSpeaker(): String = container.sessionPrefs.sarvamSpeaker

    val appLanguage: StateFlow<String> = container.sessionPrefs.appLanguage
    fun setLanguage(lang: String) = container.sessionPrefs.setAppLanguage(lang)

    val counterName: StateFlow<String> = container.sessionPrefs.counterName
    fun setCounterName(name: String) = container.sessionPrefs.setCounterName(name)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun consumeNotice() { _notice.value = null }

    val nativeVoices: StateFlow<List<String>> =
        container.ttsEngine.native.ready
            .map { ready ->
                if (ready) container.ttsEngine.native.getAvailableTeluguVoices() else emptyList()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** F6 structured voice list with network/embedded badges */
    val nativeVoiceInfos: StateFlow<List<com.shankaravam.festival.domain.model.NativeVoiceInfo>> =
        container.ttsEngine.native.ready
            .map { ready ->
                if (ready) container.ttsEngine.native.getAvailableTeluguVoiceInfos() else emptyList()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Phase 2: sourced from SessionPrefs flows (not one-time snapshots), so
     * picks made in the Announcement queue card appear here instantly and
     * vice versa (RC1 remedy).
     */
    val selectedVoice: StateFlow<String?> = container.sessionPrefs.nativeTtsVoiceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), container.sessionPrefs.nativeTtsVoice)

    val speed: StateFlow<Float> = container.sessionPrefs.nativeTtsSpeedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), container.sessionPrefs.nativeTtsSpeed)

    val chimeEnabled: StateFlow<Boolean> = container.sessionPrefs.playTempleChimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), container.sessionPrefs.playTempleChime)

    /**
     * Phase 2 explicit engine mode (RC5). Selecting it here drives queue
     * playback instantly via SessionPrefs flow + engine provider.
     */
    val engineMode: StateFlow<com.shankaravam.festival.domain.model.VoiceEngineMode> =
        container.sessionPrefs.voiceEngineModeFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), container.sessionPrefs.voiceEngineMode)

    /** Live speaker (Phase 2): queue-card picks flow here for the chips below. */
    val speakerFlow: StateFlow<String> = container.sessionPrefs.sarvamSpeakerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), container.sessionPrefs.sarvamSpeaker)

    fun setEngineMode(mode: com.shankaravam.festival.domain.model.VoiceEngineMode) {
        // F5 explicit lock: a deliberate Offline tap survives auto-pull flips.
        container.sessionPrefs.voiceOfflineLocked =
            mode == com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
        container.sessionPrefs.voiceEngineMode = mode
    }

    fun setChimeEnabled(enabled: Boolean) {
        container.sessionPrefs.playTempleChime = enabled
    }

    fun setNativeVoice(name: String?) {
        container.sessionPrefs.nativeTtsVoice = name
        // Phase 2: blank/null resets the live engine voice immediately (was a
        // no-op until restart). setVoiceByName("") now delegates to resetVoice().
        container.ttsEngine.native.setVoiceByName(name ?: "")
        // Fix-B4: picking a device voice only configures the (offline/fallback)
        // voice — it must NOT flip the engine mode. Mode changes happen solely
        // via the explicit chips (setEngineMode) or the queue voice picker.
    }

    fun setSpeed(s: Float) {
        container.sessionPrefs.nativeTtsSpeed = s
        container.ttsEngine.native.setSpeechRate(s)
    }

    fun testNativeSpeech() {
        container.ttsEngine.native.speak("శ్రీ మహేష్ బాబు గారు, వెయ్యి నూట పదహారు రూపాయలు.")
    }

    fun setSarvamSpeaker(speaker: String) {
        container.sessionPrefs.sarvamSpeaker = speaker
    }

    fun saveKeyLocally(key: String, speaker: String) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        container.secureKeys.setSarvamKey(clean)
        container.sessionPrefs.sarvamSpeaker = speaker
        // Phase 2: configuring a cloud key means cloud mode — unless the key
        // is blank (clearing falls back to offline).
        container.sessionPrefs.voiceEngineMode = if (clean.isBlank()) {
            com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
        } else {
            com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD
        }
        // F5 explicit lock follows the same intent.
        container.sessionPrefs.voiceOfflineLocked = clean.isBlank()
        keyTick.value += 1
        _notice.value = if (clean.isBlank()) {
            "Cloud key removed — offline voice active."
        } else if (container.secureKeys.isEncrypted) {
            "✓ Key stored encrypted & cloud voice activated."
        } else {
            "✓ Key stored locally & cloud voice activated."
        }
    }

    /**
     * Phase 2: key removal lives HERE (Settings), not in the Announcement
     * queue — the queue is an operational console with no secrets handling
     * (RC1: AccessPolicy.canManageKeys is head-scoped for publish; local
     * clear + offline fallback is safe for any role).
     */
    fun clearKeyLocally() {
        container.secureKeys.setSarvamKey("")
        container.sessionPrefs.voiceEngineMode =
            com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
        // F5: removing the key is an explicit offline choice — auto-pull must
        // not flip the mode back (the key still updates silently underneath).
        container.sessionPrefs.voiceOfflineLocked = true
        keyTick.value += 1
        _notice.value = "Cloud key removed — offline device voice active."
    }

    fun pushKey(key: String, speaker: String) {
        val uid = container.authRepository.user.value?.uid
        if (uid == null) {
            _notice.value = "Sign in first using Google Account above."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            when (container.syncService.writeTtsKey(key, speaker, uid)) {
                is Outcome.Ok -> {
                    keyTick.value += 1
                    _notice.value = "Shared key published for collectors."
                }
                is Outcome.Err -> _notice.value = "Publish failed — saved on this device only."
            }
            _busy.value = false
        }
    }

    fun pullKey() {
        viewModelScope.launch {
            _busy.value = true
            // F5 manual pull: explicit tap bypasses the offline lock.
            val pulled = container.syncService.maybeAutoPullVoice(force = true, respectLock = false)
            if (pulled.applied) {
                // Drafts follow automatically: the speaker draft tracks the
                // live speaker flow, the key field backfills when empty.
                keyTick.value += 1
                _notice.value = "Shared voice settings applied."
            } else if (!pulled.remotePresent) {
                _notice.value = "No shared key published yet — the head publishes it from this card."
            } else {
                keyTick.value += 1
                _notice.value = "Already up to date with the shared voice."
            }
            _busy.value = false
        }
    }

    /** F5 quiet pull for Settings entry + sign-in: silent unless applied. */
    fun autoPullVoice() {
        viewModelScope.launch {
            if (container.syncService.maybeAutoPullVoice().applied) {
                keyTick.value += 1
            }
        }
    }

    private val _sarvamTestStatus = MutableStateFlow<String?>(null)
    val sarvamTestStatus: StateFlow<String?> = _sarvamTestStatus.asStateFlow()

    private val _testingSarvam = MutableStateFlow(false)
    val testingSarvam: StateFlow<Boolean> = _testingSarvam.asStateFlow()

    fun testSarvamVoice(key: String, speaker: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            _sarvamTestStatus.value = "Please enter an API key first."
            return
        }
        if (_testingSarvam.value) return // debounce: a test is already running
        if (!container.sessionPrefs.takeSarvamSlot()) {
            _sarvamTestStatus.value = "✗ Free-tier limit reached (20 Sarvam calls per 30 min). Try later — offline voice still works."
            return
        }
        viewModelScope.launch {
            _testingSarvam.value = true
            // Phase 3 disclosure: a verification test spends real quota.
            _sarvamTestStatus.value = "Testing Sarvam AI connection… (uses 1 of 20 cloud calls)"
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val api = com.shankaravam.festival.data.remote.SarvamApiService.create()
                    val normSpeaker = com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(speaker)
                    val payload = org.json.JSONObject()
                        .put("text", "ఓం నమో వేంకటేశాయ. శర్వం క్లౌడ్ గొంతు పరీక్ష విజయవంతమైంది.")
                        .put("language_code", "te-IN")
                        .put("speaker", normSpeaker)
                        .put("model", "bulbul:v3")
                        .put("output_audio_codec", "mp3")
                        .toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                    val response = api.synthesize(trimmed, payload).string()
                    val json = org.json.JSONObject(response)
                    val audioBase64 = if (json.has("audios")) {
                        json.getJSONArray("audios").getString(0)
                    } else if (json.has("audio")) {
                        json.getString("audio")
                    } else throw java.io.IOException("Missing audio in Sarvam response")
                    val bytes = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)
                    val testFile = java.io.File(container.appContext.cacheDir, "audio_test_sample.mp3")
                    testFile.writeBytes(bytes)
                    testFile
                }
            }
            result.fold(
                onSuccess = { file ->
                    saveKeyLocally(trimmed, speaker)
                    _sarvamTestStatus.value = "✓ Key Verified & Saved! Playing audio…"
                    container.ttsEngine.playFile(file, onDone = {}, onError = {})
                },
                onFailure = { e ->
                    val errorMsg = com.shankaravam.festival.data.remote.SarvamErrorParser.parse(e)
                    _sarvamTestStatus.value = "✗ Test failed: $errorMsg"
                }
            )
            _testingSarvam.value = false
        }
    }

    fun closeEvent(event: Event) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            container.eventRepository.closeEvent(event.id, now)
            container.activityRepository.log(
                ActivityRecord(
                    id = newRecordId(),
                    eventId = event.id,
                    actionType = ActivityActions.EVENT_CLOSED,
                    details = event.name,
                    actorId = container.sessionPrefs.attributionName(),
                    timestamp = now
                )
            )
            _notice.value = "“${event.name}” closed — records stay readable, new entries stop."
        }
    }

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    /** Display-only counts for the delete confirmation dialog. */
    suspend fun getDeleteCounts(eventId: String): Pair<Int, Int> =
        container.deleteLocalEvent.getCounts(eventId)

    /** Phase 3 local scrub. Use-case re-gates isCloudEvent; notice surfaces the result. */
    fun deleteLocalEvent(event: Event) {
        if (_deleting.value) return
        viewModelScope.launch {
            _deleting.value = true
            when (val result = container.deleteLocalEvent(event.id)) {
                is Outcome.Ok -> _notice.value = "“${event.name}” and its records were deleted from this device."
                is Outcome.Err -> _notice.value = result.message
            }
            _deleting.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminSettingsViewModel = containerViewModel { AdminSettingsViewModel(it) },
    /** F1: open with the Team & Cloud Sync accordion expanded (Sync tile deep-link). */
    expandTeam: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val currentLang by viewModel.appLanguage.collectAsState()
    val counterName by viewModel.counterName.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val chime by viewModel.chimeEnabled.collectAsState()
    val teluguVoices by viewModel.nativeVoices.collectAsState()
    val teluguVoiceInfos by viewModel.nativeVoiceInfos.collectAsState()
    val sarvamTestStatus by viewModel.sarvamTestStatus.collectAsState()
    val testingSarvam by viewModel.testingSarvam.collectAsState()
    val signInIntent by viewModel.signInIntent.collectAsState()
    val deleting by viewModel.deleting.collectAsState()
    val engineMode by viewModel.engineMode.collectAsState()
    // Phase 2: live speaker flow — a pick in the queue card refreshes the
    // cloud-card chips here without reopening Settings.
    val liveSpeaker by viewModel.speakerFlow.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.completeSignIn(result.data) }

    LaunchedEffect(signInIntent) {
        signInIntent?.let {
            signInLauncher.launch(it)
            viewModel.consumeSignInIntent()
        }
    }

    // F5: entering Settings picks up a rotated shared voice (throttled, silent).
    LaunchedEffect(Unit) { viewModel.autoPullVoice() }

    var showCloseConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(showDeleteConfirm, state.event?.id) {
        if (showDeleteConfirm) {
            deleteCounts = null
            state.event?.let { deleteCounts = viewModel.getDeleteCounts(it.id) }
        }
    }

    // Hoisted draft state for form fields so collapsing accordion does NOT discard input
    var counterDraft by remember(counterName) { mutableStateOf(counterName) }
    var sarvamKeyDraft by remember { mutableStateOf(viewModel.getSavedSarvamKey()) }
    var sarvamSpeakerDraft by remember { mutableStateOf(viewModel.getSavedSarvamSpeaker()) }

    // When a key is saved/pulled — or the speaker is picked in the queue card —
    // refresh drafts accordingly. The key field backfills only when empty so
    // typing is never clobbered; chips always follow the live speaker.
    LaunchedEffect(state.hasSarvamKey, liveSpeaker) {
        val savedKey = viewModel.getSavedSarvamKey()
        if (sarvamKeyDraft.isBlank() && sarvamKeyDraft != savedKey) {
            sarvamKeyDraft = savedKey
        }
        sarvamSpeakerDraft = liveSpeaker
    }

    // Collapsible accordion states - minimal & clean for easy navigation
    var voiceExpanded by remember { mutableStateOf(false) }
    var counterExpanded by remember { mutableStateOf(false) }
    var cloudExpanded by remember { mutableStateOf(false) }
    var adminExpanded by remember { mutableStateOf(false) }
    // F1 canonical Team section (gear home for Invite/Join/sync).
    var teamExpanded by remember { mutableStateOf(expandTeam) }

    val performBack = {
        val currentSaved = viewModel.getSavedSarvamKey().trim()
        val draft = sarvamKeyDraft.trim()
        if (draft.isNotBlank() && draft != currentSaved) {
            viewModel.saveKeyLocally(draft, sarvamSpeakerDraft)
        }
        onBack()
    }

    androidx.activity.compose.BackHandler(onBack = performBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TempleAppBar(
                title = if (currentLang == SessionPrefs.LANG_TELUGU) "సెట్టింగ్స్ & ధ్వని" else "Settings & Voice",
                onBack = performBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Language Selection (Always visible, prominent at top for easy switching)
            LanguageSelectionCard(
                currentLang = currentLang,
                onSelectLanguage = { viewModel.setLanguage(it) }
            )

            // 2. Google Account (Direct authentication & committee identity)
            GoogleAccountCard(
                user = state.user,
                isConfigured = state.isAuthConfigured,
                currentLang = currentLang,
                busy = busy,
                onSignIn = { viewModel.signIn() },
                onSignOut = { viewModel.signOut() }
            )

            // 3. Role & Storage Security Status Banner
            val isHeadAdmin = state.role == UserRole.GLOBAL_HEAD
                && AdminConfig.isGlobalHeadEmail(state.cloudEmail)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = TempleSaffron,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHeadAdmin) "👑 Global Head (${AdminConfig.GLOBAL_HEAD_EMAIL}) • Verified"
                            else "Role: ${state.role.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.encrypted) "Storage: Encrypted Keystore ✓" else "Storage: Local Device App Data",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Notice Banner if any
            notice?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SaffronWash,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = TempleSaffron,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                viewModel.consumeNotice()
            }

            val event = state.event
            // F1 canonical Team & Cloud Sync (lives ABOVE the event gate: Join
            // works with zero local events, so collectors never fabricate a
            // dummy duplicate to accept an invite).
            SettingsAccordionCard(
                title = "Team & Cloud Sync",
                teluguTitle = "జట్టు & క్లౌడ్ సమకాలీకరణ",
                summary = if (state.user != null) {
                    if (event != null) "Festival: ${event.name}" else "Join a festival — nothing to create"
                } else {
                    if (currentLang == SessionPrefs.LANG_TELUGU) "జట్టులో చేరడానికి సైన్ ఇన్ చేయండి" else "Sign in to join a team"
                },
                icon = Icons.Filled.Group,
                iconTint = DeepMaroon,
                iconBackground = MaroonWash,
                isExpanded = teamExpanded,
                onToggle = { teamExpanded = !teamExpanded }
            ) {
                TeamSyncSection(
                    counterName = counterName,
                    onSaveCounter = { viewModel.setCounterName(it) }
                )
            }
            if (event == null) {
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU) "దయచేసి ముందుగా ఒక ఈవెంట్‌ను ఎంచుకోండి లేదా సృష్టించండి." else "Select or create an event first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // 3. Accordion Section 1: Temple Voice & Audio
            // Fix-B5: in cloud mode the summary names the ACTIVE speaker
            // (not the native fallback voice); in offline mode the fallback.
            val voiceDisplayName = if (engineMode == com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE) {
                selectedVoice?.substringAfterLast("-") ?: if (currentLang == SessionPrefs.LANG_TELUGU) "సిస్టమ్ డిఫాల్ట్" else "Default"
            } else {
                liveSpeaker.replaceFirstChar { it.uppercase() }
            }
            val chimeStatus = if (chime) (if (currentLang == SessionPrefs.LANG_TELUGU) "గంట నాదం ఆన్" else "Bell On") else (if (currentLang == SessionPrefs.LANG_TELUGU) "గంట నాదం ఆఫ్" else "Bell Off")
            val modeStatus = if (engineMode == com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE) {
                if (currentLang == SessionPrefs.LANG_TELUGU) "ఆఫ్‌లైన్" else "Offline"
            } else {
                if (currentLang == SessionPrefs.LANG_TELUGU) "క్లౌడ్" else "Cloud"
            }
            val voiceSummary = "$modeStatus • $voiceDisplayName • ${String.format(java.util.Locale.US, "%.2fx", speed)} • $chimeStatus"

            SettingsAccordionCard(
                title = "Temple Voice & Audio",
                teluguTitle = "గొంతు & ధ్వని సెట్టింగ్స్",
                summary = voiceSummary,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                iconTint = TempleSaffron,
                iconBackground = SaffronWash,
                isExpanded = voiceExpanded,
                onToggle = { voiceExpanded = !voiceExpanded }
            ) {
                NativeVoiceContent(
                    voices = teluguVoices,
                    voiceInfos = teluguVoiceInfos,
                    selectedVoice = selectedVoice,
                    speed = speed,
                    chimeEnabled = chime,
                    engineMode = engineMode,
                    onSetEngineMode = { viewModel.setEngineMode(it) },
                    onSelectVoice = { viewModel.setNativeVoice(it) },
                    onSetSpeed = { viewModel.setSpeed(it) },
                    onSetChime = { viewModel.setChimeEnabled(it) },
                    onTestVoice = { viewModel.testNativeSpeech() }
                )
            }

            // 4. Accordion Section 2: Advanced: Counter Identity (Draft hoisted to screen)
            val counterSummary = if (counterName.isNotBlank()) {
                if (currentLang == SessionPrefs.LANG_TELUGU) "కౌంటర్: $counterName" else "Active: $counterName"
            } else {
                if (currentLang == SessionPrefs.LANG_TELUGU) "డిఫాల్ట్ (నమోదు కాలేదు)" else "Not set (Default)"
            }

            SettingsAccordionCard(
                title = "Advanced: Counter Identity",
                teluguTitle = "అధునాతన: కౌంటర్ పేరు",
                summary = counterSummary,
                icon = Icons.Filled.Storefront,
                iconTint = TempleSaffron,
                iconBackground = SaffronWash,
                isExpanded = counterExpanded,
                onToggle = { counterExpanded = !counterExpanded }
            ) {
                CounterIdentityContent(
                    draft = counterDraft,
                    onDraftChange = { counterDraft = it },
                    savedCounterName = counterName,
                    onSaveCounter = { viewModel.setCounterName(it) }
                )
            }

            // 5. Accordion Section 3: Advanced: Cloud Voice (Sarvam AI) (Draft hoisted to screen)
            // Fix-B5: the summary follows the engine mode — a saved key while
            // offline must not claim "Cloud Voice Configured" as active.
            val cloudSummary = when {
                engineMode == com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE && state.hasSarvamKey ->
                    if (currentLang == SessionPrefs.LANG_TELUGU) "ఆఫ్‌లైన్ మోడ్ (కీ సేవ్ చేయబడింది)" else "Offline Mode Active (Key Saved)"
                state.hasSarvamKey ->
                    if (currentLang == SessionPrefs.LANG_TELUGU) "శర్వం క్లౌడ్ గొంతు సిద్ధంగా ఉంది" else "Sarvam Cloud Voice Configured"
                else ->
                    if (currentLang == SessionPrefs.LANG_TELUGU) "ఆఫ్‌లైన్ గొంతు మాత్రమే యాక్టివ్" else "Offline Voice Active (Android TTS)"
            }

            SettingsAccordionCard(
                title = "Advanced: Cloud Voice (Sarvam AI)",
                teluguTitle = "అధునాతన: క్లౌడ్ గొంతు",
                summary = cloudSummary,
                icon = Icons.Filled.Cloud,
                iconTint = TempleSaffron,
                iconBackground = SaffronWash,
                isExpanded = cloudExpanded,
                onToggle = { cloudExpanded = !cloudExpanded }
            ) {
                // F5 shared-vs-local pill: honest about where the key came from.
                val voiceSource = when {
                    state.hasSarvamKey && state.voiceSyncedAt > 0L -> {
                        val t = java.text.SimpleDateFormat("d MMM h:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(state.voiceSyncedAt))
                        "☁️ Shared voice: ${liveSpeaker.replaceFirstChar { it.uppercase() }} (synced $t)" to true
                    }
                    state.hasSarvamKey ->
                        "📱 Key on this device only — pull the shared key below" to false
                    else -> null
                }
                VoiceKeyContent(
                    key = sarvamKeyDraft,
                    onKeyChange = { sarvamKeyDraft = it },
                    speaker = sarvamSpeakerDraft,
                    onSpeakerChange = {
                        sarvamSpeakerDraft = it
                        viewModel.setSarvamSpeaker(it)
                    },
                    savedKey = viewModel.getSavedSarvamKey(),
                    busy = busy,
                    testStatus = sarvamTestStatus,
                    testing = testingSarvam,
                    hasKey = state.hasSarvamKey,
                    canPublish = AccessPolicy.canManageKeys(state.role)
                        && AdminConfig.isGlobalHeadEmail(state.cloudEmail),
                    onSaveLocal = { key, speaker -> viewModel.saveKeyLocally(key, speaker) },
                    onClearKey = { sarvamKeyDraft = ""; viewModel.clearKeyLocally() },
                    onPush = { key, speaker -> viewModel.pushKey(key, speaker) },
                    onPull = { viewModel.pullKey() },
                    onTestSarvam = { key, speaker -> viewModel.testSarvamVoice(key, speaker) },
                    voiceSource = voiceSource?.first,
                    voiceShared = voiceSource?.second == true
                )
            }

            // 6. Accordion Section 4: Festival Administration (Only visible if canCloseEvent)
            if (AccessPolicy.canCloseEvent(state.role)) {
                val adminSummary = if (event.status.name == "ACTIVE") {
                    if (currentLang == SessionPrefs.LANG_TELUGU) "ఈవెంట్: ${event.name} • యాక్టివ్" else "Event: ${event.name} • Active"
                } else {
                    if (currentLang == SessionPrefs.LANG_TELUGU) "ఈవెంట్: ${event.name} • ముగిసింది" else "Event: ${event.name} • Closed"
                }

                SettingsAccordionCard(
                    title = "Festival Administration",
                    teluguTitle = "ఉత్సవ నిర్వహణ",
                    summary = adminSummary,
                    icon = Icons.Filled.Lock,
                    iconTint = CrimsonRose,
                    iconBackground = CrimsonWash,
                    isExpanded = adminExpanded,
                    onToggle = { adminExpanded = !adminExpanded }
                ) {
                    FestivalAdminContent(
                        event = event,
                        isCloudEvent = state.isCloudEvent,
                        deleting = deleting,
                        onRequestClose = { showCloseConfirm = true },
                        onRequestDelete = { showDeleteConfirm = true }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = {
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU) "ఈ ఈవెంట్‌ను ముగించాలా?" else "Close this event?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU)
                        "మొత్తాలు, రికార్డులు మరియు నివేదికలు అలాగే ఉంటాయి. కొత్త విరాళాలు మరియు ఖర్చులు నమోదు చేయడం ఆగిపోతుంది."
                    else
                        "Totals, history and reports stay readable. New donations and expenses will stop."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.event?.let { viewModel.closeEvent(it) }
                    showCloseConfirm = false
                }) {
                    Text(
                        if (currentLang == SessionPrefs.LANG_TELUGU) "ఈవెంట్ ముగించు" else "Close Event",
                        color = CrimsonRose,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text(if (currentLang == SessionPrefs.LANG_TELUGU) "రద్దు చేయి" else "Keep Open")
                }
            }
        )
    }

    if (showDeleteConfirm && state.event != null && !state.isCloudEvent) {
        val counts = deleteCounts
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = {
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU) "స్థానిక ఈవెంట్‌ను తొలగించాలా?" else "Delete local festival?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = when {
                        counts == null -> if (currentLang == SessionPrefs.LANG_TELUGU) "రికార్డులు లెక్కిస్తోంది…" else "Counting records…"
                        counts.first == 0 && counts.second == 0 ->
                            if (currentLang == SessionPrefs.LANG_TELUGU) "ఈ ఈవెంట్‌లో విరాళాలు లేదా ఖర్చులు లేవు. ఇది వెంటనే ఈ పరికరం నుండి తొలగించబడుతుంది."
                            else "This event has no donations or expenses recorded. It will be removed immediately from this device."
                        else ->
                            if (currentLang == SessionPrefs.LANG_TELUGU) "⚠️ హెచ్చరిక: ఈ ఉత్సవంలో ${counts.first} విరాళాలు మరియు ${counts.second} ఖర్చులు ఉన్నాయి. దీన్ని తొలగిస్తే ఈ ఫోన్ నుండి అన్ని లావాదేవీలు మరియు ఆడియో ఫైళ్లు శాశ్వతంగా తొలగిపోతాయి."
                            else "⚠️ WARNING: This festival contains ${counts.first} donations and ${counts.second} expenses. Deleting it will permanently remove all transactions and audio files from this phone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.event?.let { viewModel.deleteLocalEvent(it) }
                        showDeleteConfirm = false
                    },
                    enabled = !deleting
                ) {
                    Text(
                        if (currentLang == SessionPrefs.LANG_TELUGU) "పూర్తిగా తొలగించు" else "Delete Everything",
                        color = CrimsonRose,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !deleting) {
                    Text(if (currentLang == SessionPrefs.LANG_TELUGU) "రద్దు చేయి" else "Cancel")
                }
            }
        )
    }
}

/** Reusable clean Accordion Card with unified temple aesthetics, WCAG AAA typography, and a11y role. */
@Composable
private fun SettingsAccordionCard(
    title: String,
    teluguTitle: String,
    summary: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (isExpanded) "Collapse $title" else "Expand $title",
                        onClick = onToggle
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBackground)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (teluguTitle.isNotBlank()) {
                        Text(
                            text = teluguTitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TempleSaffron
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant // onSurfaceVariant is #574A40 (dark walnut), 7.4:1 contrast (WCAG AAA)
                    )
                }

                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    content()
                }
            }
        }
    }
}

/** Top Language Card — clean, responsive, directly accessible. */
@Composable
private fun LanguageSelectionCard(
    currentLang: String,
    onSelectLanguage: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SaffronWash)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = TempleSaffron,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "App Language / యాప్ భాష",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (currentLang == SessionPrefs.LANG_TELUGU) "తెలుగు ఎంపిక చేయబడింది" else "English selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = currentLang == SessionPrefs.LANG_ENGLISH,
                    onClick = { onSelectLanguage(SessionPrefs.LANG_ENGLISH) },
                    label = { Text("English", fontWeight = FontWeight.SemiBold) }
                )
                FilterChip(
                    selected = currentLang == SessionPrefs.LANG_TELUGU,
                    onClick = { onSelectLanguage(SessionPrefs.LANG_TELUGU) },
                    label = { Text("తెలుగు (Telugu)", fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    }
}

/** Prominent Google Account Authentication & Committee Identity Card. */
@Composable
private fun GoogleAccountCard(
    user: com.shankaravam.festival.data.remote.CloudUser?,
    isConfigured: Boolean,
    currentLang: String,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val summary = when {
        !isConfigured -> {
            if (currentLang == SessionPrefs.LANG_TELUGU) "క్లౌడ్ సర్వీసులు కాన్ఫిగర్ చేయబడలేదు"
            else "Cloud services not configured"
        }
        user != null -> {
            val name = user.displayName?.takeIf { it.isNotBlank() }
            val email = user.email?.takeIf { it.isNotBlank() }
            when {
                name != null && email != null -> "$name • $email"
                name != null -> name
                email != null -> email
                else -> if (currentLang == SessionPrefs.LANG_TELUGU) "ఖాతా అనుసంధానమైంది" else "Account Connected"
            }
        }
        else -> {
            if (currentLang == SessionPrefs.LANG_TELUGU) "ఆఫ్‌లైన్ మోడ్ (లాగిన్ కాలేదు)"
            else "Offline Mode (Not signed in)"
        }
    }

    SettingsAccordionCard(
        title = if (currentLang == SessionPrefs.LANG_TELUGU) "గూగుల్ ఖాతా" else "Google Account",
        teluguTitle = if (currentLang == SessionPrefs.LANG_TELUGU) "" else "గూగుల్ ఖాతా",
        summary = summary,
        icon = Icons.Filled.Person,
        iconTint = TempleSaffron,
        iconBackground = SaffronWash,
        isExpanded = isExpanded,
        onToggle = { isExpanded = !isExpanded },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isConfigured) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (currentLang == SessionPrefs.LANG_TELUGU)
                            "క్లౌడ్ సర్వీసులు కాన్ఫిగర్ చేయబడలేదు. యాప్ 100% ఆఫ్‌లైన్‌లో సురక్షితంగా పనిచేస్తుంది."
                        else
                            "Cloud services not configured. App runs 100% offline securely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else if (user == null) {
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU)
                        "గూగుల్ ఖాతాతో లాగిన్ అవ్వడం ద్వారా క్లౌడ్ వాయిస్ కీలు మరియు ఇతర కౌంటర్లతో సమకాలీకరణ పొందవచ్చు."
                    else
                        "Sign in to synchronize multi-counter data and share cloud voice settings with your festival committee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onSignIn,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (busy) {
                            if (currentLang == SessionPrefs.LANG_TELUGU) "లాగిన్ అవుతోంది…" else "Signing in…"
                        } else {
                            if (currentLang == SessionPrefs.LANG_TELUGU) "గూగుల్‌తో సైన్ ఇన్ చేయండి" else "Sign in with Google"
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(TempleSaffron.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = (user.displayName ?: user.email ?: "U").take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = TempleSaffron,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.displayName ?: if (currentLang == SessionPrefs.LANG_TELUGU) "వినియోగదారుడు" else "User",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            user.email?.let { email ->
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (currentLang == SessionPrefs.LANG_TELUGU) "లాగౌట్ చేయండి" else "Sign Out",
                        color = CrimsonRose,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Voice engine mode + inbuilt Android TTS voice list, speech speed, bell toggle, and audio preview. */
@Composable
private fun NativeVoiceContent(
    voices: List<String>,
    voiceInfos: List<com.shankaravam.festival.domain.model.NativeVoiceInfo> = emptyList(),
    selectedVoice: String?,
    speed: Float,
    chimeEnabled: Boolean,
    engineMode: com.shankaravam.festival.domain.model.VoiceEngineMode,
    onSetEngineMode: (com.shankaravam.festival.domain.model.VoiceEngineMode) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetChime: (Boolean) -> Unit,
    onTestVoice: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Phase 2 explicit engine selector (RC5): offline is free/unlimited,
        // cloud needs a key (managed in the Cloud Voice card below).
        Text(
            text = "Voice Engine / ఇంజిన్:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = engineMode == com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE,
                onClick = { onSetEngineMode(com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE) },
                label = { Text("📱 Offline (Free)") }
            )
            FilterChip(
                selected = engineMode == com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD,
                onClick = { onSetEngineMode(com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD) },
                label = { Text("☁️ Sarvam Cloud HD") }
            )
        }
        if (engineMode == com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD) {
            Text(
                text = "Cloud voice active — speaker & API key are managed in the Cloud Voice card below. Device voice, speed and bell still apply as fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Offline Telugu voice built into Android. Select from voices installed on your device:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (voiceInfos.isNotEmpty() || voices.isNotEmpty()) {
            Text(
                text = "Installed Device Voices (Android TTS):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedVoice == null,
                    onClick = { onSelectVoice(null) },
                    label = { Text("Default / సిస్టమ్") }
                )
                if (voiceInfos.isNotEmpty()) {
                    voiceInfos.forEach { info ->
                        val shortName = info.displayName.takeLast(12)
                        FilterChip(
                            selected = selectedVoice == info.name,
                            onClick = { onSelectVoice(info.name) },
                            label = { Text("${info.badgeLabel} ($shortName)") }
                        )
                    }
                } else {
                    voices.forEach { v ->
                        val label = when {
                            v.contains("network", ignoreCase = true) -> "Device Network"
                            v.contains("local", ignoreCase = true) -> "Device Offline"
                            else -> v.substringAfterLast("-", v.takeLast(10))
                        }
                        FilterChip(
                            selected = selectedVoice == v,
                            onClick = { onSelectVoice(v) },
                            label = { Text("Device ($label)") }
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Default Telugu engine active. (To download additional voices: Android Settings → Accessibility → Text-to-speech output).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Speech Speed / వేగం: ${String.format(java.util.Locale.US, "%.2fx", speed)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.85f, 1.0f, 1.15f).forEach { s ->
                FilterChip(
                    selected = kotlin.math.abs(speed - s) < 0.05f,
                    onClick = { onSetSpeed(s) },
                    label = { Text("${s}x") }
                )
            }
        }

        // Temple Bell Chime Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { onSetChime(!chimeEnabled) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = if (chimeEnabled) TempleSaffron else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Temple bell before announcement",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ప్రకటనకు ముందు ఆలయ గంట నాదం",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = chimeEnabled,
                onCheckedChange = { onSetChime(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TempleSaffron
                )
            )
        }

        // Test Voice Action Button
        Button(
            onClick = onTestVoice,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TempleSaffron,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Test Voice: శ్రీ మహేష్ బాబు గారు", fontWeight = FontWeight.Bold)
        }
    }
}

/** Counter identity setup content (hoisted draft preserved across accordion collapse). */
@Composable
private fun CounterIdentityContent(
    draft: String,
    onDraftChange: (String) -> Unit,
    savedCounterName: String,
    onSaveCounter: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Stamped on every donation & expense on this device (works offline). Shown as Collector in History and after sync.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("e.g. Counter 2 - Ramesh / కౌంటర్ 2 - రమేష్") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSaveCounter(draft) },
            enabled = draft.trim().isNotBlank() && draft.trim() != savedCounterName,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TempleSaffron,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Counter Name / పేరు భద్రపరచండి", fontWeight = FontWeight.Bold)
        }
    }
}

/** Cloud Voice (Sarvam AI) configuration (hoisted key/speaker preserved across accordion collapse). */
@Composable
private fun VoiceKeyContent(
    key: String,
    onKeyChange: (String) -> Unit,
    speaker: String,
    onSpeakerChange: (String) -> Unit,
    savedKey: String = "",
    busy: Boolean,
    testStatus: String?,
    testing: Boolean,
    hasKey: Boolean,
    canPublish: Boolean,
    onSaveLocal: (String, String) -> Unit,
    onClearKey: () -> Unit,
    onPush: (String, String) -> Unit,
    onPull: () -> Unit,
    onTestSarvam: (String, String) -> Unit,
    /** F5 shared-vs-local pill (null = no key at all). */
    voiceSource: String? = null,
    voiceShared: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (voiceSource != null) {
            Surface(
                color = if (voiceShared) Color(0xFFE8F5E9)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = voiceSource,
                    color = if (voiceShared) Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        Text(
            text = "Optional high-fidelity cloud Telugu voice for announcements. Leave empty to use free offline Android voice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text("Sarvam API Key") },
            placeholder = { Text("Empty = offline voice only") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (savedKey.isNotBlank() && key.trim() == savedKey.trim()) {
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    Text("✓ Active Cloud Key Configured & Ready", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Text(
            text = "Voice Speaker / గొంతు ఎంపిక:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "priya" to "🌸 Priya (Female)",
                "shubh" to "🎙️ Shubh (Male)",
                "kavitha" to "🌸 Kavitha (Female)",
                "ratan" to "🎙️ Ratan (Male)"
            ).forEach { (option, label) ->
                FilterChip(
                    selected = com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(speaker) == option,
                    onClick = { onSpeakerChange(option) },
                    label = { Text(label, fontWeight = FontWeight.Medium) }
                )
            }
        }

        if (testStatus != null) {
            val isOk = testStatus.startsWith("✓")
            val isErr = testStatus.startsWith("✗")
            Surface(
                color = when {
                    isOk -> Color(0xFFE8F5E9)
                    isErr -> Color(0xFFFFEBEE)
                    else -> Color(0xFFFFF3E0)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = testStatus,
                    color = when {
                        isOk -> Color(0xFF2E7D32)
                        isErr -> Color(0xFFC62828)
                        else -> Color(0xFFE65100)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Button(
            onClick = { onTestSarvam(key, speaker) },
            enabled = key.isNotBlank() && !busy && !testing,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TempleSaffron,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (testing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text("Testing Sarvam API…", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Test Sarvam Voice / గొంతును పరీక్షించండి", fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onSaveLocal(key, speaker) },
                enabled = !busy && key.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepMaroon,
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            ) { Text("Save Key / భద్రపరచండి", maxLines = 1, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { onPull() },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Pull Shared", maxLines = 1) }
        }
        // Phase 2: key removal lives here (Settings), replacing the old
        // "Remove" action inside the Announcement queue's key dialog.
        if (hasKey || key.isNotBlank()) {
            TextButton(
                onClick = onClearKey,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove cloud key — use offline voice", color = CrimsonRose)
            }
        }
        Button(
            onClick = { onPush(key, speaker) },
            enabled = canPublish && key.isNotBlank() && !busy,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepMaroon,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (canPublish) "Publish for Collectors (Head Only)" else "Publish: Head Only")
        }
        Text(
            text = "Audio files are cached strictly on your local device. Audio is never uploaded to cloud storage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Festival Administration: close ledger + local-only delete (cloud events show guidance only). */
@Composable
private fun FestivalAdminContent(
    event: Event,
    isCloudEvent: Boolean,
    deleting: Boolean,
    onRequestClose: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "“${event.name}” will become read-only history. Financial totals and donor lists remain viewable, but new entries will be locked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onRequestClose,
            enabled = event.status.name == "ACTIVE",
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CrimsonRose,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (event.status.name == "ACTIVE") "Close “${event.name}”" else "Event Closed (Read-Only)",
                fontWeight = FontWeight.Bold
            )
        }
        if (isCloudEvent) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "☁️ Multi-Counter Cloud Event: Synced festivals cannot be deleted on-device to prevent desynchronizing other counters. Use Close Festival to lock entries, or delete the collection in Firebase Console.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            OutlinedButton(
                onClick = onRequestDelete,
                enabled = !deleting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delete Local Festival / ఈ స్థానిక ఈవెంట్‌ను తొలగించండి",
                    color = CrimsonRose,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
