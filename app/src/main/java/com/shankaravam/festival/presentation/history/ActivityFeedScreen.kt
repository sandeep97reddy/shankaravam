package com.shankaravam.festival.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.export.ReportContent
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.core.theme.CrimsonRose
import com.shankaravam.festival.core.theme.CrimsonRoseLight
import com.shankaravam.festival.core.theme.EmeraldGreen
import com.shankaravam.festival.core.theme.EmeraldGreenLight
import com.shankaravam.festival.core.theme.RadiantGold
import com.shankaravam.festival.core.theme.RadiantGoldLight
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.ActivityActions
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.Expense
import com.shankaravam.festival.domain.model.SyncStatus
import com.shankaravam.festival.presentation.common.TempleAppBar
import com.shankaravam.festival.presentation.common.containerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class HistoryTypeFilter {
    ALL,
    DONATIONS,
    EXPENSES
}

enum class TransactionType {
    DONATION,
    EXPENSE,
    CORRECTION,
    SYSTEM
}

data class RichTransactionItem(
    val id: String,
    val type: TransactionType,
    val title: String,
    val amountText: String? = null,
    val isPositive: Boolean? = null,
    val subtitle: String? = null,
    val collector: String = "",
    val timestamp: Long = 0L,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

/**
 * Combines ActivityRecords, Donations, and Expenses into a rich, detailed transaction ledger.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityFeedViewModel(container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _typeFilter = MutableStateFlow(HistoryTypeFilter.ALL)
    val typeFilter: StateFlow<HistoryTypeFilter> = _typeFilter

    fun setQuery(q: String) { _query.value = q }
    fun setTypeFilter(f: HistoryTypeFilter) { _typeFilter.value = f }

    val transactions: StateFlow<List<RichTransactionItem>> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    container.activityRepository.observeRecent(eventId, limit = 300),
                    container.donationRepository.observeForEvent(eventId),
                    container.expenseRepository.observeForEvent(eventId),
                    _query,
                    _typeFilter
                ) { activities, donations, expenses, search, filter ->
                    val donationMap = donations.associateBy { it.id }
                    val expenseMap = expenses.associateBy { it.id }

                    // Map activities to rich transaction items
                    val items = activities.mapNotNull { act ->
                        when (act.actionType) {
                            ActivityActions.DONATION_ADDED -> {
                                val donation = act.details?.let { donationMap[it] }
                                val donorName = donation?.donorName ?: "Donation"
                                val amtStr = if (donation?.isNonCash == true) {
                                    donation.itemDescription ?: "Material Item"
                                } else {
                                    formatInr(donation?.amount ?: 0.0)
                                }
                                RichTransactionItem(
                                    id = act.id,
                                    type = TransactionType.DONATION,
                                    title = donorName,
                                    amountText = amtStr,
                                    isPositive = true,
                                    subtitle = "${donation?.paymentMethod ?: "Cash"} • ${donation?.status?.name?.lowercase()?.replace('_', ' ') ?: "received"}",
                                    collector = donation?.addedBy?.ifBlank { act.actorId } ?: act.actorId,
                                    timestamp = act.timestamp,
                                    syncStatus = donation?.syncStatus ?: SyncStatus.PENDING_UPLOAD
                                )
                            }
                            ActivityActions.EXPENSE_ADDED -> {
                                val expense = act.details?.let { expenseMap[it] }
                                val desc = expense?.description ?: "Expense"
                                val amtStr = "- " + formatInr(expense?.amount ?: 0.0)
                                RichTransactionItem(
                                    id = act.id,
                                    type = TransactionType.EXPENSE,
                                    title = desc,
                                    amountText = amtStr,
                                    isPositive = false,
                                    subtitle = "${expense?.category ?: "General"} • ${expense?.paymentMethod ?: "Cash"}",
                                    collector = expense?.addedBy?.ifBlank { act.actorId } ?: act.actorId,
                                    timestamp = act.timestamp,
                                    syncStatus = expense?.syncStatus ?: SyncStatus.PENDING_UPLOAD
                                )
                            }
                            ActivityActions.CORRECTION_ADDED -> {
                                RichTransactionItem(
                                    id = act.id,
                                    type = TransactionType.CORRECTION,
                                    title = "Correction Record",
                                    amountText = null,
                                    subtitle = act.details ?: "Correction recorded",
                                    collector = act.actorId,
                                    timestamp = act.timestamp,
                                    syncStatus = SyncStatus.LOCAL_ONLY
                                )
                            }
                            ActivityActions.EVENT_CREATED -> {
                                RichTransactionItem(
                                    id = act.id,
                                    type = TransactionType.SYSTEM,
                                    title = "Event Created: ${act.details ?: ""}",
                                    subtitle = "Festival launched offline",
                                    collector = act.actorId,
                                    timestamp = act.timestamp,
                                    syncStatus = SyncStatus.LOCAL_ONLY
                                )
                            }
                            ActivityActions.RECORD_CANCELLED -> {
                                RichTransactionItem(
                                    id = act.id,
                                    type = TransactionType.CORRECTION,
                                    title = "Record Cancelled",
                                    subtitle = act.details ?: "Voided",
                                    collector = act.actorId,
                                    timestamp = act.timestamp,
                                    syncStatus = SyncStatus.LOCAL_ONLY
                                )
                            }
                            else -> null
                        }
                    }

                    // Also include donations/expenses that might not have an activity record
                    val activityDonationIds = activities.filter { it.actionType == ActivityActions.DONATION_ADDED }.mapNotNull { it.details }.toSet()
                    val orphanDonations = donations.filter { it.id !in activityDonationIds }.map { d ->
                        RichTransactionItem(
                            id = d.id,
                            type = TransactionType.DONATION,
                            title = d.donorName,
                            amountText = if (d.isNonCash) (d.itemDescription ?: "Item") else formatInr(d.amount),
                            isPositive = true,
                            subtitle = "${d.paymentMethod} • ${d.status.name.lowercase()}",
                            collector = d.addedBy,
                            timestamp = d.createdAt,
                            syncStatus = d.syncStatus
                        )
                    }

                    val activityExpenseIds = activities.filter { it.actionType == ActivityActions.EXPENSE_ADDED }.mapNotNull { it.details }.toSet()
                    val orphanExpenses = expenses.filter { it.id !in activityExpenseIds }.map { e ->
                        RichTransactionItem(
                            id = e.id,
                            type = TransactionType.EXPENSE,
                            title = e.description,
                            amountText = "- " + formatInr(e.amount),
                            isPositive = false,
                            subtitle = "${e.category} • ${e.paymentMethod}",
                            collector = e.addedBy,
                            timestamp = e.createdAt,
                            syncStatus = e.syncStatus
                        )
                    }

                    val combined = (items + orphanDonations + orphanExpenses).sortedByDescending { it.timestamp }

                    // Apply filters & search query
                    combined.filter { item ->
                        val matchesType = when (filter) {
                            HistoryTypeFilter.ALL -> true
                            HistoryTypeFilter.DONATIONS -> item.type == TransactionType.DONATION
                            HistoryTypeFilter.EXPENSES -> item.type == TransactionType.EXPENSE
                        }
                        val q = search.trim().lowercase()
                        val matchesSearch = q.isEmpty() ||
                            item.title.lowercase().contains(q) ||
                            item.collector.lowercase().contains(q) ||
                            (item.subtitle?.lowercase()?.contains(q) == true)

                        matchesType && matchesSearch
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun ActivityFeedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityFeedViewModel = containerViewModel { ActivityFeedViewModel(it) }
) {
    val items by viewModel.transactions.collectAsState()
    val query by viewModel.query.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val strings = appStrings()

    Scaffold(
        modifier = modifier,
        topBar = {
            TempleAppBar(
                title = "${strings.historyTitle} (${items.size})",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = { Text(strings.searchHistoryPlaceholder, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TempleSaffron) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = TempleSaffron,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Filter Tabs (All, Donations, Expenses)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = typeFilter == HistoryTypeFilter.ALL,
                    onClick = { viewModel.setTypeFilter(HistoryTypeFilter.ALL) },
                    label = { Text(strings.filterAll, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = typeFilter == HistoryTypeFilter.DONATIONS,
                    onClick = { viewModel.setTypeFilter(HistoryTypeFilter.DONATIONS) },
                    label = { Text(strings.filterDonations, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreenLight,
                        selectedLabelColor = EmeraldGreen
                    )
                )
                FilterChip(
                    selected = typeFilter == HistoryTypeFilter.EXPENSES,
                    onClick = { viewModel.setTypeFilter(HistoryTypeFilter.EXPENSES) },
                    label = { Text(strings.filterExpenses, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrimsonRoseLight,
                        selectedLabelColor = CrimsonRose
                    )
                )
            }

            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        strings.noHistoryFound,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = items, key = { it.id }) { item ->
                        RichTransactionCard(
                            item = item,
                            collectorLabel = strings.collectorLabel,
                            syncedText = strings.statusSynced,
                            pendingSyncText = strings.statusPendingSync,
                            localOnlyText = strings.statusLocalOnly,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Rich Transaction Card displaying:
 * - Transaction icon & type
 * - Donor / Vendor name
 * - Formatted Amount (+ ₹5,000 / - ₹1,200)
 * - Collector / Added By
 * - Date & Time
 * - Sync Status Badge (Synced, Pending Sync, Local Only)
 */
@Composable
fun RichTransactionCard(
    item: RichTransactionItem,
    collectorLabel: String,
    syncedText: String,
    pendingSyncText: String,
    localOnlyText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Icon + Title + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular Type Icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            when (item.type) {
                                TransactionType.DONATION -> EmeraldGreenLight
                                TransactionType.EXPENSE -> CrimsonRoseLight
                                TransactionType.CORRECTION -> RadiantGoldLight
                                TransactionType.SYSTEM -> TempleSaffron.copy(alpha = 0.12f)
                            }
                        )
                ) {
                    Icon(
                        imageVector = when (item.type) {
                            TransactionType.DONATION -> Icons.Filled.ArrowUpward
                            TransactionType.EXPENSE -> Icons.Filled.ArrowDownward
                            TransactionType.CORRECTION -> Icons.Filled.EditNote
                            TransactionType.SYSTEM -> Icons.Filled.Celebration
                        },
                        contentDescription = null,
                        tint = when (item.type) {
                            TransactionType.DONATION -> EmeraldGreen
                            TransactionType.EXPENSE -> CrimsonRose
                            TransactionType.CORRECTION -> RadiantGold
                            TransactionType.SYSTEM -> TempleSaffron
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Name / Description & Subtitle
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    item.subtitle?.let { sub ->
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // Amount
                item.amountText?.let { amt ->
                    Text(
                        text = amt,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = when (item.isPositive) {
                            true -> EmeraldGreen
                            false -> CrimsonRose
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            // Bottom Row: Collector & Time on left, Sync Status Badge on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.collector.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$collectorLabel: ${item.collector}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", color = MaterialTheme.colorScheme.outline)
                    }
                    Text(
                        text = ReportContent.formatTime(item.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sync Status Badge
                SyncStatusBadge(
                    status = item.syncStatus,
                    syncedText = syncedText,
                    pendingSyncText = pendingSyncText,
                    localOnlyText = localOnlyText
                )
            }
        }
    }
}

@Composable
private fun SyncStatusBadge(
    status: SyncStatus,
    syncedText: String,
    pendingSyncText: String,
    localOnlyText: String
) {
    val isSynced = status == SyncStatus.SYNCED
    val isPending = status == SyncStatus.PENDING_UPLOAD

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSynced -> EmeraldGreenLight
                    isPending -> RadiantGoldLight
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = when {
                isSynced -> Icons.Filled.CloudDone
                isPending -> Icons.Filled.CloudUpload
                else -> Icons.Filled.CloudOff
            },
            contentDescription = null,
            tint = when {
                isSynced -> EmeraldGreen
                isPending -> RadiantGold
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = when {
                isSynced -> syncedText
                isPending -> pendingSyncText
                else -> localOnlyText
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = when {
                isSynced -> EmeraldGreen
                isPending -> RadiantGold
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 11.sp
        )
    }
}
