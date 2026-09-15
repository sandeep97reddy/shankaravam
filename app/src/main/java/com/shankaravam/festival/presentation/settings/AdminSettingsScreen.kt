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
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.core.ui.haptics.LocalAppHaptics
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
import com.shankaravam.festival.core.util.parseJoinCode
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.AccessPolicy
import com.shankaravam.festival.domain.model.SARVAM_SPEAKER_ORDER
import com.shankaravam.festival.domain.model.sarvamPickerLabel
import com.shankaravam.festival.domain.model.ActivityActions
import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.AdminConfig
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.model.UserRole
import com.shankaravam.festival.domain.model.roleOf
import com.shankaravam.festival.presentation.common.TempleAppBar
import com.shankaravam.festival.presentation.common.containerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
        val isAuthConfigured: Boolean = false
    )

    val uiState: StateFlow<UiState> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                container.authRepository.user.map { user ->
                    UiState(
                        encrypted = container.secureKeys.isEncrypted,
                        user = user,
                        cloudUser = user?.uid,
                        cloudEmail = user?.email,
                        isAuthConfigured = container.authRepository.isConfigured
                    )
                }
            } else {
                combine(
                    container.eventRepository.observeEvent(eventId),
                    container.authRepository.user
                ) { event: Event?, user: com.shankaravam.festival.data.remote.CloudUser? ->
                    UiState(
                        event = event,
                        role = roleOf(container.sessionPrefs.myRole(eventId)),
                        isCloudEvent = container.sessionPrefs.isCloudEvent(eventId),
                        encrypted = container.secureKeys.isEncrypted,
                        user = user,
                        cloudUser = user?.uid,
                        cloudEmail = user?.email,
                        isAuthConfigured = container.authRepository.isConfigured
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
        _notice.value = "Signed out of Google account."
    }

    val appLanguage: StateFlow<String> = container.sessionPrefs.appLanguage
    fun setLanguage(lang: String) = container.sessionPrefs.setAppLanguage(lang)

    val hapticFeedbackEnabled: StateFlow<Boolean> = container.sessionPrefs.hapticFeedbackEnabled
    fun setHapticFeedbackEnabled(enabled: Boolean) = container.sessionPrefs.setHapticFeedbackEnabled(enabled)

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

    // Shared-voice key management lives in CloudSyncViewModel (Committee
    // Shared Voice card) — this screen keeps only the offline Temple Voice
    // controls. Speaker/key flows stay live via speakerFlow/engineMode above.

    /** F5 quiet pull for Settings entry + sign-in: silent unless applied. */
    fun autoPullVoice() {
        viewModelScope.launch {
            runCatching { container.syncService.maybeAutoPullVoice() }
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
    onOpenSync: () -> Unit = {},
    /** F1: open with the Team & Cloud Sync accordion expanded (Sync tile deep-link). */
    expandTeam: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val currentLang by viewModel.appLanguage.collectAsState()
    val hapticEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val counterName by viewModel.counterName.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val chime by viewModel.chimeEnabled.collectAsState()
    val teluguVoices by viewModel.nativeVoices.collectAsState()
    val teluguVoiceInfos by viewModel.nativeVoiceInfos.collectAsState()
    val signInIntent by viewModel.signInIntent.collectAsState()
    val deleting by viewModel.deleting.collectAsState()
    val engineMode by viewModel.engineMode.collectAsState()
    // Live speaker flow — a pick in the queue card refreshes the summary
    // here without reopening Settings.
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

    // Collapsible accordion states - minimal & clean for easy navigation
    var prefsExpanded by remember { mutableStateOf(false) }
    var joinExpanded by remember { mutableStateOf(expandTeam) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var counterExpanded by remember { mutableStateOf(false) }
    var adminExpanded by remember { mutableStateOf(false) }

    val performBack = { onBack() }

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
            // 1. Role & Storage Security Status Banner (Prominent at top)
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

            // 2. App Preferences Accordion (Language, Haptics, Google Account)
            AppPreferencesAccordion(
                currentLang = currentLang,
                onSelectLanguage = { viewModel.setLanguage(it) },
                hapticEnabled = hapticEnabled,
                onToggleHaptic = { viewModel.setHapticFeedbackEnabled(it) },
                user = state.user,
                isAuthConfigured = state.isAuthConfigured,
                busy = busy,
                onSignIn = { viewModel.signIn() },
                onSignOut = { viewModel.signOut() },
                isExpanded = prefsExpanded,
                onToggle = { prefsExpanded = !prefsExpanded }
            )

            // 3. Join Festival with Code Accordion
            JoinFestivalAccordion(
                isExpanded = joinExpanded,
                onToggle = { joinExpanded = !joinExpanded },
                currentLang = currentLang,
                counterName = counterName,
                onSaveCounter = { viewModel.setCounterName(it) },
                eventName = state.event?.name
            )

            val event = state.event
            if (event == null) {
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU) "దయచేసి ముందుగా ఒక ఈవెంట్‌ను ఎంచుకోండి లేదా సృష్టించండి." else "Select or create an event first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // 4. Temple Voice & Audio Accordion
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

            // 5. Advanced: Counter Identity
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
internal fun SettingsAccordionCard(
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

/** App Preferences Accordion — language selection, subtle haptic feedback, and Google account management in one clean place. */
@Composable
private fun AppPreferencesAccordion(
    currentLang: String,
    onSelectLanguage: (String) -> Unit,
    hapticEnabled: Boolean,
    onToggleHaptic: (Boolean) -> Unit,
    user: com.shankaravam.festival.data.remote.CloudUser?,
    isAuthConfigured: Boolean,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val haptics = LocalAppHaptics.current
    val langLabel = if (currentLang == SessionPrefs.LANG_TELUGU) "తెలుగు" else "English"
    val hapticLabel = if (hapticEnabled) (if (currentLang == SessionPrefs.LANG_TELUGU) "వైబ్రేషన్ ఆన్" else "Haptics On")
        else (if (currentLang == SessionPrefs.LANG_TELUGU) "వైబ్రేషన్ ఆఫ్" else "Haptics Off")
    val accountLabel = user?.email ?: (if (currentLang == SessionPrefs.LANG_TELUGU) "ఆఫ్‌లైన్" else "Offline")
    val summary = "$langLabel • $hapticLabel • $accountLabel"

    SettingsAccordionCard(
        title = if (currentLang == SessionPrefs.LANG_TELUGU) "యాప్ ప్రాధాన్యతలు" else "App Preferences",
        teluguTitle = if (currentLang == SessionPrefs.LANG_TELUGU) "" else "యాప్ ప్రాధాన్యతలు",
        summary = summary,
        icon = Icons.Filled.Tune,
        iconTint = TempleSaffron,
        iconBackground = SaffronWash,
        isExpanded = isExpanded,
        onToggle = onToggle,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. App Language Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = TempleSaffron,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "App Language / యాప్ భాష",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 2. Haptic Feedback Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable {
                        val next = !hapticEnabled
                        if (next) haptics.tick()
                        onToggleHaptic(next)
                    }
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
                        imageVector = Icons.Filled.Vibration,
                        contentDescription = null,
                        tint = TempleSaffron,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = strings.hapticFeedbackTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.hapticFeedbackSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = hapticEnabled,
                    onCheckedChange = {
                        if (it) haptics.tick()
                        onToggleHaptic(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TempleSaffron
                    )
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 3. Google Account Section (Flattened directly, no nested accordion)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = TempleSaffron,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU) "గూగుల్ ఖాతా (Google Account)" else "Google Account",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!isAuthConfigured) {
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

/** Join Festival with Code Accordion — allows collectors and members to join without creating duplicate events. */
@Composable
private fun JoinFestivalAccordion(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    currentLang: String,
    counterName: String,
    onSaveCounter: (String) -> Unit,
    eventName: String?,
    modifier: Modifier = Modifier,
    syncViewModel: CloudSyncViewModel = containerViewModel { CloudSyncViewModel(it) }
) {
    val syncState by syncViewModel.uiState.collectAsState()
    val busy by syncViewModel.busy.collectAsState()
    val notice by syncViewModel.notice.collectAsState()
    var joinCode by remember { mutableStateOf("") }
    var counterDraft by remember(counterName) { mutableStateOf(counterName) }
    var qrBusy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qrPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || qrBusy) return@rememberLauncherForActivityResult
        qrBusy = true
        scope.launch {
            val code = decodeJoinCodeFromUri(context.contentResolver, uri)
            if (code != null) {
                joinCode = code
                syncViewModel.info("QR read: $code — tap Request to join.")
            } else {
                syncViewModel.info("No invite QR found in that image — try a clearer screenshot.")
            }
            qrBusy = false
        }
    }

    val summary = if (eventName != null) {
        if (currentLang == SessionPrefs.LANG_TELUGU) "పండుగ: $eventName" else "Festival: $eventName"
    } else {
        if (currentLang == SessionPrefs.LANG_TELUGU) "కోడ్‌తో పండుగలో చేరండి" else "Join a festival with invite code"
    }

    SettingsAccordionCard(
        title = if (currentLang == SessionPrefs.LANG_TELUGU) "కోడ్‌తో పండుగలో చేరండి" else "Join Festival with Code",
        teluguTitle = if (currentLang == SessionPrefs.LANG_TELUGU) "" else "కోడ్‌తో పండుగలో చేరండి",
        summary = summary,
        icon = Icons.Filled.GroupAdd,
        iconTint = DeepMaroon,
        iconBackground = MaroonWash,
        isExpanded = isExpanded,
        onToggle = onToggle,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val user = syncState.user
            if (user == null) {
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU)
                        "పండుగలో చేరడానికి ముందుగా పైన ఉన్న యాప్ ప్రాధాన్యతలలో గూగుల్‌తో సైన్ ఇన్ చేయండి."
                    else
                        "Sign in with Google in App Preferences above to join a festival committee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                JoinCard(
                    code = joinCode,
                    onCode = { joinCode = it },
                    valid = parseJoinCode(joinCode) != null,
                    busy = busy == "join",
                    onJoin = {
                        onSaveCounter(counterDraft.trim())
                        syncViewModel.join(joinCode, user.uid) { joinCode = "" }
                    },
                    counter = counterDraft,
                    onCounter = { counterDraft = it },
                    onPickQr = { qrPickLauncher.launch("image/*") },
                    pickingQr = qrBusy
                )
            }

            notice?.let {
                Surface(
                    color = SaffronWash,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                syncViewModel.consumeNotice()
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
        // cloud needs a key (managed in Cloud Sync).
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
                text = "Cloud voice active — speaker & API key are managed in Cloud Sync. Device voice, speed and bell still apply as fallback.",
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
internal fun VoiceKeyContent(
    key: String,
    onKeyChange: (String) -> Unit,
    speaker: String,
    onSpeakerChange: (String) -> Unit,
    savedKey: String = "",
    busy: Boolean,
    testStatus: String?,
    testing: Boolean,
    hasKey: Boolean,
    hasGateway: Boolean = false,
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
        if (hasGateway) {
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Temple Media Gateway is active. The cloud Worker manages Sarvam AI voice synthesis without requiring a key on this phone.",
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

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
            text = if (hasGateway)
                "Direct Sarvam API Key (Optional local override — gateway handles cloud voice automatically):"
            else
                "Optional high-fidelity cloud Telugu voice for announcements. Leave empty to use free offline Android voice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text(if (hasGateway) "Direct Sarvam API Key (Override)" else "Sarvam API Key") },
            placeholder = { Text(if (hasGateway) "Managed by gateway" else "Empty = offline voice only") },
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
            // T0.5 lineup: Shubh default, Pooja secondary (single order in Voice.kt).
            SARVAM_SPEAKER_ORDER.forEach { option ->
                FilterChip(
                    selected = com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(speaker) == option,
                    onClick = { onSpeakerChange(option) },
                    label = { Text(sarvamPickerLabel(option), fontWeight = FontWeight.Medium) }
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
