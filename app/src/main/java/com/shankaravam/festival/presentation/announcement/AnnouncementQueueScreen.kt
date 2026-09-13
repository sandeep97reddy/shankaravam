package com.shankaravam.festival.presentation.announcement

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shankaravam.festival.core.audio.displayName
import com.shankaravam.festival.core.theme.CrimsonRose
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.presentation.common.containerViewModel
import com.shankaravam.festival.presentation.common.rememberContainer
import com.shankaravam.festival.presentation.donation.DonationCard
import kotlinx.coroutines.launch

/**
 * Announcement queue (plan §11): route badge + test audio, cloud-key row,
 * transport controls, persisted gap/sort/language, tappable playlist.
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
            item { RouteCard(state.route.displayName(), state.testingAudio, state.nativeReady) { viewModel.testAudio() } }
            item { VoiceSettingsCard() }
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
                itemsIndexed(items = state.queue, key = { index, d -> "${d.id}_$index" }) { position, donation ->
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
private fun RouteCard(
    routeName: String,
    testing: Boolean,
    nativeReady: Boolean,
    onTest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = TempleSaffron
            )
            Column(Modifier.weight(1f)) {
                Text(routeName, fontWeight = FontWeight.SemiBold)
                Text(
                    if (nativeReady) "Voice engine ready • offline" else "Initializing voice…",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onTest) { Text("Test audio") }
            }
        }
    }
}

@Composable
private fun VoiceSettingsCard() {
    val container = rememberContainer()
    val prefs = remember { container.sessionPrefs }
    val scope = rememberCoroutineScope()
    var storedKey by remember { mutableStateOf<String?>(null) }
    var speaker by remember { mutableStateOf(prefs.sarvamSpeaker) }
    var selectedNativeVoice by remember { mutableStateOf(prefs.nativeTtsVoice) }
    var showMenu by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }

    val nativeReady by container.ttsEngine.nativeReady.collectAsState()
    // Reactive: the native engine boots async (~200ms), so reload the list
    // when it becomes ready instead of snapshotting once at composition.
    var nativeVoices by remember { mutableStateOf(emptyList<String>()) }
    androidx.compose.runtime.LaunchedEffect(nativeReady) {
        if (nativeReady) {
            nativeVoices = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { container.ttsEngine.native.getAvailableTeluguVoices() }
                    .getOrDefault(emptyList())
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { container.secureKeys.getSarvamKey() }.getOrDefault("")
        }
        storedKey = loaded
        keyDraft = loaded
    }

    val hasKey = !storedKey.isNullOrBlank()
    val normSpeaker = com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(speaker)
    val activeLabel = when {
        hasKey && normSpeaker == "priya" -> "🌸 Priya (Sarvam Cloud HD)"
        hasKey && normSpeaker == "shubh" -> "🎙️ Shubh (Sarvam Cloud HD)"
        hasKey && normSpeaker == "kavitha" -> "🌸 Kavitha (Sarvam Cloud HD)"
        hasKey && normSpeaker == "ratan" -> "🎙️ Ratan (Sarvam Cloud HD)"
        hasKey -> "☁️ Sarvam Voice (${normSpeaker.replaceFirstChar { it.uppercase() }})"
        selectedNativeVoice != null -> "📱 Android Voice (${selectedNativeVoice?.substringAfterLast("-", "Offline")})"
        else -> "📱 Android System Voice (Offline)"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = com.shankaravam.festival.core.theme.TempleSaffron,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Temple Voice / గొంతు ఎంపిక", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = { showKeyDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(if (hasKey) "API Key ✓" else "Set Key", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Dropdown Selector Button
            Box {
                OutlinedButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(activeLabel, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select voice")
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Cloud Section
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("🌸 Priya (Female) • Recommended", fontWeight = FontWeight.SemiBold)
                                Text("Sarvam AI Bulbul v3 • Studio Telugu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            speaker = "priya"
                            prefs.sarvamSpeaker = "priya"
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("🎙️ Shubh (Male) • Recommended", fontWeight = FontWeight.SemiBold)
                                Text("Sarvam AI Bulbul v3 • Studio Telugu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            speaker = "shubh"
                            prefs.sarvamSpeaker = "shubh"
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("🌸 Kavitha (Female)", fontWeight = FontWeight.SemiBold)
                                Text("Sarvam AI Bulbul v3 • Clear Telugu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            speaker = "kavitha"
                            prefs.sarvamSpeaker = "kavitha"
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("🎙️ Ratan (Male)", fontWeight = FontWeight.SemiBold)
                                Text("Sarvam AI Bulbul v3 • Clear Telugu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            speaker = "ratan"
                            prefs.sarvamSpeaker = "ratan"
                            showMenu = false
                        }
                    )

                    HorizontalDivider()

                    // Native Section
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("📱 System Default", fontWeight = FontWeight.SemiBold)
                                Text("Android Built-in • 100% Offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            selectedNativeVoice = null
                            prefs.nativeTtsVoice = null
                            container.ttsEngine.native.setVoiceByName("")
                            showMenu = false
                        }
                    )

                    nativeVoices.forEach { voiceName ->
                        val vLabel = voiceName.substringAfterLast("-", voiceName.takeLast(8))
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("📱 Android Telugu ($vLabel)", fontWeight = FontWeight.SemiBold)
                                    Text("Device voice: $voiceName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                selectedNativeVoice = voiceName
                                prefs.nativeTtsVoice = voiceName
                                container.ttsEngine.native.setVoiceByName(voiceName)
                                showMenu = false
                            }
                        )
                    }
                }
            }

            Text(
                text = if (hasKey) "✓ High-fidelity Sarvam cloud voice active."
                else "Offline Android voice active. Add a Sarvam API key for studio clarity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showKeyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("Sarvam AI API Key", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your Sarvam API subscription key for natural temple announcements.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = keyDraft.trim()
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { container.secureKeys.setSarvamKey(trimmed) }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                storedKey = trimmed
                                showKeyDialog = false
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        keyDraft = ""
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { container.secureKeys.setSarvamKey("") }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                storedKey = ""
                                showKeyDialog = false
                            }
                        }
                    }
                ) { Text("Remove") }
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            if (state.prefetchRemaining > 0) {
                Text(
                    "Preparing cloud audio… (${state.prefetchRemaining} left, offline voice fills gaps)",
                    style = MaterialTheme.typography.bodySmall
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
