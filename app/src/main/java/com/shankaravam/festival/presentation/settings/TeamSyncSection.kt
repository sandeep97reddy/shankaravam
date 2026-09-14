package com.shankaravam.festival.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.ShankaRavamApp
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.core.util.parseJoinCode
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.data.work.SyncWorker
import com.shankaravam.festival.domain.model.AccessPolicy
import com.shankaravam.festival.domain.model.roleOf
import com.shankaravam.festival.presentation.common.containerViewModel
import kotlinx.coroutines.launch

/**
 * F1 canonical Team & Cloud Sync section, hosted in the gear (Admin Settings).
 *
 * - Sign-in gated: signed-out collectors see only the account card — no
 *   invite codes, no sync toggles (Rule #1: offline donate still works).
 * - Zero-event Join: a fresh install joins the head's festival directly.
 *   Nobody creates a dummy duplicate event to reveal the join box.
 * - Reuses [CloudSyncViewModel] + the shared cards in CloudSyncScreen.kt, so
 *   there is exactly one logic owner (no VM divergence).
 */
@Composable
fun TeamSyncSection(
    counterName: String,
    onSaveCounter: (String) -> Unit,
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
    var counterDraft by remember(counterName) { mutableStateOf(counterName) }
    // F2 gallery-QR pick (zero permission): decode off-thread, prefill the code.
    var qrBusy by remember { mutableStateOf(false) }
    // F3 live seat: the foreground manager's own-seat listener updates this
    // instantly on approve/revoke (no 15-min wait, no stuck pending).
    val app = LocalContext.current.applicationContext as ShankaRavamApp
    val liveSeat by app.container.foregroundSync.seat.collectAsState()
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
                viewModel.info("QR read: $code — tap Request to join.")
            } else {
                viewModel.info("No invite QR found in that image — try a clearer screenshot.")
            }
            qrBusy = false
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.completeSignIn(result.data) }
    androidx.compose.runtime.LaunchedEffect(signInIntent) {
        signInIntent?.let {
            signInLauncher.launch(it)
            viewModel.consumeSignInIntent()
        }
    }
    // Roster auto-load: keys on gate + event, so zero reads for
    // signed-out/pending/revoked (Rule #1).
    val headEventId = state.event?.id
    val rosterNow = headEventId != null && state.user != null && viewModel.canViewRoster(headEventId)
    androidx.compose.runtime.LaunchedEffect(rosterNow, headEventId) {
        if (rosterNow) viewModel.refreshMembers(headEventId!!)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.configured) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cloud not configured", fontWeight = FontWeight.Bold)
                    Text(
                        "This build has no google-services.json, so team sync stays disabled " +
                            "and the app runs 100% offline.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        val user = state.user
        if (user == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Team sync — sign in to join", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Collectors join the head's festival with a 6-letter code or QR — " +
                            "no need to create an event first. Everything keeps working offline meanwhile.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.signIn() },
                        enabled = state.configured && busy == null
                    ) {
                        Text(if (busy == "signin") "Signing in…" else "Sign in with Google")
                    }
                }
            }
        } else {
            val event = state.event
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
                            enabled = state.configured && event != null,
                            onCheckedChange = { viewModel.setSyncEnabled(it, event?.id) }
                        )
                    }
                    Text(
                        lastSyncLine(state.lastSync, state.syncEnabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            if (event != null) {
                                if (!state.syncEnabled) {
                                    viewModel.setSyncEnabled(true, event.id)
                                } else {
                                    viewModel.syncNow(event.id)
                                }
                            }
                        },
                        enabled = event != null && busy != "sync",
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
            if (event == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No festival on this device yet", fontWeight = FontWeight.Bold)
                        Text(
                            "Join your organizer's festival below with their invite code — " +
                                "you don't need to create anything first.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            // F3 live access chip: pending/revoked for THIS event, updated by
            // the foreground seat listener the moment the head acts.
            val seatForEvent = liveSeat?.takeIf { it.eventId == event?.id }
            if (seatForEvent != null && seatForEvent.status != SessionPrefs.STATUS_ACTIVE) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (seatForEvent.status == SessionPrefs.STATUS_PENDING)
                            "⏳ Waiting for head approval — your entries stay on this device until approved."
                        else
                            "Access revoked by the head — changes stay on this device.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            var selectedTab by remember(event?.id) { mutableStateOf(0) }
            val isHead = event != null && viewModel.isHeadNow(event.id)
            val canView = event != null && viewModel.canViewRoster(event.id)

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
                if (event != null) {
                    InviteCard(
                        eventName = event.name,
                        code = state.myCode,
                        busy = busy == "code",
                        onPublish = { viewModel.publishCode(event, user.uid) },
                        canClose = isHead,
                        closing = busy == "closecode",
                        onClose = { state.myCode?.let { viewModel.closeCode(event.id, it) } },
                        shareEnabled = true
                    )
                }
                JoinCard(
                    code = joinCode,
                    // No character filtering: QR payloads / shared links paste verbatim,
                    // parseJoinCode extracts the code (F1).
                    onCode = { joinCode = it },
                    valid = parseJoinCode(joinCode) != null,
                    busy = busy == "join",
                    onJoin = {
                        onSaveCounter(counterDraft.trim())
                        viewModel.join(joinCode, user.uid) { joinCode = "" }
                    },
                    counter = counterDraft,
                    onCounter = { counterDraft = it },
                    onPickQr = { qrPickLauncher.launch("image/*") },
                    pickingQr = qrBusy
                )
                if (event != null && AccessPolicy.canApproveMembers(roleOf(state.myRole))) {
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
                    onRefresh = { viewModel.refreshMembers(event!!.id) },
                    onSetRole = { member, role, status ->
                        viewModel.setMemberRole(event!!.id, member, role, status)
                    }
                )
            }
            // Re-assert the 15-min schedule while sync is on (KEEP policy —
            // no duplicate work, heals a schedule lost to task-clear).
            androidx.compose.runtime.LaunchedEffect(state.syncEnabled) {
                if (state.syncEnabled) viewModel.ensurePeriodic()
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

/** F1 diagnostics line: last successful sync, or the honest never-synced state. */
private fun lastSyncLine(lastSync: Long, enabled: Boolean): String {
    if (lastSync <= 0L) {
        return if (enabled) "Sync is on — first cloud sync hasn't completed yet."
        else "Delta-only via WorkManager (15 min + on-demand). Room stays the screen source of truth."
    }
    val fmt = java.text.SimpleDateFormat("d MMM h:mm a", java.util.Locale.getDefault())
    return "Last synced ${fmt.format(java.util.Date(lastSync))} • delta-only, Room first."
}
