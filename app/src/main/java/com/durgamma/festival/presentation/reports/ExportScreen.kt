package com.durgamma.festival.presentation.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durgamma.festival.core.export.ReportContent
import com.durgamma.festival.core.theme.DeepMaroon
import com.durgamma.festival.core.theme.TempleGold
import com.durgamma.festival.core.util.formatInr
import com.durgamma.festival.di.AppContainer
import com.durgamma.festival.domain.model.Correction
import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.Event
import com.durgamma.festival.domain.model.Expense
import com.durgamma.festival.domain.usecase.BalanceSnapshot
import com.durgamma.festival.domain.usecase.calculateBalance
import com.durgamma.festival.presentation.common.containerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Small screen — ViewModel lives here; heavy rendering stays in ReportExporter (IO). */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModel(container: AppContainer) : ViewModel() {

    data class ExportData(
        val event: Event? = null,
        val donations: List<Donation> = emptyList(),
        val expenses: List<Expense> = emptyList(),
        val corrections: List<Correction> = emptyList()
    ) {
        val totals: BalanceSnapshot get() = calculateBalance(donations, expenses)
    }

    val data: StateFlow<ExportData> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(ExportData())
            } else {
                combine(
                    container.eventRepository.observeEvent(eventId),
                    container.donationRepository.observeForEvent(eventId),
                    container.expenseRepository.observeForEvent(eventId)
                ) { event: Event?, donations: List<Donation>, expenses: List<Expense> ->
                    ExportData(event, donations, expenses, emptyList())
                }.combine(container.correctionRepository.observeForEvent(eventId)) { base, corrections ->
                    base.copy(corrections = corrections)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExportData())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = containerViewModel { ExportViewModel(it) }
) {
    val state by viewModel.data.collectAsState()
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf<String?>(null) }
    var lastFile by remember { mutableStateOf<File?>(null) }
    val exporter = rememberExporter()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Reports & sharing") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val event = state.event
            if (event == null) {
                Text("Select or create an event to export.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Donations: ${state.donations.size} • Expenses: ${state.expenses.size} • Corrections: ${state.corrections.size}")
                    Text(
                        "Balance: ${formatInr(state.totals.balance)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            ExportAction(
                title = "PDF summary",
                subtitle = "Totals + all rows, saved on this device, then shared.",
                working = working == "pdf",
                onClick = {
                    scope.launch {
                        working = "pdf"
                        runCatching {
                            val file = exporter.exportPdf(
                                event.name, event.templeName.ifBlank { event.name },
                                state.donations, state.expenses, state.corrections
                            )
                            lastFile = file
                            exporter.shareFile(file, "application/pdf")
                        }
                        working = null
                    }
                }
            )
            ExportAction(
                title = "Donations CSV (Excel)",
                subtitle = "Opens in Excel / Sheets for the committee audit.",
                working = working == "csv-d",
                onClick = {
                    scope.launch {
                        working = "csv-d"
                        runCatching {
                            val file = exporter.exportDonationsCsv(state.donations)
                            lastFile = file
                            exporter.shareFile(file, "text/csv")
                        }
                        working = null
                    }
                }
            )
            ExportAction(
                title = "Expenses CSV (Excel)",
                subtitle = "Every expense with category, vendor and status.",
                working = working == "csv-x",
                onClick = {
                    scope.launch {
                        working = "csv-x"
                        runCatching {
                            val file = exporter.exportExpensesCsv(state.expenses)
                            lastFile = file
                            exporter.shareFile(file, "text/csv")
                        }
                        working = null
                    }
                }
            )
            ExportAction(
                title = "WhatsApp summary",
                subtitle = "Short Telugu + English totals text.",
                working = false,
                primary = false,
                onClick = {
                    exporter.shareText(
                        ReportContent.whatsAppSummary(
                            eventName = event.name,
                            templeName = event.templeName.ifBlank { event.name },
                            totals = state.totals,
                            corrections = state.corrections,
                            cashCollectedText = formatInr(state.totals.cashCollected),
                            expenseTotalText = formatInr(state.totals.expenseTotal),
                            balanceText = formatInr(state.totals.balance),
                            pledgedText = formatInr(state.totals.pledgedTotal)
                        )
                    )
                }
            )
            lastFile?.let {
                Text(
                    "Last file: ${it.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "All reports generate fully offline. Nothing is uploaded anywhere.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun rememberExporter(): com.durgamma.festival.core.export.ReportExporter {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) { com.durgamma.festival.core.export.ReportExporter(context) }
}

@Composable
private fun ExportAction(
    title: String,
    subtitle: String,
    working: Boolean,
    onClick: () -> Unit,
    primary: Boolean = true
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            if (primary) {
                Button(onClick = onClick, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                    if (working) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(title)
                }
            } else {
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(title)
                }
            }
        }
    }
}
