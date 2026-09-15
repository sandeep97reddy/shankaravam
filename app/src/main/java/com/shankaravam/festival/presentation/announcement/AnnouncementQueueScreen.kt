package com.shankaravam.festival.presentation.announcement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import com.shankaravam.festival.core.i18n.appStrings
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shankaravam.festival.core.audio.displayName
import com.shankaravam.festival.core.theme.CrimsonRose
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.domain.model.SARVAM_SPEAKER_ORDER
import com.shankaravam.festival.domain.model.sarvamPickerLabel
import com.shankaravam.festival.domain.model.sarvamPickerSublabel
import com.shankaravam.festival.presentation.common.containerViewModel
import com.shankaravam.festival.presentation.common.rememberContainer
import com.shankaravam.festival.presentation.donation.DonationCard

/**
 * Announcement queue (plan §11): route badge + voice picker, transport
 * controls, persisted gap/sort/language, tappable playlist. API keys are
 * managed in Settings only — this screen never handles secrets.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnnouncementQueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnnouncementQueueViewModel = containerViewModel { AnnouncementQueueViewModel(it) }
) {
    val state by viewModel.uiState.collectAsState()
    val importReport by viewModel.importReport.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val gatewayError by viewModel.gatewayError.collectAsState()
    val rosterPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris -> viewModel.importRosterClips(uris) }

    Scaffold(
        modifier = modifier,
        topBar = {
            com.shankaravam.festival.presentation.common.TempleAppBar(
                title = "Announcements",
                onBack = onBack
            )
        }
    ) { padding ->
        if (!state.hasEvent) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select or create an event to announce.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                VoiceSettingsCard(
                    route = state.route,
                    testingAudio = state.testingAudio,
                    onTestAudio = { viewModel.testAudio() },
                    gatewayError = gatewayError,
                    onClearGatewayError = { viewModel.clearGatewayError() }
                )
            }
            item {
                RosterImportCard(
                    report = importReport,
                    onPick = { rosterPicker.launch("audio/*") },
                    onDismissReport = { viewModel.consumeImportReport() }
                )
            }
            item {
                TransportCard(
                    state = state,
                    playbackError = playbackError,
                    onClearError = { viewModel.clearPlaybackError() },
                    onPlay = { viewModel.play() },
                    onPause = { viewModel.pause() },
                    onStop = { viewModel.stop() },
                    onNext = { viewModel.next() },
                    onPrevious = { viewModel.previous() },
                    onReplay = { viewModel.replay() },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onToggleRoster = { viewModel.toggleRosterMode() },
                    onPreset = { viewModel.setFestivalPreset(it) },
                    onGap = { viewModel.setGap(it) },
                    onSort = { viewModel.setSort(it) },
                    onLanguage = { viewModel.setLanguage(it) },
                    onPledged = { viewModel.setShowPledged(it) },
                    onTag = { viewModel.setTag(it) }
                )
            }
            if (state.queue.isEmpty()) {
                item {
                    Text(
                        if (state.showPledged) "No pledged donations to announce."
                        else "No received/confirmed donations yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(items = state.queue, key = { _, d -> d.id }) { position, donation ->
                    val isCurrent = position == state.index && (state.isPlaying || state.isPaused)
                    DonationCard(
                        donation = donation,
                        modifier = Modifier
                            .animateItem()
                            .then(
                                if (isCurrent) Modifier.border(
                                    2.dp,
                                    TempleGold,
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            ),
                        onClick = { viewModel.jumpTo(position) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioRouteBadge(
    route: com.shankaravam.festival.core.audio.AudioRoute,
    modifier: Modifier = Modifier
) {
    val isTelugu = appStrings().languageCode == "te"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = when (route) {
            com.shankaravam.festival.core.audio.AudioRoute.BLUETOOTH -> TempleSaffron.copy(alpha = 0.12f)
            com.shankaravam.festival.core.audio.AudioRoute.WIRED -> TempleGold.copy(alpha = 0.18f)
            com.shankaravam.festival.core.audio.AudioRoute.SPEAKER -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = when (route) {
                    com.shankaravam.festival.core.audio.AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
                    com.shankaravam.festival.core.audio.AudioRoute.WIRED -> Icons.Filled.Headphones
                    com.shankaravam.festival.core.audio.AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = null,
                tint = when (route) {
                    com.shankaravam.festival.core.audio.AudioRoute.BLUETOOTH -> TempleSaffron
                    com.shankaravam.festival.core.audio.AudioRoute.WIRED -> DeepMaroon
                    com.shankaravam.festival.core.audio.AudioRoute.SPEAKER -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = when (route) {
                    com.shankaravam.festival.core.audio.AudioRoute.BLUETOOTH -> if (isTelugu) "బ్లూటూత్ Horn" else "Bluetooth Horn"
                    com.shankaravam.festival.core.audio.AudioRoute.WIRED -> if (isTelugu) "హెడ్‌సెట్" else "Headset"
                    com.shankaravam.festival.core.audio.AudioRoute.SPEAKER -> if (isTelugu) "స్పీకర్" else "Speaker"
                },
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = when (route) {
                    com.shankaravam.festival.core.audio.AudioRoute.BLUETOOTH -> TempleSaffron
                    com.shankaravam.festival.core.audio.AudioRoute.WIRED -> DeepMaroon
                    com.shankaravam.festival.core.audio.AudioRoute.SPEAKER -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun VoiceSettingsCard(
    route: com.shankaravam.festival.core.audio.AudioRoute,
    testingAudio: Boolean,
    onTestAudio: () -> Unit,
    gatewayError: String = "",
    onClearGatewayError: () -> Unit = {}
) {
    val container = rememberContainer()
    val prefs = remember { container.sessionPrefs }
    var showMenu by remember { mutableStateOf(false) }

    // Phase 2: collect flows — never snapshot prefs into remember {}.
    // A pick in Settings ⚙️ (or vice versa) re-renders here instantly (RC1).
    val engineMode by prefs.voiceEngineModeFlow.collectAsState()
    val speaker by prefs.sarvamSpeakerFlow.collectAsState()
    val nativeVoice by prefs.nativeTtsVoiceFlow.collectAsState()
    val hasKey by container.secureKeys.hasKeyFlow.collectAsState()
    val gatewayUrl by prefs.gatewayBaseUrlFlow.collectAsState()
    val currentUser by container.authRepository.user.collectAsState()
    val hasGateway = gatewayUrl.isNotBlank()
    val hasCloudActive = hasKey || (hasGateway && currentUser != null)

    var showKeyDialog by remember { mutableStateOf(false) }
    var pendingSpeaker by remember { mutableStateOf("shubh") }

    val nativeReady by container.ttsEngine.nativeReady.collectAsState()
    // Reactive: the native engine boots async (~200ms), so reload the list
    // when it becomes ready instead of snapshotting once at composition.
    var nativeVoices by remember { mutableStateOf(emptyList<String>()) }
    var nativeVoiceInfos by remember { mutableStateOf(emptyList<com.shankaravam.festival.domain.model.NativeVoiceInfo>()) }
    androidx.compose.runtime.LaunchedEffect(nativeReady) {
        if (nativeReady) {
            nativeVoiceInfos = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { container.ttsEngine.native.getAvailableTeluguVoiceInfos() }
                    .getOrDefault(emptyList())
            }
            nativeVoices = nativeVoiceInfos.map { it.name }
        }
    }

    // One shared label, mode-first (RC3 fix — the old when{} checked hasKey
    // first, so native picks never changed the label while a key existed).
    val config = com.shankaravam.festival.domain.model.VoiceConfig(
        engineMode = engineMode,
        sarvamSpeaker = speaker,
        nativeVoice = nativeVoice,
        hasSarvamKey = hasKey,
        hasGateway = hasGateway,
        hasSignIn = currentUser != null
    )

    // Every pick writes prefs (the single source of truth) AND takes effect
    // live: speaker/mode flow into DualTtsEngine providers; native voice
    // applies to the TTS engine immediately ("" resets to default).
    fun pickCloud(next: String) {
        prefs.sarvamSpeaker = next
        showMenu = false
        if (!hasCloudActive) {
            pendingSpeaker = next
            showKeyDialog = true
        } else {
            prefs.voiceEngineMode = com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD
        }
    }

    fun pickNative(next: String?) {
        prefs.nativeTtsVoice = next
        container.ttsEngine.native.setVoiceByName(next ?: "")
        prefs.voiceEngineMode = com.shankaravam.festival.domain.model.VoiceEngineMode.OFFLINE_NATIVE
        showMenu = false
    }

    val isTelugu = appStrings().languageCode == "te"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Concise Title on Left, Audio Route & Cloud Indicator on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = TempleSaffron,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = if (isTelugu) "గొంతు ఎంపిక (Voice)" else "Temple Voice",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AudioRouteBadge(route = route)
                    if (hasGateway && (currentUser != null || hasKey)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                "Cloud ✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (hasKey) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                "Key ✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (hasGateway && currentUser == null) {
                        TextButton(
                            onClick = {
                                pendingSpeaker = speaker
                                showKeyDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                "Sign In / Key",
                                style = MaterialTheme.typography.labelSmall,
                                color = TempleSaffron,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                pendingSpeaker = speaker
                                showKeyDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                "+ Cloud",
                                style = MaterialTheme.typography.labelSmall,
                                color = TempleSaffron,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Unified Row: Voice Selector Dropdown (occupies remaining width) + Compact Test Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dropdown Selector Button
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = config.displayLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select voice",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Cloud Section (T0.5 lineup: Shubh default, Pooja
                        // secondary — single order in Voice.kt).
                        SARVAM_SPEAKER_ORDER.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            sarvamPickerLabel(option) +
                                                if (option == "shubh") " • Recommended" else "",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            sarvamPickerSublabel(option),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = { pickCloud(option) }
                            )
                        }

                        HorizontalDivider()

                        // Native Section
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("📱 System Default", fontWeight = FontWeight.SemiBold)
                                    Text("Android Built-in • 100% Offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { pickNative(null) }
                        )

                        if (nativeVoiceInfos.isNotEmpty()) {
                            nativeVoiceInfos.forEach { info ->
                                val vLabel = info.displayName.takeLast(10)
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("📱 Android Telugu ($vLabel)", fontWeight = FontWeight.SemiBold)
                                            Text("${info.badgeLabel} • ${info.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { pickNative(info.name) }
                                )
                            }
                        } else {
                            nativeVoices.forEach { voiceName ->
                                val vLabel = voiceName.substringAfterLast("-", voiceName.takeLast(8))
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("📱 Android Telugu ($vLabel)", fontWeight = FontWeight.SemiBold)
                                            Text("Device voice: $voiceName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { pickNative(voiceName) }
                                )
                            }
                        }
                    }
                }

                // Compact Test Audio Button
                Button(
                    onClick = onTestAudio,
                    enabled = !testingAudio,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    if (testingAudio) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Testing…", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isTelugu) "పరీక్ష" else "Test", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status micro-line
            Text(
                text = config.statusLine(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Gateway diagnostics: last fallback reason (401/403/timeout/...).
            // This is what turns "still local voice" from a mystery into an action.
            if (gatewayError.isNotBlank()) {
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ $gatewayError — speaking offline meanwhile. Tap Test again after fixing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onClearGatewayError,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Hide", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showKeyDialog) {
        var keyInput by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = {
                Text(
                    "Connect Cloud Voice / క్లౌడ్ గొంతు అనుసంధానం",
                    fontWeight = FontWeight.Bold,
                    color = DeepMaroon
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "To use studio Telugu voice (${pendingSpeaker.replaceFirstChar { it.uppercase() }}), enter your Cloudflare Gateway URL (Recommended — no API key needed on this phone!) or a direct Sarvam API key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasGateway && currentUser == null && !hasKey) {
                        Text(
                            "💡 Tip: Gateway is active. Sign in with Google (Settings ⚙️ → Cloud Sync) to use the shared gateway, or paste a direct Sarvam AI API key below to use without signing in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = com.shankaravam.festival.core.theme.TempleSaffron,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            inputError = null
                        },
                        label = { Text("Gateway URL or Sarvam Key") },
                        placeholder = { Text("https://shankaravam-gateway.<subdomain>.workers.dev") },
                        singleLine = true,
                        isError = inputError != null,
                        supportingText = {
                            if (inputError != null) {
                                Text(inputError!!, color = CrimsonRose)
                            } else {
                                Text(
                                    "Paste your Cloudflare Worker URL (e.g. https://...workers.dev)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = keyInput.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                        if (trimmed.isBlank()) {
                            inputError = "Please enter a Gateway URL or API key"
                        } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains("workers.dev")) {
                            prefs.gatewayBaseUrl = trimmed.trimEnd('/')
                            prefs.sarvamSpeaker = pendingSpeaker
                            prefs.voiceEngineMode = com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD
                            showKeyDialog = false
                        } else {
                            container.secureKeys.setSarvamKey(trimmed)
                            prefs.sarvamSpeaker = pendingSpeaker
                            prefs.voiceEngineMode = com.shankaravam.festival.domain.model.VoiceEngineMode.SARVAM_CLOUD
                            showKeyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TempleSaffron,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save & Activate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog = false }) {
                    Text("Cancel / రద్దు")
                }
            }
        )
    }
}

@Composable
private fun RosterImportCard(
    report: String?,
    onPick: () -> Unit,
    onDismissReport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Shared roster clips", fontWeight = FontWeight.SemiBold)
            Text(
                "Pick shared mp3 clips in one batch — each is matched to a donor by name. Unmatched files are reported, never force-attached.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text("Import roster clips")
            }
            report?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismissReport) { Text("Dismiss") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportCard(
    state: QueueUiState,
    playbackError: String?,
    onClearError: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReplay: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleRoster: () -> Unit,
    onPreset: (String) -> Unit,
    onGap: (Int) -> Unit,
    onSort: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onPledged: (Boolean) -> Unit,
    onTag: (String?) -> Unit
) {
    var showSort by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(14.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.current?.let { current ->
                Text(
                    "${state.index + 1} / ${state.queue.size} • ${current.donorName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            } ?: Text(
                "${state.queue.size} in queue",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            AnimatedVisibility(
                visible = playbackError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                playbackError?.let { error ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonRose,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.prefetchRemaining > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    "Preparing cloud audio… (${state.prefetchRemaining} left, offline voice fills gaps)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Phase 3 quota transparency (RC4): the pill explains WHY some rows
            // speak in the offline voice instead of silently flipping mid-queue.
            AnimatedVisibility(
                visible = state.quotaPill.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    state.quotaPill,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TempleSaffron
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = onReplay) {
                    Icon(Icons.Filled.Replay, contentDescription = "Replay")
                }
                if (state.isPlaying) {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }
                } else {
                    IconButton(onClick = onPlay, enabled = state.queue.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = "Repeat list",
                        tint = if (state.repeat) TempleGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Announcement Style Toggle (Roster vs Full)
            Text("Announcement Style / శైలి", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.rosterMode,
                    onClick = onToggleRoster,
                    label = { Text("జాబితా శైలి (Roster Mode)") }
                )
                FilterChip(
                    selected = !state.rosterMode,
                    onClick = onToggleRoster,
                    label = { Text("పూర్తి వాక్యాలు (Full)") }
                )
            }

            // Festival Opening Preset (when in roster mode)
            if (state.rosterMode) {
                Text(
                    if (state.festivalPresetAuto) "Festival Opening / ప్రారంభ ప్రకటన (auto • ఆటో)"
                    else "Festival Opening / ప్రారంభ ప్రకటన",
                    style = MaterialTheme.typography.labelSmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "VINAYAKA_CHAVITHI" to "వినాయక చవితి",
                        "KANAKA_DURGAMMA" to "కనకదుర్గమ్మ",
                        "SRI_RAMA_NAVAMI" to "శ్రీరామనవమి",
                        "HANUMAN_JAYANTHI" to "హనుమాన్ జయంతి",
                        "MAHA_SHIVARATRI" to "మహా శివరాత్రి",
                        "TEMPLE_ANNADANAM" to "ఆలయ అన్నదానం"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = state.festivalPreset == key,
                            onClick = { onPreset(key) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Text("Pause between announcements", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 5, 10).forEach { gap ->
                    FilterChip(
                        selected = state.gapSeconds == gap,
                        onClick = { onGap(gap) },
                        label = { Text("${gap}s") }
                    )
                }
            }
            Text("Language", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.language == SessionPrefs.LANG_TELUGU,
                    onClick = { onLanguage(SessionPrefs.LANG_TELUGU) },
                    label = { Text("Telugu") }
                )
                FilterChip(
                    selected = state.language == SessionPrefs.LANG_ENGLISH,
                    onClick = { onLanguage(SessionPrefs.LANG_ENGLISH) },
                    label = { Text("English") }
                )
                FilterChip(
                    selected = state.language == SessionPrefs.LANG_BILINGUAL,
                    onClick = { onLanguage(SessionPrefs.LANG_BILINGUAL) },
                    label = { Text("Both") }
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.showPledged,
                    onClick = { onPledged(false) },
                    label = { Text("Received") }
                )
                FilterChip(
                    selected = state.showPledged,
                    onClick = { onPledged(true) },
                    label = { Text("Pledged") }
                )
                TextButton(onClick = { showSort = !showSort }) {
                    Text("Sort: ${queueSortLabel(state.sort)}")
                }
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    listOf(
                        SessionPrefs.SORT_NEWEST, SessionPrefs.SORT_OLDEST,
                        SessionPrefs.SORT_AMOUNT_DESC, SessionPrefs.SORT_AMOUNT_ASC,
                        SessionPrefs.SORT_NAME_ASC
                    ).forEach { key ->
                        DropdownMenuItem(text = { Text(queueSortLabel(key)) }, onClick = {
                            onSort(key); showSort = false
                        })
                    }
                }
            }
            if (state.availableTags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.availableTags.take(10).forEach { tag ->
                        FilterChip(
                            selected = state.tagFilter == tag,
                            onClick = { onTag(if (state.tagFilter == tag) null else tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }
        }
    }
}

private fun queueSortLabel(key: String): String = when (key) {
    SessionPrefs.SORT_OLDEST -> "Oldest"
    SessionPrefs.SORT_AMOUNT_DESC -> "Amount ↓"
    SessionPrefs.SORT_AMOUNT_ASC -> "Amount ↑"
    SessionPrefs.SORT_NAME_ASC -> "Name A–Z"
    else -> "Newest"
}
