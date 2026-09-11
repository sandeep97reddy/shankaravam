package com.shankaravam.festival.presentation.announcement

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.shankaravam.festival.core.audio.displayName
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.presentation.common.containerViewModel
import com.shankaravam.festival.presentation.common.rememberContainer
import com.shankaravam.festival.presentation.donation.DonationCard

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
                TransportCard(
                    state = state,
                    onPlay = { viewModel.play() },
                    onPause = { viewModel.pause() },
                    onStop = { viewModel.stop() },
                    onNext = { viewModel.next() },
                    onPrevious = { viewModel.previous() },
                    onReplay = { viewModel.replay() },
                    onToggleRepeat = { viewModel.toggleRepeat() },
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
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(routeName, fontWeight = FontWeight.SemiBold)
                Text(
                    if (nativeReady) "Telugu voice ready • offline" else "Loading Telugu voice…",
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
    var key by remember { mutableStateOf(prefs.sarvamApiKey) }
    var speaker by remember { mutableStateOf(prefs.sarvamSpeaker) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; prefs.sarvamApiKey = it },
                label = { Text("Sarvam API key (optional)") },
                placeholder = { Text("Empty = offline voice") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("meera", "arvind").forEach { option ->
                    FilterChip(
                        selected = speaker == option,
                        onClick = { speaker = option; prefs.sarvamSpeaker = option },
                        label = { Text(option.replaceFirstChar { it.titlecase() }) }
                    )
                }
            }
            Text(
                "Cloud audio is cached on this device only — never uploaded. Key moves to secure storage in G6.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportCard(
    state: QueueUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReplay: () -> Unit,
    onToggleRepeat: () -> Unit,
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
            Text("Pause between announcements", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10).forEach { gap ->
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
