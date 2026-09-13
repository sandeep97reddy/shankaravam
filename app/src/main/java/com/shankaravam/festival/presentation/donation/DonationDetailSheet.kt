package com.shankaravam.festival.presentation.donation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.shankaravam.festival.core.tts.AnnouncementLanguage
import com.shankaravam.festival.core.util.buildWhatsAppReceipt
import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.presentation.common.containerViewModel
import com.shankaravam.festival.presentation.common.rememberContainer
import com.shankaravam.festival.presentation.common.shareAudioViaApps
import com.shankaravam.festival.presentation.common.shareTextViaWhatsApp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Donation detail sheet (plan §14): full details, announcement preview text,
 * correction history. Edit lives behind the grace-window/correction flow (G5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationDetailSheet(
    donation: Donation,
    eventName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    donation.donorName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(donation.status)
            }
            Text(
                if (donation.isNonCash) {
                    listOfNotNull(
                        donation.quantity?.let { q ->
                            if (!q.isFinite()) null
                            else (if (q % 1.0 == 0.0) q.toLong().toString() else q.toString())
                        },
                        donation.unit,
                        donation.itemDescription
                    ).joinToString(" ").ifEmpty { "Item donation" }
                } else {
                    formatInr(donation.amount)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            DetailRow("Payment", donation.paymentMethod)
            donation.pronunciationText?.let { DetailRow("Pronunciation", it) }
            if (donation.tags.isNotEmpty()) DetailRow("Tags", donation.tags.joinToString(", "))
            DetailRow("Added by", donation.addedBy.ifBlank { "—" })
            DetailRow("Added", formatTime(donation.addedTime))
            donation.notes?.let { DetailRow("Notes", it) }

            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Announcement preview",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        runCatching { buildAnnouncementPreview(donation, eventName.ifBlank { "ఉత్సవం" }) }
                            .getOrDefault("పరీక్ష. ఆడియో సరిగ్గా పనిచేస్తోంది."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            CorrectionHistory(donationId = donation.id)

            SinglePlayButton(donation = donation, eventName = eventName)

            ShareAudioButton(donation = donation)

            ImportAudioButton(donation = donation)

            WhatsAppReceiptButton(donation = donation, eventName = eventName)

            CorrectEntryButton(donation = donation)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * Amount fix through the shared correction flow: direct edit inside the
 * 5-minute window, appended Correction with mandatory reason after it.
 */
@Composable
private fun CorrectEntryButton(donation: Donation) {
    // CANCELLED rows are terminal — no further edits.
    if (donation.status == com.shankaravam.festival.domain.model.DonationStatus.CANCELLED) return
    var showDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val viewModel: DonationDetailViewModel =
        containerViewModel { DonationDetailViewModel(it, donation.id) }

    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Fix amount")
    }
    if (showDialog) {
        com.shankaravam.festival.presentation.correction.CorrectDialog(
            title = "Fix donation",
            originalAmount = donation.amount,
            addedTimeMillis = donation.addedTime,
            onDismiss = { showDialog = false; error = null },
            onConfirm = { newAmount, reason ->
                scope.launch {
                    when (
                        val result = viewModel.correct(donation, newAmount, reason)
                    ) {
                        is com.shankaravam.festival.core.util.Outcome.Ok -> {
                            showDialog = false
                            error = null
                        }
                        is com.shankaravam.festival.core.util.Outcome.Err -> {
                            error = result.message
                        }
                    }
                }
            }
        )
    }
}

/**
 * Instant single-row playback through the DualTtsEngine (cached cloud mp3 when
 * present, native Telugu otherwise). Stops when the sheet goes away.
 */
@Composable
private fun SinglePlayButton(donation: Donation, eventName: String) {
    val container = rememberContainer()
    var playing by remember(donation.id) { mutableStateOf(false) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val nativeReady by container.ttsEngine.nativeReady.collectAsState(initial = false)
    DisposableEffect(donation.id) {
        onDispose { runCatching { container.ttsEngine.stopAll() } }
    }
    val language = when (container.sessionPrefs.queueLanguage) {
        SessionPrefs.LANG_ENGLISH -> AnnouncementLanguage.ENGLISH
        SessionPrefs.LANG_BILINGUAL -> AnnouncementLanguage.BILINGUAL
        else -> AnnouncementLanguage.TELUGU
    }
    Button(
        onClick = {
            runCatching {
                if (playing) {
                    container.ttsEngine.stopAll()
                    playing = false
                } else {
                    playing = true
                    container.ttsEngine.playBest(
                        donation,
                        eventName,
                        language,
                        onDone = { mainHandler.post { playing = false } },
                        onError = { mainHandler.post { playing = false } }
                    )
                }
            }.onFailure { mainHandler.post { playing = false } }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (playing) "Stop preview / ఆపు" else "Play announcement / వినిపించు")
    }
}

/**
 * 1-tap WhatsApp digital receipt (feature #5): the record's own attribution
 * ([Donation.addedBy]) is the counter truth — not whoever happens to hold
 * the phone when sharing.
 */
@Composable
private fun WhatsAppReceiptButton(donation: Donation, eventName: String) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            shareTextViaWhatsApp(
                context,
                buildWhatsAppReceipt(donation, eventName, donation.addedBy)
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Share receipt on WhatsApp / వాట్సాప్ రసీదు")
    }
}

/**
 * 1-tap Audio Announcement share to WhatsApp and other apps via Android FileProvider.
 */
@Composable
private fun ShareAudioButton(
    donation: Donation,
    viewModel: DonationDetailViewModel = containerViewModel { DonationDetailViewModel(it, donation.id) }
) {
    val context = LocalContext.current
    val container = rememberContainer()
    // Reactive on the live row: appears the moment background TTS marks READY.
    // Phase 1: speaker-aware lookup first, human-import slots as fallback so a
    // WhatsApp clip shared under any voice is still shareable.
    // Fix-B2: human clips come first — share exactly what playback plays.
    val liveRow by viewModel.donation.collectAsState()
    val audioTick = liveRow?.audioStatus
    val speaker = container.sessionPrefs.sarvamSpeaker
    val audioFile = remember(donation.id, audioTick, speaker) {
        container.ttsEngine.importedFile(donation.id)
            ?: container.ttsEngine.cachedFile(donation.id, speaker = speaker)
            ?: container.ttsEngine.importedFile(donation.id, roster = true)
            ?: container.ttsEngine.cachedFile(donation.id, roster = true, speaker = speaker)
    }
    if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
        OutlinedButton(
            onClick = {
                shareAudioViaApps(
                    context,
                    audioFile,
                    "Telugu Announcement - ${donation.donorName}"
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Share Audio / ఆడియో షేర్ చేయండి")
        }
    }
}

/**
 * Shared-clip import (P4): picks one audio file (e.g. WhatsApp share —
 * human-recorded or Sarvam, bytes are bytes) into this row's human full-clip
 * slot (`donation_{id}.mp3`). Phase 1: the engine plays this slot as an
 * intentional override under any speaker, after the Sarvam speaker file.
 * Roster mode falls back to it when no roster clip exists. Never throws.
 */
@Composable
private fun ImportAudioButton(donation: Donation) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var importing by remember(donation.id) { mutableStateOf(false) }
    var error by remember(donation.id) { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        error = null
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val failure = runCatching {
                val dir = java.io.File(container.appContext.cacheDir, "audio")
                    .apply { if (!exists()) mkdirs() }
                val dest = java.io.File(dir, "donation_${donation.id}.mp3")
                container.appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: throw java.io.IOException("Could not read that file")
                if (dest.length() == 0L) throw java.io.IOException("Empty audio file")
                if (dest.length() > com.shankaravam.festival.data.local.SessionPrefs.AUDIO_IMPORT_MAX_BYTES) {
                    runCatching { dest.delete() }
                    throw java.io.IOException("File too large (5 MB max)")
                }
                container.donationRepository.updateAudioStatus(
                    donation.id,
                    com.shankaravam.festival.domain.model.AudioStatus.READY
                )
            }.exceptionOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                importing = false
                error = failure?.message
            }
        }
    }
    OutlinedButton(
        onClick = { picker.launch("audio/*") },
        enabled = !importing,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (importing) "Importing…" else "Import shared audio")
    }
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CorrectionHistory(
    donationId: String,
    viewModel: DonationDetailViewModel = containerViewModel { DonationDetailViewModel(it, donationId) }
) {
    val corrections by viewModel.corrections.collectAsState()
    if (corrections.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Corrections (${corrections.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        corrections.forEach { c ->
            Text(
                "${formatInr(c.originalAmount)} → ${formatInr(c.effectiveAmount)} — ${c.reason}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
