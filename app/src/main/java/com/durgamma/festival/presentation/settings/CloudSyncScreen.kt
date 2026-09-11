package com.durgamma.festival.presentation.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durgamma.festival.core.theme.DeepMaroon
import com.durgamma.festival.core.theme.TempleGold
import com.durgamma.festival.core.util.Outcome
import com.durgamma.festival.core.util.generateShareCode
import com.durgamma.festival.core.util.isValidShareCode
import com.durgamma.festival.data.remote.CloudMember
import com.durgamma.festival.data.remote.CloudUser
import com.durgamma.festival.data.work.SyncWorker
import com.durgamma.festival.di.AppContainer
import com.durgamma.festival.domain.model.AccessPolicy
import com.durgamma.festival.domain.model.Event
import com.durgamma.festival.domain.model.roleOf
import com.durgamma.festival.presentation.common.containerViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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

/** Settings-owned VM (folded): auth + sync toggle + invite codes + approvals. */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val event: Event? = null,
        val user: CloudUser? = null,
        val configured: Boolean = false,
        val syncEnabled: Boolean = false,
        val myRole: String = "organizer",
        val myCode: String? = null
    )

    val uiState: StateFlow<UiState> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(UiState(user = container.authRepository.user.value))
            } else {
                combine(
                    container.eventRepository.observeEvent(eventId),
                    container.authRepository.user
                ) { event: Event?, user: CloudUser? ->
                    UiState(
                        event = event,
                        user = user,
                        configured = container.authRepository.isConfigured,
                        syncEnabled = container.sessionPrefs.cloudSyncEnabled,
                        myRole = container.sessionPrefs.myRole(eventId),
                        myCode = container.sessionPrefs.shareCodeFor(eventId)
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _pending = MutableStateFlow<List<CloudMember>>(emptyList())
    val pending: StateFlow<List<CloudMember>> = _pending.asStateFlow()

    private val _signInIntent = MutableStateFlow<android.content.Intent?>(null)
    val signInIntent: StateFlow<android.content.Intent?> = _signInIntent.asStateFlow()

    fun consumeNotice() { _notice.value = null }

    fun signIn() {
        when (val result = container.authRepository.googleSignInIntent()) {
            is Outcome.Ok -> _signInIntent.value = result.value
            is Outcome.Err -> _notice.value = result.message
        }
    }

    fun consumeSignInIntent() { _signInIntent.value = null }

    fun completeSignIn(data: android.content.Intent?) {
        viewModelScope.launch {
            _busy.value = "signin"
            when (
                val result = container.authRepository.handleSignInResult(data)
            ) {
                is Outcome.Ok -> _notice.value = "Signed in as ${result.value.displayName ?: result.value.email}."
                is Outcome.Err -> _notice.value = result.message
            }
            _busy.value = null
        }
    }

    fun signOut() {
        container.authRepository.signOut()
    }

    fun setSyncEnabled(enabled: Boolean, eventId: String?) {
        container.sessionPrefs.cloudSyncEnabled = enabled
        if (enabled) {
            SyncWorker.schedulePeriodic(container.appContext)
            if (eventId != null) SyncWorker.syncNow(container.appContext, eventId)
            _notice.value = "Cloud sync on — uploads only deltas, in the background."
        } else {
            SyncWorker.cancelAll(container.appContext)
            _notice.value = "Cloud sync off — the app keeps working fully offline."
        }
    }

    fun syncNow(eventId: String) {
        SyncWorker.syncNow(container.appContext, eventId)
        _notice.value = "Sync queued — runs when the network is up."
    }

    fun publishCode(event: Event, uid: String) {
        viewModelScope.launch {
            _busy.value = "code"
            val code = container.sessionPrefs.shareCodeFor(event.id) ?: generateShareCode()
            // First publisher becomes global head on this device.
            if (container.sessionPrefs.myRole(event.id) == "organizer") {
                container.sessionPrefs.setMyRole(event.id, "global_head")
            }
            when (
                val result = container.syncService.publishShareCode(event.id, code, event.name, uid)
            ) {
                is Outcome.Ok -> _notice.value = "Invite live: $code"
                is Outcome.Err -> {
                    // Offline: keep the code locally; it publishes on next sync.
                    container.sessionPrefs.putShareCode(event.id, code)
                    container.sessionPrefs.putShareCodeReverse(code, event.id)
                    _notice.value = "Saved offline — invite publishes on next sync. (${result.message})"
                }
            }
            _busy.value = null
        }
    }

    fun join(code: String, uid: String, onJoined: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = "join"
            when (val result = container.syncService.requestToJoin(code, uid)) {
                is Outcome.Ok -> {
                    container.sessionPrefs.setMyRole(result.value, "member")
                    _notice.value = "Request sent — the organizer approves you as collector."
                    onJoined(result.value)
                }
                is Outcome.Err -> _notice.value = result.message
            }
            _busy.value = null
        }
    }

    fun refreshPending(eventId: String) {
        viewModelScope.launch {
            _busy.value = "pending"
            when (val result = container.syncService.pendingMembers(eventId)) {
                is Outcome.Ok -> _pending.value = result.value
                is Outcome.Err -> _notice.value = result.message
            }
            _busy.value = null
        }
    }

    fun approve(eventId: String, member: CloudMember, asRole: String, approvedBy: String) {
        viewModelScope.launch {
            _busy.value = "approve:${member.userId}"
            when (
                val result = container.syncService.setMember(
                    eventId, member.userId, asRole, "active", approvedBy
                )
            ) {
                is Outcome.Ok -> {
                    _pending.value = _pending.value.filterNot { it.userId == member.userId }
                    _notice.value = "Approved as $asRole."
                }
                is Outcome.Err -> _notice.value = result.message
            }
            _busy.value = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloudSyncViewModel = containerViewModel { CloudSyncViewModel(it) }
) {
    val state by viewModel.uiState.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val signInIntent by viewModel.signInIntent.collectAsState()
    var joinCode by remember { mutableStateOf("") }

    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.completeSignIn(result.data) }
    androidx.compose.runtime.LaunchedEffect(signInIntent) {
        signInIntent?.let {
            signInLauncher.launch(it)
            viewModel.consumeSignInIntent()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cloud sync") },
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
            if (!state.configured) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cloud not configured", fontWeight = FontWeight.Bold)
                        Text(
                            "This build has no google-services.json, so everything below " +
                                "stays disabled and the app runs 100% offline. To go live: " +
                                "create a Firebase (Spark) project, drop in google-services.json, " +
                                "deploy firestore.rules from the repo root — no code changes needed.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val user = state.user
                    if (user == null) {
                        Text("Not signed in — offline mode.", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { viewModel.signIn() },
                            enabled = state.configured && busy == null
                        ) {
                            Text(if (busy == "signin") "Signing in…" else "Sign in with Google")
                        }
                    } else {
                        Text(user.displayName ?: user.email ?: user.uid)
                        OutlinedButton(onClick = { viewModel.signOut() }) { Text("Sign out") }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Multi-counter sync", fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = state.syncEnabled,
                            enabled = state.configured && state.user != null && state.event != null,
                            onCheckedChange = { viewModel.setSyncEnabled(it, state.event?.id) }
                        )
                    }
                    Text(
                        "Delta-only via WorkManager (15 min + on-demand). Room stays the screen source of truth.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { state.event?.let { viewModel.syncNow(it.id) } },
                        enabled = state.syncEnabled && state.event != null && busy == null
                    ) { Text("Sync now") }
                }
            }
            val event = state.event
            val user = state.user
            if (event != null && user != null) {
                InviteCard(
                    eventName = event.name,
                    code = state.myCode,
                    busy = busy == "code",
                    onPublish = { viewModel.publishCode(event, user.uid) }
                )
                JoinCard(
                    code = joinCode,
                    onCode = { joinCode = it.uppercase().filter(Char::isLetterOrDigit) },
                    valid = isValidShareCode(joinCode),
                    busy = busy == "join",
                    onJoin = { viewModel.join(joinCode, user.uid) {} }
                )
                if (AccessPolicy.canApproveMembers(roleOf(state.myRole))) {
                    ApprovalsCard(
                        pending = pending,
                        busyKey = busy,
                        onRefresh = { viewModel.refreshPending(event.id) },
                        onApprove = { member, role ->
                            viewModel.approve(event.id, member, role, user.uid)
                        }
                    )
                }
                OutlinedButton(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
                    Text("Voice & admin settings")
                }
            }
            notice?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(14.dp))
                }
                viewModel.consumeNotice()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InviteCard(eventName: String, code: String?, busy: Boolean, onPublish: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Invite collectors to “$eventName”", fontWeight = FontWeight.SemiBold)
            if (code == null) {
                Button(onClick = onPublish, enabled = !busy) {
                    Text(if (busy) "Publishing…" else "Create invite code")
                }
            } else {
                val bitmap = remember(code) { qrBitmap("durgamma://join/$code") }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Invite QR for code $code",
                    modifier = Modifier.size(180.dp)
                )
                Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Collectors type this code — or scan — in Cloud sync.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun JoinCard(code: String, onCode: (String) -> Unit, valid: Boolean, busy: Boolean, onJoin: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Join with a code", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = code,
                onValueChange = onCode,
                label = { Text("6-letter code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onJoin, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Sending…" else "Request to join")
            }
        }
    }
}

@Composable
private fun ApprovalsCard(
    pending: List<CloudMember>,
    busyKey: String?,
    onRefresh: () -> Unit,
    onApprove: (CloudMember, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Access requests (${pending.size})", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            }
            pending.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(member.userId.take(12) + "…", modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { onApprove(member, "organizer") },
                        enabled = busyKey != "approve:${member.userId}"
                    ) { Text("Collector") }
                    OutlinedButton(
                        onClick = { onApprove(member, "member") },
                        enabled = busyKey != "approve:${member.userId}"
                    ) { Text("Viewer") }
                }
            }
            if (pending.isEmpty()) {
                Text("No pending requests.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun qrBitmap(content: String, sizePx: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return bitmap
}
