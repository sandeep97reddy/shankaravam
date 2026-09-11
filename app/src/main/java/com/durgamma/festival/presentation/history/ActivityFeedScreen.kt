package com.durgamma.festival.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durgamma.festival.core.export.ReportContent
import com.durgamma.festival.core.theme.DeepMaroon
import com.durgamma.festival.core.theme.TempleGold
import com.durgamma.festival.di.AppContainer
import com.durgamma.festival.domain.model.ActivityActions
import com.durgamma.festival.domain.model.ActivityRecord
import com.durgamma.festival.presentation.common.containerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Small screen — ViewModel lives here to keep the history feature in one file. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityFeedViewModel(container: AppContainer) : ViewModel() {
    val entries: StateFlow<List<ActivityRecord>> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) flowOf(emptyList())
            else container.activityRepository.observeRecent(eventId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityFeedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityFeedViewModel = containerViewModel { ActivityFeedViewModel(it) }
) {
    val entries by viewModel.entries.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("History (${entries.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DeepMaroon,
                    titleContentColor = TempleGold,
                    navigationIconContentColor = TempleGold
                )
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Nothing yet — donations, expenses, corrections and cancels appear here.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = entries, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth().animateItem()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                actionLabel(entry.actionType),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (entry.actorId.isNotBlank()) {
                                Text(
                                    "by ${entry.actorId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            ReportContent.formatTime(entry.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun actionLabel(action: String): String = when (action) {
    ActivityActions.EVENT_CREATED -> "Event created"
    ActivityActions.EVENT_CLOSED -> "Event closed"
    ActivityActions.DONATION_ADDED -> "Donation added"
    ActivityActions.EXPENSE_ADDED -> "Expense added"
    ActivityActions.CORRECTION_ADDED -> "Correction recorded"
    ActivityActions.RECORD_CANCELLED -> "Record cancelled"
    ActivityActions.STATUS_CHANGED -> "Record edited"
    else -> action.replace('_', ' ')
}
