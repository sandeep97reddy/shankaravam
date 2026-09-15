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
import androidx.compose.material3.SwitchDefaults
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
import com.shankaravam.festival.presentation.common.rememberContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.graphics.graphicsLayer
import com.shankaravam.festival.core.theme.CrimsonRose
import com.shankaravam.festival.core.theme.CrimsonWash
import com.shankaravam.festival.core.theme.MaroonWash
import com.shankaravam.festival.core.theme.SaffronWash
import com.shankaravam.festival.core.ui.haptics.LocalAppHaptics
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
        val lastSync: Long = 0L,
        val hasSarvamKey: Boolean = false,
        val voiceSyncedAt: Long = 0L,
        val sarvamSpeaker: String = "shubh"
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
                        lastSync = 0L,
                        hasSarvamKey = container.secureKeys.getSarvamKey().isNotBlank(),
                        voiceSyncedAt = container.sessionPrefs.lastVoiceSyncAt(),
                        sarvamSpeaker = container.sessionPrefs.sarvamSpeaker
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
                        lastSync = container.sessionPrefs.lastSyncMillis(eventId),
                        hasSarvamKey = container.secureKeys.getSarvamKey().isNotBlank(),
                        voiceSyncedAt = container.sessionPrefs.lastVoiceSyncAt(),
                        sarvamSpeaker = container.sessionPrefs.sarvamSpeaker
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
        // Rules admit only the code creator (or master admin) for updates —
        // a non-creator head's tap would fail server-side, so refuse locally.
        if (!canManageCode()) return
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
        // Collectors may approve pending→active (rules S1.2), but revoked or
        // viewer seats cannot — gate locally instead of failing server-side.
        if (!canApproveNow(eventId)) {
            _notice.value = "Only active collectors can approve requests."
            return
        }
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

    /**
     * Creator-or-admin check: firestore.rules admits member updates and code
     * closes only for the event creator (globalHeadId) or the master admin —
     * a promoted non-creator head would fail server-side. The whitelisted
     * admin flag/email covers foreign-event heads (rules isGlobalAdmin path).
     */
    fun canManageTeam(eventId: String): Boolean {
        if (!isHeadNow(eventId)) return false
        val user = container.authRepository.user.value
        val event = uiState.value.event?.takeIf { it.id == eventId }
        if (user != null && event != null &&
            event.globalHeadId.isNotBlank() && event.globalHeadId == user.uid
        ) return true
        if (container.sessionPrefs.isGlobalHeadUser) return true
        return AdminConfig.isGlobalHeadEmail(user?.email)
    }

    /**
     * Invite-close check: codes rules admit updates only for the code creator
     * (usually the event creator who published) or the master admin. The
     * client does not store createdBy, so the event-creator proxy above is
     * the closest local signal — non-creator heads get a clear notice instead
     * of a server denial.
     */
    fun canManageCode(): Boolean {
        val eventId = uiState.value.event?.id ?: return false
        return canManageTeam(eventId)
    }

    /** Active-collector check for the approve path (rules isCollector). */
    fun canApproveNow(eventId: String): Boolean =
        container.sessionPrefs.myStatus(eventId) == SessionPrefs.STATUS_ACTIVE &&
            AccessPolicy.canApproveMembers(roleOf(container.sessionPrefs.myRole(eventId)))

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
        // Full-manage is creator-or-admin server-side (approve path excepted,
        // which flows through approve()); refuse other heads locally.
        if (!canManageTeam(eventId)) {
            _notice.value = "Only the festival creator can change roles."
            return
        }
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

    // ---- Committee Shared Voice & Sarvam AI ----

    fun getSavedSarvamKey(): String = container.secureKeys.getSarvamKey()
    fun getSavedSarvamSpeaker(): String = container.sessionPrefs.sarvamSpeaker

    fun setSarvamSpeaker(speaker: String) {
        container.sessionPrefs.sarvamSpeaker = speaker
        codeTick.value += 1
    }

    fun saveKeyLocally(key: String, speaker: String) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        container.secureKeys.setSarvamKey(clean)
        container.sessionPrefs.sarvamSpeaker = speaker
        // Configuring a cloud key means cloud mode — blank clears to offline.
        container.sessionPrefs.voiceEngineMode = if (clean.isBlank()) {
            com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
        } else {
            com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD
        }
        container.sessionPrefs.voiceOfflineLocked = clean.isBlank()
        codeTick.value += 1
        _notice.value = if (clean.isBlank()) {
            "Cloud key removed — offline voice active."
        } else if (container.secureKeys.isEncrypted) {
            "✓ Key stored encrypted & cloud voice activated."
        } else {
            "✓ Key stored locally & cloud voice activated."
        }
    }

    fun clearKeyLocally() {
        container.secureKeys.setSarvamKey("")
        container.sessionPrefs.voiceEngineMode =
            com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
        // Explicit offline choice — auto-pull must not flip the mode back.
        container.sessionPrefs.voiceOfflineLocked = true
        codeTick.value += 1
        _notice.value = "Cloud key removed — offline device voice active."
    }

    fun pushKey(key: String, speaker: String) {
        val uid = container.authRepository.user.value?.uid
        if (uid == null) {
            _notice.value = "Sign in first using Google Account above."
            return
        }
        viewModelScope.launch {
            _busy.value = "pushvoice"
            saveKeyLocally(key, speaker)
            when (container.syncService.writeTtsKey(key.trim(), speaker, uid)) {
                is Outcome.Ok -> {
                    codeTick.value += 1
                    _notice.value = "Shared key published for collectors."
                }
                is Outcome.Err -> _notice.value = "Publish failed — saved on this device only."
            }
            _busy.value = null
        }
    }

    fun pullKey() {
        viewModelScope.launch {
            _busy.value = "pullvoice"
            val pulled = container.syncService.maybeAutoPullVoice(force = true, respectLock = false)
            if (pulled.applied) {
                codeTick.value += 1
                _notice.value = "Shared voice settings applied."
            } else if (!pulled.remotePresent) {
                _notice.value = "No shared key published yet — the head publishes it from this card."
            } else {
                codeTick.value += 1
                _notice.value = "Already up to date with the shared voice."
            }
            _busy.value = null
        }
    }

    fun autoPullVoice() {
        viewModelScope.launch {
            if (container.syncService.maybeAutoPullVoice().applied) {
                codeTick.value += 1
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
        if (_testingSarvam.value) return
        if (!container.sessionPrefs.takeSarvamSlot()) {
            _sarvamTestStatus.value = "✗ Free-tier limit reached (20 Sarvam calls per 30 min). Try later — offline voice still works."
            return
        }
        viewModelScope.launch {
            _testingSarvam.value = true
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

    val testStatus by viewModel.sarvamTestStatus.collectAsState()
    val testing by viewModel.testingSarvam.collectAsState()
    val savedSarvamKey = remember(state.hasSarvamKey) { viewModel.getSavedSarvamKey() }
    val savedSarvamSpeaker = remember(state.sarvamSpeaker) { viewModel.getSavedSarvamSpeaker() }
    var sarvamKeyDraft by remember(savedSarvamKey) { mutableStateOf(savedSarvamKey) }
    var sarvamSpeakerDraft by remember(savedSarvamSpeaker) { mutableStateOf(savedSarvamSpeaker) }
    val container = rememberContainer()
    val activeGatewayUrl by container.sessionPrefs.gatewayBaseUrlFlow.collectAsState()

    var voiceAccordionExpanded by remember { mutableStateOf(false) }
    var gatewayExpanded by remember { mutableStateOf(false) }

    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.completeSignIn(result.data) }

    var qrBusy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qrPickLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || qrBusy) return@rememberLauncherForActivityResult
        qrBusy = true
        scope.launch {
            val code = decodeJoinCodeFromUri(context.contentResolver, uri)
            if (code != null) {
                joinCode = code
                viewModel.info("QR read: $code — tap Request to join.")
            } else {
                viewModel.info("No invite QR found in that image — try a clearer screenshot.")
            }
            qrBusy = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(signInIntent) {
        signInIntent?.let {
            signInLauncher.launch(it)
            viewModel.consumeSignInIntent()
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.autoPullVoice()
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
                            onCheckedChange = { viewModel.setSyncEnabled(it, state.event?.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TempleSaffron,
                                checkedBorderColor = TempleSaffron,
                                // OFF but enabled must not look disabled: solid
                                // outline-gray track + white thumb reads clearly
                                // in light AND dark mode; truly-disabled keeps
                                // the washed-out default alpha.
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
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
                if (user == null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GroupAdd,
                                    contentDescription = null,
                                    tint = TempleSaffron,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    "Join a Festival Committee",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepMaroon
                                )
                            }
                            Text(
                                "To join an existing festival using an invite code or QR screenshot, sign in with Google so your counter identity is recognized by the organizer.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.signIn() },
                                enabled = state.configured && busy == null,
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (busy == "signin") "Signing in…" else "Sign in with Google to Join")
                            }
                        }
                    }
                } else {
                    JoinCard(
                        code = joinCode,
                        onCode = { joinCode = it.uppercase().filter(Char::isLetterOrDigit) },
                        valid = isValidShareCode(joinCode),
                        busy = busy == "join",
                        onJoin = { viewModel.join(joinCode, user.uid) { joinCode = "" } },
                        onPickQr = { qrPickLauncher.launch("image/*") },
                        pickingQr = qrBusy
                    )
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
            } else {
                var selectedTab by remember(event.id) { mutableStateOf(0) }
                // Creator-or-admin proxy: non-creator heads see the team
                // read-only and no close button (rules would deny them).
                val canManage = viewModel.canManageTeam(event.id)
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
                        canClose = canManage,
                        closing = busy == "closecode",
                        onClose = { state.myCode?.let { viewModel.closeCode(event.id, it) } }
                    )
                    JoinCard(
                        code = joinCode,
                        onCode = { joinCode = it.uppercase().filter(Char::isLetterOrDigit) },
                        valid = isValidShareCode(joinCode),
                        busy = busy == "join",
                        onJoin = { viewModel.join(joinCode, user.uid) { joinCode = "" } },
                        onPickQr = { qrPickLauncher.launch("image/*") },
                        pickingQr = qrBusy
                    )
                        if (viewModel.canApproveNow(event.id)) {
                            ApprovalsCard(
                                pending = pending,
                                busyKey = busy,
                                onRefresh = { viewModel.refreshPending(event.id) },
                                onApprove = { member, role ->
                                    viewModel.approve(event.id, member, role, user.uid)
                                }
                            )
                        }

                        // Committee Shared Voice Card (Global Head pushes, Collectors pull)
                        val voiceSource = when {
                            !state.hasSarvamKey -> null
                            state.voiceSyncedAt > 0L -> {
                                val dateStr = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                                    .format(java.util.Date(state.voiceSyncedAt))
                                "Shared by head (synced $dateStr)"
                            }
                            else -> "Local key on this device"
                        }
                        val voiceShared = state.voiceSyncedAt > 0L
                        val voiceSummary = when {
                            activeGatewayUrl.isNotBlank() -> "Gateway Active • Cloud Voice Enabled"
                            state.hasSarvamKey -> "Sarvam Key Active • ${state.sarvamSpeaker.replaceFirstChar { it.uppercase() }}"
                            else -> "Not configured • Free offline Android voice active"
                        }

                        SettingsAccordionCard(
                            title = "Committee Shared Voice",
                            teluguTitle = "ఉమ్మడి క్లౌడ్ గొంతు (శర్వం AI)",
                            summary = voiceSummary,
                            icon = Icons.Filled.Cloud,
                            iconTint = TempleSaffron,
                            iconBackground = SaffronWash,
                            isExpanded = voiceAccordionExpanded,
                            onToggle = { voiceAccordionExpanded = !voiceAccordionExpanded }
                        ) {
                            VoiceKeyContent(
                                key = sarvamKeyDraft,
                                onKeyChange = { sarvamKeyDraft = it },
                                speaker = sarvamSpeakerDraft,
                                onSpeakerChange = {
                                    sarvamSpeakerDraft = it
                                    viewModel.setSarvamSpeaker(it)
                                },
                                savedKey = savedSarvamKey,
                                busy = busy == "pushvoice" || busy == "pullvoice",
                                testStatus = testStatus,
                                testing = testing,
                                hasKey = state.hasSarvamKey,
                                hasGateway = activeGatewayUrl.isNotBlank(),
                                canPublish = viewModel.canManageTeam(event.id),
                                onSaveLocal = { k, s -> viewModel.saveKeyLocally(k, s) },
                                onClearKey = {
                                    sarvamKeyDraft = ""
                                    viewModel.clearKeyLocally()
                                },
                                onPush = { k, s -> viewModel.pushKey(k, s) },
                                onPull = {
                                    viewModel.pullKey()
                                    sarvamKeyDraft = viewModel.getSavedSarvamKey()
                                    sarvamSpeakerDraft = viewModel.getSavedSarvamSpeaker()
                                },
                                onTestSarvam = { k, s -> viewModel.testSarvamVoice(k, s) },
                                voiceSource = voiceSource,
                                voiceShared = voiceShared
                            )
                        }
                    } else {
                        ConnectedCountersCard(
                            team = team,
                            selfUid = user.uid,
                            busyKey = busy,
                            manageEnabled = canManage,
                            onRefresh = { viewModel.refreshMembers(event.id) },
                            onSetRole = { member, role, status ->
                                viewModel.setMemberRole(event.id, member, role, status)
                            }
                        )
                    }
                }

            // Temple Media Gateway (Single-sourced in collapsed accordion at bottom)
            val gatewaySummary = if (activeGatewayUrl.isNotBlank()) "Connected • Cloudflare Worker & R2" else "Optional Worker & R2 endpoint"
            SettingsAccordionCard(
                title = "Temple Media Gateway",
                teluguTitle = "మీడియా గేట్‌వే (Cloudflare R2)",
                summary = gatewaySummary,
                icon = Icons.Filled.Cloud,
                iconTint = DeepMaroon,
                iconBackground = MaroonWash,
                isExpanded = gatewayExpanded,
                onToggle = { gatewayExpanded = !gatewayExpanded }
            ) {
                GatewayCard()
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
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Access Requests",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepMaroon
                        )
                        if (pending.isNotEmpty()) {
                            Surface(
                                color = TempleSaffron,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "${pending.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "Approve volunteer counters to join",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RefreshPill(
                    refreshing = busyKey == "pending",
                    onClick = onRefresh
                )
            }

            if (pending.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "All caught up! No pending join requests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                pending.forEach { member ->
                    key(member.userId) {
                        val displayName = resolveMemberName(
                            member.counterName,
                            member.displayName,
                            member.email,
                            member.userId
                        )
                        val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
                        val isApproving = busyKey == "approve:${member.userId}"

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(SaffronWash)
                                    ) {
                                        Text(
                                            text = initial,
                                            fontWeight = FontWeight.Bold,
                                            color = TempleSaffron,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        val sub = member.email ?: "ID: …${member.userId.takeLast(6)}"
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onApprove(member, SessionPrefs.ROLE_ORGANIZER) },
                                        enabled = !isApproving,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = TempleSaffron
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (isApproving) "…" else "Approve Collector",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onApprove(member, SessionPrefs.ROLE_MEMBER) },
                                        enabled = !isApproving,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "Approve Viewer",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern tonal Refresh Pill with infinite rotation animation, loading state, and haptics.
 */
@Composable
private fun RefreshPill(
    refreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Refresh"
) {
    val haptics = LocalAppHaptics.current
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        onClick = {
            if (!refreshing) {
                haptics.tick()
                onClick()
            }
        },
        enabled = !refreshing,
        shape = RoundedCornerShape(20.dp),
        color = SaffronWash,
        border = BorderStroke(1.dp, TempleSaffron.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = label,
                tint = TempleSaffron,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        if (refreshing) rotationZ = rotation
                    }
            )
            Text(
                text = if (refreshing) "Syncing…" else label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DeepMaroon
            )
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
                Text(
                    "Connected Counters (${team.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DeepMaroon
                )
                Text(
                    "Manage collector devices & live presence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RefreshPill(
                refreshing = busyKey == "team",
                onClick = onRefresh
            )
        }
        if (rows.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "No counters connected yet. Share the invite code in the Sync & Invites tab.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }) {
                    Text("Revoke", color = CrimsonRose, fontWeight = FontWeight.Bold)
                }
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
    val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"

    val presenceLabel = when (presence) {
        MemberPresence.ACTIVE_NOW -> "Active now"
        MemberPresence.IDLE -> "Idle"
        MemberPresence.OFFLINE -> "Offline"
    }
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 44dp circular avatar with saffron wash tint + presence dot
                Box {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SaffronWash)
                    ) {
                        Text(
                            text = initial,
                            fontWeight = FontWeight.Bold,
                            color = TempleSaffron,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 1.dp, bottom = 1.dp)
                    ) {
                        PresenceDot(presence)
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    val accountLabel = member.email ?: member.displayName?.takeIf { it.isNotBlank() } ?: "ID: …${member.userId.takeLast(6)}"
                    Text(
                        text = "$accountLabel • $presenceLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                RoleBadge(role = member.role, status = member.status)
            }

            // Segmented role switcher chips + Revoke button (head-only)
            if (manageEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isCollector = member.role == SessionPrefs.ROLE_ORGANIZER
                    val isViewer = member.role == SessionPrefs.ROLE_MEMBER

                    FilterChip(
                        selected = isCollector,
                        onClick = onCollector,
                        enabled = actionsEnabled && member.role != SessionPrefs.ROLE_GLOBAL_HEAD,
                        label = { Text("Collector", maxLines = 1) },
                        leadingIcon = if (isCollector) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SaffronWash,
                            selectedLabelColor = DeepMaroon,
                            selectedLeadingIconColor = TempleSaffron
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = isViewer,
                        onClick = onViewer,
                        enabled = actionsEnabled && member.role != SessionPrefs.ROLE_GLOBAL_HEAD,
                        label = { Text("Viewer", maxLines = 1) },
                        leadingIcon = if (isViewer) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelf) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text(
                                text = "This device",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRevoke,
                            enabled = actionsEnabled && member.role != SessionPrefs.ROLE_GLOBAL_HEAD,
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = CrimsonRose
                            ),
                            border = BorderStroke(1.dp, CrimsonRose.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("Revoke", maxLines = 1, color = CrimsonRose)
                        }
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
        MemberPresence.IDLE -> Color(0xFFF57C00)
        MemberPresence.OFFLINE -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color, CircleShape)
        )
    }
}

@Composable
private fun RoleBadge(role: String, status: String) {
    val (label, bg, fg) = when {
        status == SessionPrefs.STATUS_REVOKED ->
            Triple("Revoked", CrimsonWash, CrimsonRose)
        status == SessionPrefs.STATUS_PENDING ->
            Triple("Pending", MaroonWash, DeepMaroon)
        role == SessionPrefs.ROLE_GLOBAL_HEAD ->
            Triple("👑 Global Head", TempleGold, DeepMaroon)
        role == SessionPrefs.ROLE_ORGANIZER ->
            Triple("🏷️ Collector", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        else -> Triple(
            "👁️ Viewer",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
