package com.shankaravam.festival.presentation.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.generateShareCode
import com.shankaravam.festival.core.util.isValidShareCode
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.data.remote.CloudMember
import com.shankaravam.festival.data.remote.CloudUser
import com.shankaravam.festival.data.work.SyncWorker
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.AccessPolicy
import com.shankaravam.festival.domain.model.AdminConfig
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.model.MemberPresence
import com.shankaravam.festival.domain.model.UserRole
import com.shankaravam.festival.domain.model.presenceOf
import com.shankaravam.festival.domain.model.resolveMemberName
import com.shankaravam.festival.domain.model.roleOf
import com.shankaravam.festival.presentation.common.containerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        val myCode: String? = null,
        /** Last successful ledger sync for the current event (0 = never). F1 diagnostics. */
        val lastSync: Long = 0L
    )

    /**
     * P0 fix: the invite code lives in SharedPreferences (not a Flow), so
     * publish/close bumps this tick to force [uiState] to re-read it.
     * Without this the InviteCard shows a stale code until remount.
     */
    private val codeTick = MutableStateFlow(0)

    val uiState: StateFlow<UiState> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                combine(
                    container.authRepository.user,
                    container.sessionPrefs.cloudSyncEnabledFlow,
                    codeTick
                ) { user: CloudUser?, syncEnabled: Boolean, _ ->
                    UiState(
                        event = null,
                        user = user,
                        configured = container.authRepository.isConfigured,
                        syncEnabled = syncEnabled,
                        myRole = "organizer",
                        myCode = null,
                        lastSync = 0L
                    )
                }
            } else {
                combine(
                    container.eventRepository.observeEvent(eventId),
                    container.authRepository.user,
                    container.sessionPrefs.cloudSyncEnabledFlow,
                    codeTick
                ) { event: Event?, user: CloudUser?, syncEnabled: Boolean, _ ->
                    UiState(
                        event = event,
                        user = user,
                        configured = container.authRepository.isConfigured,
                        syncEnabled = syncEnabled,
                        myRole = container.sessionPrefs.myRole(eventId),
                        myCode = container.sessionPrefs.shareCodeFor(eventId),
                        lastSync = container.sessionPrefs.lastSyncMillis(eventId)
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

    /** F2: surface an info line from composable-side flows (gallery QR results). */
    fun info(message: String) { _notice.value = message }

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
                is Outcome.Ok -> {
                    _notice.value = "Signed in as ${result.value.displayName ?: result.value.email}."
                    // F5: fresh login picks up the shared voice immediately.
                    runCatching { container.syncService.maybeAutoPullVoice() }
                }
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
            ensurePeriodic()
            if (eventId != null) SyncWorker.syncNow(container.appContext, eventId)
            _notice.value = "Cloud sync on — uploads only deltas, in the background."
        } else {
            SyncWorker.cancelAll(container.appContext)
            _notice.value = "Cloud sync off — the app keeps working fully offline."
        }
    }

    /** F1: idempotent re-assert of the 15-min schedule (KEEP — heals task-clear loss). */
    fun ensurePeriodic() {
        runCatching { SyncWorker.schedulePeriodic(container.appContext) }
    }

    fun syncNow(eventId: String) {
        viewModelScope.launch {
            _busy.value = "sync"
            _notice.value = "Syncing with cloud…"
            when (val outcome = container.syncService.syncEvent(eventId)) {
                is com.shankaravam.festival.data.remote.SyncOutcome.Done -> {
                    val r = outcome.result
                    _notice.value = "✓ Synced: ${r.uploaded} uploaded, ${r.downloaded} downloaded."
                }
                // F3: Blocked carries the human copy (pending/revoked/sign-in).
                is com.shankaravam.festival.data.remote.SyncOutcome.Blocked -> {
                    _notice.value = outcome.message
                }
                is com.shankaravam.festival.data.remote.SyncOutcome.Failed -> {
                    _notice.value = "Sync: ${outcome.message}"
                }
            }
            _busy.value = null
        }
    }

    fun publishCode(event: Event, uid: String) {
        viewModelScope.launch {
            _busy.value = "code"
            val code = container.sessionPrefs.shareCodeFor(event.id) ?: generateShareCode()
            // First publisher becomes global head on this device.
            if (container.sessionPrefs.myRole(event.id) == SessionPrefs.ROLE_ORGANIZER) {
                container.sessionPrefs.setMyRole(event.id, SessionPrefs.ROLE_GLOBAL_HEAD)
            }
            val me = container.authRepository.user.value
            when (
                val result = container.syncService.publishShareCode(
                    event.id, code, event.name, uid,
                    email = me?.email, displayName = me?.displayName
                )
            ) {
                is Outcome.Ok -> _notice.value = "Invite live: $code"
                is Outcome.Err -> {
                    // Offline: keep the code locally; it publishes on next sync.
                    container.sessionPrefs.putShareCode(event.id, code)
                    container.sessionPrefs.putShareCodeReverse(code, event.id)
                    _notice.value = "Saved offline — invite publishes on next sync. (${result.message})"
                }
            }
            codeTick.value += 1
            _busy.value = null
        }
    }

    /**
     * Close the live invite code (feature #2, head-only): flips the cloud doc
     * to closed and forgets the local mapping so the next publish mints a
     * fresh code. Republishing re-opens by design (fresh 10-day window).
     */
    fun closeCode(eventId: String, code: String) {
        if (!isHeadNow(eventId)) return
        viewModelScope.launch {
            _busy.value = "closecode"
            when (val result = container.syncService.closeShareCode(code)) {
                is Outcome.Ok -> {
                    container.sessionPrefs.clearShareCode(eventId)
                    _notice.value = "Invite closed — counters can no longer join with it."
                }
                is Outcome.Err -> _notice.value = result.message
            }
            codeTick.value += 1
            _busy.value = null
        }
    }

    fun join(code: String, uid: String, onJoined: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = "join"
            // Service owns role+status (incl. whitelisted-admin elevation) —
            // the VM must NOT blindly overwrite with MEMBER (S3.1).
            // parseJoinCode accepts typed codes, QR payloads and shared URLs (F1).
            val normalized = com.shankaravam.festival.core.util.parseJoinCode(code)
            if (normalized == null) {
                _notice.value = "That doesn't look like an invite code — check the 6-letter code or QR."
                _busy.value = null
                return@launch
            }
            val me = container.authRepository.user.value
            when (val result = container.syncService.requestToJoin(normalized, uid, me?.email, me?.displayName)) {
                is Outcome.Ok -> {
                    // F1: joining IS opting into sync — enable it so the new
                    // festival actually arrives without hunting for the toggle.
                    container.sessionPrefs.cloudSyncEnabled = true
                    SyncWorker.schedulePeriodic(container.appContext)
                    SyncWorker.syncNow(container.appContext, result.value)
                    // F1: silently drop empty local dummies (zero rows, never
                    // synced) left over from the old create-first-to-join flow.
                    val cleaned = cleanupEmptyDummies(keepId = result.value)
                    _notice.value = if (container.sessionPrefs.myStatus(result.value) == SessionPrefs.STATUS_ACTIVE
                        && container.sessionPrefs.myRole(result.value) == SessionPrefs.ROLE_GLOBAL_HEAD
                    ) {
                        "Welcome back, Head — the team directory is unlocked."
                    } else if (cleaned > 0) {
                        "Joined! Request sent — the organizer approves you as collector."
                    } else {
                        "Request sent — the organizer approves you as collector."
                    }
                    onJoined(result.value)
                }
                is Outcome.Err -> _notice.value = result.message
            }
            _busy.value = null
        }
    }

    /**
     * F1 one-way cleanup: deletes local-only, zero-record events (the dummies
     * the old flow forced collectors to create). Never touches cloud events,
     * events with rows, or the just-joined festival. Best-effort, never throws.
     * Returns the number removed (for the join notice).
     */
    private suspend fun cleanupEmptyDummies(keepId: String): Int = runCatching {
        var removed = 0
        val events = container.eventRepository.observeEvents().first()
        for (event in events) {
            if (event.id == keepId) continue
            if (container.sessionPrefs.isCloudEvent(event.id)) continue
            val counts = container.deleteLocalEvent.getCounts(event.id)
            if (counts.first == 0 && counts.second == 0) {
                if (container.deleteLocalEvent(event.id) is Outcome.Ok) removed++
            }
        }
        removed
    }.getOrDefault(0)

    fun refreshPending(eventId: String) {
        viewModelScope.launch {
            _busy.value = "pending"
            // Single roster query (S3.3) + client-side partition — no composite
            // index needed. Full-team view lands in S4 on the same call.
            when (val result = container.syncService.fetchAllMembers(eventId)) {
                is Outcome.Ok -> _pending.value = result.value.filter { it.status == "pending" }
                is Outcome.Err -> _notice.value = result.message
            }
            _busy.value = null
        }
    }

    fun approve(eventId: String, member: CloudMember, asRole: String, approvedBy: String) {
        viewModelScope.launch {
            _busy.value = "approve:${member.userId}"
            when (
                val result = container.syncService.setMemberRole(
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

    // ---- Connected Team (S4, head-only) ----

    private val _teamMembers = MutableStateFlow<List<CloudMember>>(emptyList())
    val teamMembers: StateFlow<List<CloudMember>> = _teamMembers.asStateFlow()

    /**
     * Head-now check: cached active/global_head on THIS event. The whitelisted
     * admin override is already folded into myRole() (cloud-scoped, S2.3), so
     * no raw-email check here — the member doc is the truth. Volunteers,
     * viewers and pending/revoked never pass, so the roster is never fetched
     * nor shown for them (Rule #1).
     */
    fun isHeadNow(eventId: String): Boolean =
        roleOf(container.sessionPrefs.myRole(eventId)) == UserRole.GLOBAL_HEAD
            && container.sessionPrefs.myStatus(eventId) == SessionPrefs.STATUS_ACTIVE

    /**
     * Roster visibility (verdict Q3): any ACTIVE signed-in member may view the
     * team directory (firestore.rules already permits it) so collectors see
     * which counter peers are online. Role management stays head-only via
     * [isHeadNow]; pending/revoked/signed-out never pass.
     */
    fun canViewRoster(eventId: String): Boolean =
        container.authRepository.user.value != null &&
            container.sessionPrefs.myStatus(eventId) == SessionPrefs.STATUS_ACTIVE

    fun refreshMembers(eventId: String) {
        if (!canViewRoster(eventId)) return
        viewModelScope.launch {
            _busy.value = "team"
            when (val result = container.syncService.fetchAllMembers(eventId)) {
                is Outcome.Ok -> {
                    _teamMembers.value = result.value
                    _pending.value = result.value.filter { it.status == "pending" }
                    // Own-row enforcement (S3.5 VM side): the roster is truth.
                    val me = container.authRepository.user.value?.uid
                    result.value.firstOrNull { it.userId == me }?.let {
                        container.sessionPrefs.setMyRole(eventId, it.role)
                        container.sessionPrefs.setMyStatus(eventId, it.status)
                    }
                }
                is Outcome.Err -> {
                    _notice.value =
                        if ("permission" in result.message.lowercase()) "Only the head can view the team."
                        else result.message
                }
            }
            _busy.value = null
        }
    }

    fun setMemberRole(eventId: String, member: CloudMember, role: String, status: String) {
        if (!isHeadNow(eventId)) return
        viewModelScope.launch {
            _busy.value = "role:${member.userId}"
            val me = container.authRepository.user.value?.uid ?: ""
            when (
                val result = container.syncService.setMemberRole(
                    eventId, member.userId, role, status, me
                )
            ) {
                is Outcome.Ok -> {
                    val cleanRole = role.trim().lowercase()
                    val cleanStatus = status.trim().lowercase()
                    _teamMembers.value = _teamMembers.value.map {
                        if (it.userId == member.userId) it.copy(role = cleanRole, status = cleanStatus)
                        else it
                    }
                    _pending.value = _teamMembers.value.filter { it.status == "pending" }
                    _notice.value = when {
                        cleanStatus == SessionPrefs.STATUS_REVOKED -> "Access revoked."
                        cleanRole == SessionPrefs.ROLE_ORGANIZER -> "Collector access granted."
                        else -> "Viewer access granted."
                    }
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
    val team by viewModel.teamMembers.collectAsState()
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
    // Roster auto-load (S4.2 + verdict Q3): keys on gate + event, so no fetch
    // loop and zero reads for signed-out/pending/revoked (Rule #1).
    val headEventId = state.event?.id
    val rosterNow = headEventId != null && state.user != null && viewModel.canViewRoster(headEventId)
    androidx.compose.runtime.LaunchedEffect(rosterNow, headEventId) {
        if (rosterNow) viewModel.refreshMembers(headEventId!!)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            com.shankaravam.festival.presentation.common.TempleAppBar(
                title = "Cloud Sync",
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
                        if (AdminConfig.isGlobalHeadEmail(user.email)) {
                            val ev = state.event
                            val verified = ev != null && viewModel.isHeadNow(ev.id)
                            Text(
                                "👑 Global Head Admin • ${user.email ?: ""}",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (verified) "Verified on “${ev.name}”."
                                else "Head powers unlock on your festivals after sync.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(user.displayName ?: user.email ?: "Signed in")
                        }
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
                        enabled = state.syncEnabled && state.event != null && busy != "sync",
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy == "sync") {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Syncing with cloud…")
                        } else {
                            Text("Sync now / ఇప్పుడే సమకాలీకరించు")
                        }
                    }
                }
            }
            val event = state.event
            val user = state.user
            if (event == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (user != null) "No festival on this device yet" else "No event yet",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (user != null)
                                "Join your organizer's festival below with their invite code — you don't need to create anything first."
                            else
                                "Create your festival event on the dashboard first — " +
                                    "invites and multi-counter sync unlock after that. " +
                                    "Everything still works 100% offline meanwhile.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (user == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sign in to sync “${event.name}”", fontWeight = FontWeight.Bold)
                        Text(
                            "Use the Account card above. Collectors approve each other after signing in.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (user != null) {
                if (event == null) {
                    JoinCard(
                        code = joinCode,
                        onCode = { joinCode = it.uppercase().filter(Char::isLetterOrDigit) },
                        valid = isValidShareCode(joinCode),
                        busy = busy == "join",
                        onJoin = { viewModel.join(joinCode, user.uid) {} }
                    )
                } else {
                    var selectedTab by remember(event.id) { mutableStateOf(0) }
                    val isHead = viewModel.isHeadNow(event.id)
                    // Verdict Q3: active collectors get a read-only team view.
                    val canView = viewModel.canViewRoster(event.id)

                    if (canView) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = TempleSaffron,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Sync & Invites", fontWeight = FontWeight.SemiBold) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Team & Counters (${team.size})", fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    if (selectedTab == 0 || !canView) {
                        InviteCard(
                            eventName = event.name,
                            code = state.myCode,
                            busy = busy == "code",
                            onPublish = { viewModel.publishCode(event, user.uid) },
                            canClose = isHead,
                            closing = busy == "closecode",
                            onClose = { state.myCode?.let { viewModel.closeCode(event.id, it) } }
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
                    } else {
                        ConnectedCountersCard(
                            team = team,
                            selfUid = user.uid,
                            busyKey = busy,
                            manageEnabled = isHead,
                            onRefresh = { viewModel.refreshMembers(event.id) },
                            onSetRole = { member, role, status ->
                                viewModel.setMemberRole(event.id, member, role, status)
                            }
                        )
                    }
                }
            }
            OutlinedButton(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
                Text("Voice & admin settings")
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
internal fun InviteCard(
    eventName: String,
    code: String?,
    busy: Boolean,
    onPublish: () -> Unit,
    canClose: Boolean,
    closing: Boolean,
    onClose: () -> Unit,
    /** F2: when true, a WhatsApp-capable Share button renders under the QR. */
    shareEnabled: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                // F2: QR renders off the main thread (was a 262k-setPixel
                // remember block janking composition); spinner meanwhile.
                var bitmap by remember(code) { mutableStateOf<Bitmap?>(null) }
                androidx.compose.runtime.LaunchedEffect(code) {
                    bitmap = runCatching { renderInviteQr(joinQrPayload(code)) }.getOrNull()
                }
                val ready = bitmap
                if (ready == null) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Preparing QR…", style = MaterialTheme.typography.bodySmall)
                } else {
                    Image(
                        bitmap = ready.asImageBitmap(),
                        contentDescription = "Invite QR for code $code",
                        modifier = Modifier.size(180.dp)
                    )
                }
                Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Collectors type this code — or scan — in Cloud sync.", style = MaterialTheme.typography.bodySmall)
                Text("Codes expire 10 days after publishing.", style = MaterialTheme.typography.bodySmall)
                if (shareEnabled) {
                    var sharing by remember(code) { mutableStateOf(false) }
                    Button(
                        onClick = {
                            val bmp = bitmap
                            if (bmp != null && !sharing) {
                                sharing = true
                                scope.launch {
                                    shareInviteQr(context, code, bmp)
                                    sharing = false
                                }
                            }
                        },
                        enabled = bitmap != null && !sharing
                    ) {
                        Text(if (sharing) "Preparing…" else "Share invite (WhatsApp)")
                    }
                }
                // Head-only 1-tap close (feature #2): hidden from everyone
                // else — closing is a head power, like the team directory.
                if (canClose) {
                    OutlinedButton(onClick = onClose, enabled = !closing) {
                        Text(if (closing) "Closing…" else "Close invite")
                    }
                }
            }
        }
    }
}

@Composable
internal fun JoinCard(
    code: String,
    onCode: (String) -> Unit,
    valid: Boolean,
    busy: Boolean,
    onJoin: () -> Unit,
    /** F1: when non-null, an inline counter-name field renders above the code (gear Join). */
    counter: String? = null,
    onCounter: ((String) -> Unit)? = null,
    /** F2: when non-null, a gallery-QR pick button renders (no camera permission). */
    onPickQr: (() -> Unit)? = null,
    pickingQr: Boolean = false
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Join with a code", fontWeight = FontWeight.SemiBold)
            if (onCounter != null) {
                OutlinedTextField(
                    value = counter ?: "",
                    onValueChange = onCounter,
                    label = { Text("Your counter name (shows in Team)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
            if (onPickQr != null) {
                OutlinedButton(
                    onClick = onPickQr,
                    enabled = !busy && !pickingQr,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (pickingQr) "Reading QR…" else "Pick QR image from gallery")
                }
            }
        }
    }
}

@Composable
internal fun ApprovalsCard(
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
                    Text(
                        resolveMemberName(member.counterName, member.displayName, member.email, member.userId),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
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

/**
 * Exclusive Global Head directory (S4.2): live presence, counter identity and
 * 1-tap Collector/Viewer/Revoke. Rendered ONLY under [CloudSyncViewModel.isHeadNow];
 * the VM likewise refuses to fetch or act for anyone else (Rule #1).
 *
 * Perf: parent is a vertically-scrolled Column, so rows are a keyed forEach
 * (no nested lazy); presence labels derive from a minute-ticked clock via
 * derivedStateOf — no per-frame time math in composition.
 */
@Composable
internal fun ConnectedCountersCard(
    team: List<CloudMember>,
    selfUid: String,
    busyKey: String?,
    manageEnabled: Boolean = true,
    onRefresh: () -> Unit,
    onSetRole: (CloudMember, String, String) -> Unit
) {
    var revokeTarget by remember { mutableStateOf<CloudMember?>(null) }
    // Local badge clock: re-ages labels each minute, zero network.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            nowTick = System.currentTimeMillis()
        }
    }
    val rows by remember(team, nowTick) {
        derivedStateOf { team.map { it to presenceOf(it.lastActiveAt, nowTick) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Connected Counters (${team.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Manage collector devices & live presence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onRefresh, enabled = busyKey != "team") {
                Text(if (busyKey == "team") "…" else "Refresh")
            }
        }
        if (rows.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No counters connected yet. Share the invite code in the Sync & Invites tab.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        rows.forEach { (member, presence) ->
            key(member.userId) {
                TeamRow(
                    member = member,
                    presence = presence,
                    isSelf = member.userId == selfUid,
                    manageEnabled = manageEnabled,
                    actionsEnabled = busyKey != "role:${member.userId}",
                    onCollector = {
                        onSetRole(member, SessionPrefs.ROLE_ORGANIZER, SessionPrefs.STATUS_ACTIVE)
                    },
                    onViewer = {
                        onSetRole(member, SessionPrefs.ROLE_MEMBER, SessionPrefs.STATUS_ACTIVE)
                    },
                    onRevoke = { revokeTarget = member }
                )
            }
        }
    }
    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke access?") },
            text = {
                Text(
                    "“${target.counterName ?: target.displayName ?: target.email ?: "this counter"}” " +
                        "loses cloud access instantly. Their past donations stay in the ledger."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetRole(target, target.role, SessionPrefs.STATUS_REVOKED)
                    revokeTarget = null
                }) { Text("Revoke") }
            },
            dismissButton = { TextButton(onClick = { revokeTarget = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun TeamRow(
    member: CloudMember,
    presence: MemberPresence,
    isSelf: Boolean,
    manageEnabled: Boolean,
    actionsEnabled: Boolean,
    onCollector: () -> Unit,
    onViewer: () -> Unit,
    onRevoke: () -> Unit
) {
    val title =
        (member.counterName?.takeIf { it.isNotBlank() }
            ?: member.displayName?.takeIf { it.isNotBlank() }
            ?: "Unknown counter") +
            (member.deviceTag?.takeIf { it.isNotBlank() }?.let { " (#$it)" } ?: "")
    val presenceLabel = when (presence) {
        MemberPresence.ACTIVE_NOW -> "Active now"
        MemberPresence.IDLE -> "Idle"
        MemberPresence.OFFLINE -> "Offline"
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PresenceDot(presence)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    val accountLabel = member.email ?: member.displayName?.takeIf { it.isNotBlank() } ?: "ID: …${member.userId.takeLast(6)}"
                    Text(
                        "$accountLabel • $presenceLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                RoleBadge(role = member.role, status = member.status)
            }
            // Verdict Q3: role buttons are head-only; collectors get the
            // directory above in read-only form.
            if (manageEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCollector,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f)
                ) { Text("Collector", maxLines = 1) }
                OutlinedButton(
                    onClick = onViewer,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f)
                ) { Text("Viewer", maxLines = 1) }
                if (isSelf) {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Text("You", maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = onRevoke,
                        enabled = actionsEnabled,
                        modifier = Modifier.weight(1f)
                    ) { Text("Revoke", maxLines = 1) }
                }
            }
            }
        }
    }
}

@Composable
private fun PresenceDot(presence: MemberPresence) {
    val color = when (presence) {
        MemberPresence.ACTIVE_NOW -> Color(0xFF2E7D32)
        MemberPresence.IDLE -> Color(0xFFF9A825)
        MemberPresence.OFFLINE -> Color(0xFF9E9E9E)
    }
    Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun RoleBadge(role: String, status: String) {
    val (label, bg, fg) = when {
        status == SessionPrefs.STATUS_REVOKED ->
            Triple("Revoked", Color(0xFFB71C1C), Color.White)
        status == SessionPrefs.STATUS_PENDING ->
            Triple("Pending approval", TempleGold, DeepMaroon)
        role == SessionPrefs.ROLE_GLOBAL_HEAD ->
            Triple("Global Head", TempleGold, DeepMaroon)
        role == SessionPrefs.ROLE_ORGANIZER ->
            Triple("Collector", Color(0xFF2E7D32), Color.White)
        else -> Triple(
            "Viewer",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(10.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
