package com.durgamma.festival.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durgamma.festival.core.util.formatInr
import com.durgamma.festival.domain.usecase.BalanceSnapshot
import com.durgamma.festival.presentation.common.TempleAppBar
import com.durgamma.festival.presentation.common.containerViewModel
import com.durgamma.festival.presentation.common.derivedTotal
import com.durgamma.festival.presentation.event.CurrentEventBanner

/**
 * Live event dashboard (plan §19). Room is the source of truth; totals arrive
 * via BalanceSnapshot Flow. derivedStateOf keeps big-number formatting cheap.
 */
@Composable
fun DashboardScreen(
    onAddDonation: () -> Unit,
    onViewDonations: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = containerViewModel { DashboardViewModel(it) }
) {
    val state by viewModel.uiState.collectAsState()
    val unsynced by viewModel.unsyncedCount.collectAsState()

    LaunchedEffect(state.event?.id) {
        if (state.event != null) viewModel.refreshUnsynced()
    }

    // Cheap memoized strings — recompute only when totals change.
    val collectedText by derivedTotal(state.totals) { formatInr(state.totals.cashCollected) }
    val balanceText by derivedTotal(state.totals) { formatInr(state.totals.balance) }
    val pledgedText by derivedTotal(state.totals) { formatInr(state.totals.pledgedTotal) }
    val expenseText by derivedTotal(state.totals) { formatInr(state.totals.expenseTotal) }

    Scaffold(
        modifier = modifier,
        topBar = { TempleAppBar(title = "దుర్గమ్మ ఉత్సవాలు") },
        floatingActionButton = {
            if (state.event != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddDonation,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Donation") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CurrentEventBanner()

            if (state.event == null) {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Create your first event to begin",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap New above — e.g. Vinayaka Chavithi 2026. Everything works offline.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                TotalsGrid(state.totals, collectedText, balanceText, pledgedText, expenseText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("${state.totals.donorCount} donors") })
                    AssistChip(onClick = {}, label = { Text("${state.totals.nonCashCount} non-cash") })
                    if (unsynced > 0) {
                        AssistChip(onClick = {}, label = { Text("$unsynced unsynced") })
                    }
                }
                Button(onClick = onViewDonations, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    Text("View donations")
                }
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Announcements (G4)  •  Expenses (G5)")
                }
            }
        }
    }
}

@Composable
private fun TotalsGrid(
    totals: BalanceSnapshot,
    collected: String,
    balance: String,
    pledged: String,
    expenses: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalCard("Collected", collected, Modifier.weight(1f))
        TotalCard("Balance", balance, Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalCard("Pledged", pledged, Modifier.weight(1f))
        TotalCard("Expenses", expenses, Modifier.weight(1f))
    }
    if (totals.balance < 0) {
        Text(
            "Expenses exceed collections — review before spending more.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun TotalCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
