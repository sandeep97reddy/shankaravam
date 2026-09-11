package com.durgamma.festival.presentation.donation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durgamma.festival.core.util.formatInr
import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.presentation.common.containerViewModel
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
                            (if (q % 1.0 == 0.0) q.toLong().toString() else q.toString())
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
                        buildAnnouncementPreview(donation, eventName.ifBlank { "ఉత్సవం" }),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            CorrectionHistory(donationId = donation.id)

            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Play announcement (G4)  •  Correct (G5)")
            }
            Spacer(Modifier.height(4.dp))
        }
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
