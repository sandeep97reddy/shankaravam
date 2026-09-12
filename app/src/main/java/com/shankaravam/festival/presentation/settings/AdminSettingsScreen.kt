package com.shankaravam.festival.presentation.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
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
        val encrypted: Boolean = false,
        val cloudUser: String? = null,
        val cloudEmail: String? = null,
        val hasSarvamKey: Boolean = false
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
                        cloudUser = user?.uid,
                        cloudEmail = user?.email,
                        hasSarvamKey = container.secureKeys.getSarvamKey().isNotBlank()
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
                        encrypted = container.secureKeys.isEncrypted,
                        cloudUser = user?.uid,
                        cloudEmail = user?.email,
                        hasSarvamKey = container.secureKeys.getSarvamKey().isNotBlank()
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

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

    private val _selectedVoice = MutableStateFlow(container.sessionPrefs.nativeTtsVoice)
    val selectedVoice: StateFlow<String?> = _selectedVoice.asStateFlow()

    private val _speed = MutableStateFlow(container.sessionPrefs.nativeTtsSpeed)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _chime = MutableStateFlow(container.sessionPrefs.playTempleChime)
    val chimeEnabled: StateFlow<Boolean> = _chime.asStateFlow()

    fun setChimeEnabled(enabled: Boolean) {
        _chime.value = enabled
        container.sessionPrefs.playTempleChime = enabled
    }

    fun setNativeVoice(name: String?) {
        _selectedVoice.value = name
        container.sessionPrefs.nativeTtsVoice = name
        if (name != null) {
            container.ttsEngine.native.setVoiceByName(name)
        }
    }

    fun setSpeed(s: Float) {
        _speed.value = s
        container.sessionPrefs.nativeTtsSpeed = s
        container.ttsEngine.native.setSpeechRate(s)
    }

    fun testNativeSpeech() {
        container.ttsEngine.native.speak("శ్రీ రెడబోతు సందీప్ రెడ్డి గారు, వెయ్యి నూట పదహారు రూపాయలు.")
    }

    fun saveKeyLocally(key: String, speaker: String) {
        container.secureKeys.setSarvamKey(key)
        container.sessionPrefs.sarvamSpeaker = speaker
        keyTick.value += 1
        _notice.value = if (container.secureKeys.isEncrypted) {
            "Key stored encrypted on this device."
        } else {
            "Key stored locally (device has no keystore — moves to secure storage on supported devices)."
        }
    }

    fun pushKey(key: String, speaker: String) {
        val uid = container.authRepository.user.value?.uid
        if (uid == null) {
            _notice.value = "Sign in first (Cloud sync screen)."
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
            when (val result = container.syncService.readTtsKey()) {
                is Outcome.Ok -> {
                    container.secureKeys.setSarvamKey(result.value.first)
                    container.sessionPrefs.sarvamSpeaker = result.value.second
                    keyTick.value += 1
                    _notice.value = "Shared voice settings applied."
                }
                is Outcome.Err -> _notice.value = result.message
            }
            _busy.value = false
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
                    actorId = container.authRepository.user.value?.uid ?: "",
                    timestamp = now
                )
            )
            _notice.value = "“${event.name}” closed — records stay readable, new entries stop."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminSettingsViewModel = containerViewModel { AdminSettingsViewModel(it) }
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

    var showCloseConfirm by remember { mutableStateOf(false) }

    // Hoisted draft state for form fields so collapsing accordion does NOT discard input
    var counterDraft by remember(counterName) { mutableStateOf(counterName) }
    var sarvamKeyDraft by remember { mutableStateOf(viewModel.getSavedSarvamKey()) }
    var sarvamSpeakerDraft by remember { mutableStateOf(viewModel.getSavedSarvamSpeaker()) }

    // When a key is saved/pulled, update drafts accordingly
    LaunchedEffect(state.hasSarvamKey) {
        val savedKey = viewModel.getSavedSarvamKey()
        if (savedKey.isNotBlank() && sarvamKeyDraft.isBlank()) {
            sarvamKeyDraft = savedKey
        }
        sarvamSpeakerDraft = viewModel.getSavedSarvamSpeaker()
    }

    // Collapsible accordion states - minimal & clean for easy navigation
    var voiceExpanded by remember { mutableStateOf(false) }
    var counterExpanded by remember { mutableStateOf(false) }
    var cloudExpanded by remember { mutableStateOf(false) }
    var adminExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TempleAppBar(
                title = if (currentLang == SessionPrefs.LANG_TELUGU) "సెట్టింగ్స్ & ధ్వని" else "Settings & Voice",
                onBack = onBack
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

            // 2. Role & Storage Security Status Banner
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
            if (event == null) {
                Text(
                    text = if (currentLang == SessionPrefs.LANG_TELUGU) "దయచేసి ముందుగా ఒక ఈవెంట్‌ను ఎంచుకోండి లేదా సృష్టించండి." else "Select or create an event first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // 3. Accordion Section 1: Temple Voice & Audio
            val voiceDisplayName = selectedVoice?.substringAfterLast("-") ?: if (currentLang == SessionPrefs.LANG_TELUGU) "సిస్టమ్ డిఫాల్ట్" else "Default"
            val chimeStatus = if (chime) (if (currentLang == SessionPrefs.LANG_TELUGU) "గంట నాదం ఆన్" else "Bell On") else (if (currentLang == SessionPrefs.LANG_TELUGU) "గంట నాదం ఆఫ్" else "Bell Off")
            val voiceSummary = "$voiceDisplayName • ${String.format(java.util.Locale.US, "%.2fx", speed)} • $chimeStatus"

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
                    selectedVoice = selectedVoice,
                    speed = speed,
                    chimeEnabled = chime,
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
            val cloudSummary = if (state.hasSarvamKey) {
                if (currentLang == SessionPrefs.LANG_TELUGU) "శర్వం క్లౌడ్ గొంతు సిద్ధంగా ఉంది" else "Sarvam Cloud Voice Configured"
            } else {
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
                VoiceKeyContent(
                    key = sarvamKeyDraft,
                    onKeyChange = { sarvamKeyDraft = it },
                    speaker = sarvamSpeakerDraft,
                    onSpeakerChange = { sarvamSpeakerDraft = it },
                    busy = busy,
                    canPublish = AccessPolicy.canManageKeys(state.role)
                        && AdminConfig.isGlobalHeadEmail(state.cloudEmail),
                    onSaveLocal = { key, speaker -> viewModel.saveKeyLocally(key, speaker) },
                    onPush = { key, speaker -> viewModel.pushKey(key, speaker) },
                    onPull = { viewModel.pullKey() }
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
                    iconTint = DeepMaroon,
                    iconBackground = MaroonWash,
                    isExpanded = adminExpanded,
                    onToggle = { adminExpanded = !adminExpanded }
                ) {
                    FestivalAdminContent(
                        event = event,
                        onRequestClose = { showCloseConfirm = true }
                    )
                }
            }
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
                        color = DeepMaroon,
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
                            color = DeepMaroon // DeepMaroon on white has 13.5:1 contrast, satisfying WCAG AAA!
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

/** Inbuilt Android TTS content with voice list, speech speed, bell toggle, and audio preview. */
@Composable
private fun NativeVoiceContent(
    voices: List<String>,
    selectedVoice: String?,
    speed: Float,
    chimeEnabled: Boolean,
    onSelectVoice: (String?) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetChime: (Boolean) -> Unit,
    onTestVoice: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Offline Telugu voice built into Android. Select from voices installed on your device:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (voices.isNotEmpty()) {
            Text(
                text = "Installed Telugu Voices:",
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
                voices.forEach { v ->
                    val label = v.substringAfterLast("-", v.takeLast(10))
                    FilterChip(
                        selected = selectedVoice == v,
                        onClick = { onSelectVoice(v) },
                        label = { Text("Voice ($label)") }
                    )
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
            Text("Test Voice: శ్రీ రెడబోతు సందీప్ రెడ్డి గారు", fontWeight = FontWeight.Bold)
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
    busy: Boolean,
    canPublish: Boolean,
    onSaveLocal: (String, String) -> Unit,
    onPush: (String, String) -> Unit,
    onPull: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Text(
            text = "Voice Speaker / గొంతు ఎంపిక:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("meera" to "Meera (Female)", "arvind" to "Arvind (Male)").forEach { (option, label) ->
                FilterChip(
                    selected = speaker == option,
                    onClick = { onSpeakerChange(option) },
                    label = { Text(label, fontWeight = FontWeight.Medium) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onSaveLocal(key, speaker) },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Save Local", maxLines = 1) }
            OutlinedButton(
                onClick = { onPull() },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Pull Shared", maxLines = 1) }
        }
        Button(
            onClick = { onPush(key, speaker) },
            enabled = canPublish && key.isNotBlank() && !busy,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TempleSaffron,
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

/** Festival Administration content for closing the active festival ledger. */
@Composable
private fun FestivalAdminContent(
    event: Event,
    onRequestClose: () -> Unit
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
                containerColor = DeepMaroon,
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
    }
}
