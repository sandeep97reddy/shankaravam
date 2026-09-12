package com.shankaravam.festival.presentation.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.newRecordId
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.AccessPolicy
import com.shankaravam.festival.domain.model.ActivityActions
import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.AdminConfig
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.model.UserRole
import com.shankaravam.festival.domain.model.roleOf
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

/** Admin-owned VM (folded): secure voice key, role label, event closure. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminSettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val event: Event? = null,
        val role: UserRole = UserRole.ORGANIZER,
        val encrypted: Boolean = false,
        val cloudUser: String? = null,
        val cloudEmail: String? = null
    )

    val uiState: StateFlow<UiState> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                val user = container.authRepository.user.value
                flowOf(
                    UiState(
                        encrypted = container.secureKeys.isEncrypted,
                        cloudUser = user?.uid,
                        cloudEmail = user?.email
                    )
                )
            } else {
                combine(
                    container.eventRepository.observeEvent(eventId),
                    container.authRepository.user
                ) { event: Event?, user: com.shankaravam.festival.data.remote.CloudUser? ->
                    UiState(
                        event = event,
                        role = roleOf(container.sessionPrefs.myRole(eventId)),
                        encrypted = container.secureKeys.isEncrypted,
                        cloudUser = user?.uid,
                        cloudEmail = user?.email
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

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
                is Outcome.Ok -> _notice.value = "Shared key published for collectors."
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
    var showCloseConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            com.shankaravam.festival.presentation.common.TempleAppBar(
                title = "Settings & Voice",
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Language Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App Language / యాప్ భాష", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = currentLang == com.shankaravam.festival.data.local.SessionPrefs.LANG_ENGLISH,
                            onClick = { viewModel.setLanguage(com.shankaravam.festival.data.local.SessionPrefs.LANG_ENGLISH) },
                            label = { Text("English", fontWeight = FontWeight.SemiBold) }
                        )
                        FilterChip(
                            selected = currentLang == com.shankaravam.festival.data.local.SessionPrefs.LANG_TELUGU,
                            onClick = { viewModel.setLanguage(com.shankaravam.festival.data.local.SessionPrefs.LANG_TELUGU) },
                            label = { Text("తెలుగు (Telugu)", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            // Counter / collector identity Card (one-time setup, stamped offline)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                var draft by remember(counterName) { mutableStateOf(counterName) }
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Counter name / కౌంటర్ పేరు", fontWeight = FontWeight.Bold)
                    Text(
                        "Stamped on every donation & expense on this device (works offline). Shown as Collector in History and after sync.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("e.g. Counter 2 - Ramesh") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { viewModel.setCounterName(draft) },
                        enabled = draft.trim().isNotBlank() && draft.trim() != counterName
                    ) { Text("Save counter name") }
                }
            }

            val event = state.event
            if (event == null) {
                Text("Select or create an event first.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // S4.4: whitelisted head gets the Verified line; everyone
                    // else keeps the plain role label (Rule #1: no login push).
                    val isHeadAdmin = state.role == UserRole.GLOBAL_HEAD
                        && AdminConfig.isGlobalHeadEmail(state.cloudEmail)
                    Text(
                        if (isHeadAdmin) "👑 Global Head (Admin: ${AdminConfig.GLOBAL_HEAD_EMAIL}) • Verified"
                        else "Your role: ${state.role.name.lowercase().replace('_', ' ')}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (state.encrypted) "Voice key storage: encrypted ✓" else "Voice key storage: plain (no keystore)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            VoiceKeyCard(
                busy = busy,
                // S1 model (a): /config/tts_settings is master-admin-only, so
                // Publish unlocks solely for the whitelisted head. Pull/save
                // stay available to all (read is signed-in, keystore is local).
                canPublish = AccessPolicy.canManageKeys(state.role)
                    && AdminConfig.isGlobalHeadEmail(state.cloudEmail),
                onSaveLocal = { key, speaker -> viewModel.saveKeyLocally(key, speaker) },
                onPush = { key, speaker -> viewModel.pushKey(key, speaker) },
                onPull = { viewModel.pullKey() }
            )

            val selectedVoice by viewModel.selectedVoice.collectAsState()
            val speed by viewModel.speed.collectAsState()
            val chime by viewModel.chimeEnabled.collectAsState()
            val teluguVoices by viewModel.nativeVoices.collectAsState()
            NativeVoiceCard(
                voices = teluguVoices,
                selectedVoice = selectedVoice,
                speed = speed,
                onSelectVoice = { viewModel.setNativeVoice(it) },
                onSetSpeed = { viewModel.setSpeed(it) },
                onTestVoice = { viewModel.testNativeSpeech() },
                chimeEnabled = chime,
                onSetChime = { viewModel.setChimeEnabled(it) }
            )

            if (AccessPolicy.canCloseEvent(state.role)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Close event", fontWeight = FontWeight.SemiBold)
                        Text(
                            "“${event.name}” becomes read-only history. Needs a signed-in approver on synced events.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = { showCloseConfirm = true },
                            enabled = event.status.name == "ACTIVE"
                        ) { Text(if (event.status.name == "ACTIVE") "Close “${event.name}”" else "Already closed") }
                    }
                }
            }
            notice?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(14.dp))
                }
                viewModel.consumeNotice()
            }
        }
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Close this event?") },
            text = { Text("Totals, history and reports stay. New donations and expenses stop.") },
            confirmButton = {
                TextButton(onClick = {
                    state.event?.let { viewModel.closeEvent(it) }
                    showCloseConfirm = false
                }) { Text("Close event") }
            },
            dismissButton = { TextButton(onClick = { showCloseConfirm = false }) { Text("Keep open") } }
        )
    }
}

@Composable
private fun VoiceKeyCard(
    busy: Boolean,
    canPublish: Boolean,
    onSaveLocal: (String, String) -> Unit,
    onPush: (String, String) -> Unit,
    onPull: () -> Unit
) {
    var key by remember { mutableStateOf("") }
    var speaker by remember { mutableStateOf("meera") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cloud voice (Sarvam)", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Sarvam API key") },
                placeholder = { Text("Empty = offline voice only") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("meera", "arvind").forEach { option ->
                    FilterChip(
                        selected = speaker == option,
                        onClick = { speaker = option },
                        label = { Text(option.replaceFirstChar { it.titlecase() }) }
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
                    modifier = Modifier.weight(1f)
                ) { Text("Save on device") }
                OutlinedButton(
                    onClick = { onPull() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Pull shared") }
            }
            Button(
                onClick = { onPush(key, speaker) },
                enabled = canPublish && key.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (canPublish) "Publish for collectors (head only)" else "Publish: head only")
            }
            Text(
                "Publishing writes /config/tts_settings (head-write rule). Audio itself never leaves devices.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun NativeVoiceCard(
    voices: List<String>,
    selectedVoice: String?,
    speed: Float,
    onSelectVoice: (String?) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onTestVoice: () -> Unit,
    chimeEnabled: Boolean,
    onSetChime: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Inbuilt Phone Voice (Android TTS)", fontWeight = FontWeight.SemiBold)
            Text(
                "Offline Telugu voice built into Android. Select from voices installed on your device:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (voices.isNotEmpty()) {
                Text("Installed Telugu Voices:", style = MaterialTheme.typography.labelSmall)
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
                    "Default Telugu engine active. (To download additional voices, open Android Settings → Accessibility → Text-to-speech output → Google TTS engine).",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("Speech Speed / వేగం: ${String.format(java.util.Locale.US, "%.2fx", speed)}", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85f, 1.0f, 1.15f).forEach { s ->
                    FilterChip(
                        selected = kotlin.math.abs(speed - s) < 0.05f,
                        onClick = { onSetSpeed(s) },
                        label = { Text("${s}x") }
                    )
                }
            }

            OutlinedButton(
                onClick = onTestVoice,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Test Voice: శ్రీ రెడబోతు సందీప్ రెడ్డి గారు")
            }

            FilterChip(
                selected = chimeEnabled,
                onClick = { onSetChime(!chimeEnabled) },
                label = { Text("Temple bell before announcements / గంట నాదం") }
            )
        }
    }
}

