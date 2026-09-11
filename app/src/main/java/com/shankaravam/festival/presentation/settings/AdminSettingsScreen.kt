package com.shankaravam.festival.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Admin-owned VM (folded): secure voice key, role label, event closure. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminSettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val event: Event? = null,
        val role: UserRole = UserRole.ORGANIZER,
        val encrypted: Boolean = false,
        val cloudUser: String? = null
    )

    val uiState: StateFlow<UiState> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(UiState(encrypted = container.secureKeys.isEncrypted))
            } else {
                combine(
                    container.eventRepository.observeEvent(eventId),
                    container.authRepository.user
                ) { event: Event?, user: com.shankaravam.festival.data.remote.CloudUser? ->
                    UiState(
                        event = event,
                        role = roleOf(container.sessionPrefs.myRole(eventId)),
                        encrypted = container.secureKeys.isEncrypted,
                        cloudUser = user?.uid
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun consumeNotice() { _notice.value = null }

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
    val notice by viewModel.notice.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var showCloseConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Voice & admin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DeepMaroon,
                    titleContentColor = TempleGold,
                    navigationIconContentColor = TempleGold
                )
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
            val event = state.event
            if (event == null) {
                Text("Select or create an event first.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your role: ${state.role.name.lowercase().replace('_', ' ')}", fontWeight = FontWeight.Bold)
                    Text(
                        if (state.encrypted) "Voice key storage: encrypted ✓" else "Voice key storage: plain (no keystore)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            VoiceKeyCard(
                busy = busy,
                canManageKeys = AccessPolicy.canManageKeys(state.role),
                onSaveLocal = { key, speaker -> viewModel.saveKeyLocally(key, speaker) },
                onPush = { key, speaker -> viewModel.pushKey(key, speaker) },
                onPull = { viewModel.pullKey() }
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
    canManageKeys: Boolean,
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
                enabled = canManageKeys && key.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (canManageKeys) "Publish for collectors (head only)" else "Publish: head only")
            }
            Text(
                "Publishing writes /config/tts_settings (head-write rule). Audio itself never leaves devices.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
