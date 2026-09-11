package com.shankaravam.festival.presentation.donation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.SyncStatus
import com.shankaravam.festival.presentation.common.containerViewModel

/**
 * Donation ledger list (plan §14–§15). Keyed LazyColumn + animateItem for
 * zero-jank rapid counter inserts; filter chips always show active state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DonationListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DonationListViewModel = containerViewModel { DonationListViewModel(it) }
) {
    val state by viewModel.uiState.collectAsState()
    var showSort by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val eventVm: com.shankaravam.festival.presentation.event.EventViewModel =
        containerViewModel { c -> com.shankaravam.festival.presentation.event.EventViewModel(c, c.sessionPrefs) }
    val eventState by eventVm.uiState.collectAsState()
    val eventName = eventState.currentEvent?.name ?: ""

    Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("Donations (${state.donations.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = com.shankaravam.festival.core.theme.DeepMaroon,
                    titleContentColor = com.shankaravam.festival.core.theme.TempleGold,
                    navigationIconContentColor = com.shankaravam.festival.core.theme.TempleGold
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                label = { Text("Search donor / item") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusFilterChip(state.statusFilter, onSelect = { viewModel.setStatus(it) })
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Sort")
                }
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    sortLabel(SessionPrefs.SORT_NEWEST).let { label ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            viewModel.setSort(SessionPrefs.SORT_NEWEST); showSort = false
                        })
                    }
                    DropdownMenuItem(text = { Text(sortLabel(SessionPrefs.SORT_OLDEST)) }, onClick = {
                        viewModel.setSort(SessionPrefs.SORT_OLDEST); showSort = false
                    })
                    DropdownMenuItem(text = { Text(sortLabel(SessionPrefs.SORT_AMOUNT_DESC)) }, onClick = {
                        viewModel.setSort(SessionPrefs.SORT_AMOUNT_DESC); showSort = false
                    })
                    DropdownMenuItem(text = { Text(sortLabel(SessionPrefs.SORT_AMOUNT_ASC)) }, onClick = {
                        viewModel.setSort(SessionPrefs.SORT_AMOUNT_ASC); showSort = false
                    })
                    DropdownMenuItem(text = { Text(sortLabel(SessionPrefs.SORT_NAME_ASC)) }, onClick = {
                        viewModel.setSort(SessionPrefs.SORT_NAME_ASC); showSort = false
                    })
                }
                if (state.statusFilter != null || state.tagFilter != null || state.query.isNotBlank()) {
                    IconButton(onClick = { viewModel.clearFilters() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear filters")
                    }
                }
            }
            if (state.availableTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.availableTags.take(12).forEach { tag ->
                        FilterChip(
                            selected = state.tagFilter == tag,
                            onClick = { viewModel.setTag(if (state.tagFilter == tag) null else tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }
            if (!state.hasEvent) {
                EmptyHint("Select or create an event to see donations.")
            } else if (state.donations.isEmpty()) {
                EmptyHint(
                    if (state.totalCount == 0) "No donations yet — tap + Donation on the dashboard."
                    else "No donations match the active filters."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = state.donations, key = { it.id }) { donation ->
                        DonationCard(
                            donation = donation,
                            modifier = Modifier.animateItem(),
                            onClick = { selectedId = donation.id }
                        )
                    }
                }
            }
        }
    }

    selectedId?.let { id ->
        state.donations.find { it.id == id }?.let { donation ->
            DonationDetailSheet(
                donation = donation,
                eventName = eventName,
                onDismiss = { selectedId = null }
            )
        } ?: run { selectedId = null }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun sortLabel(key: String): String = when (key) {
    SessionPrefs.SORT_OLDEST -> "Oldest first"
    SessionPrefs.SORT_AMOUNT_DESC -> "Amount: high to low"
    SessionPrefs.SORT_AMOUNT_ASC -> "Amount: low to high"
    SessionPrefs.SORT_NAME_ASC -> "Donor name A–Z"
    else -> "Newest first"
}

@Composable
private fun StatusFilterChip(
    selected: DonationStatus?,
    onSelect: (DonationStatus?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { expanded = true },
        label = { Text(selected?.name?.lowercase()?.replace('_', ' ') ?: "All statuses") },
        trailingIcon = {
            Icon(Icons.Filled.FilterList, contentDescription = null)
        }
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("All statuses") }, onClick = {
            onSelect(null); expanded = false
        })
        DonationStatus.entries.forEach { status ->
            DropdownMenuItem(
                text = { Text(status.name.lowercase().replace('_', ' ')) },
                onClick = { onSelect(status); expanded = false }
            )
        }
    }
}

@Composable
fun DonationCard(
    donation: Donation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    donation.donorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (donation.isNonCash) {
                        donation.itemDescription ?: "Item"
                    } else {
                        formatInr(donation.amount)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(donation.status)
                if (donation.isNonCash) {
                    AssistChip(onClick = {}, label = { Text("non-cash") })
                }
                Spacer(Modifier.weight(1f))
                SyncIcon(donation.syncStatus)
                AudioIcon(donation.audioStatus)
            }
            if (donation.tags.isNotEmpty()) {
                Text(
                    donation.tags.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: DonationStatus) {
    val (label, color) = when (status) {
        DonationStatus.PLEDGED -> "Pledged" to Color(0xFF8D6E00)
        DonationStatus.PARTIALLY_RECEIVED -> "Partial" to Color(0xFFE65100)
        DonationStatus.RECEIVED -> "Received" to Color(0xFF1565C0)
        DonationStatus.CONFIRMED -> "Confirmed" to Color(0xFF2E7D32)
        DonationStatus.CANCELLED -> "Cancelled" to Color(0xFFC62828)
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
}

@Composable
private fun SyncIcon(status: SyncStatus) {
    val synced = status == SyncStatus.SYNCED
    Icon(
        if (synced) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
        contentDescription = if (synced) "Synced" else "Local only",
        tint = if (synced) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AudioIcon(status: AudioStatus) {
    Icon(
        when (status) {
            AudioStatus.READY -> Icons.Filled.Check
            AudioStatus.PREPARING -> Icons.Filled.HourglassEmpty
            else -> Icons.Filled.HourglassEmpty
        },
        contentDescription = "Audio: ${status.name.lowercase()}",
        tint = if (status == AudioStatus.READY) Color(0xFF2E7D32)
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
