package com.shankaravam.festival.presentation.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.export.ReportContent
import com.shankaravam.festival.core.theme.CrimsonRose
import com.shankaravam.festival.core.theme.CrimsonWash
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.EmeraldGreen
import com.shankaravam.festival.core.theme.EmeraldWash
import com.shankaravam.festival.core.theme.GoldWash
import com.shankaravam.festival.core.theme.MaroonWash
import com.shankaravam.festival.core.theme.RadiantGold
import com.shankaravam.festival.core.theme.SaffronWash
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.model.Expense
import com.shankaravam.festival.domain.usecase.BalanceSnapshot
import com.shankaravam.festival.domain.usecase.calculateBalance
import com.shankaravam.festival.presentation.common.containerViewModel
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
        // T0.2: on-screen totals are effective (post-correction) figures; the
        // audit trail below still lists original → effective per row.
        val totals: BalanceSnapshot get() = calculateBalance(
            donations,
            expenses,
            com.shankaravam.festival.domain.model.groupCorrectionsByTarget(corrections)
        )
    }

    val currentLang: StateFlow<String> = container.sessionPrefs.appLanguage

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
    val currentLang by viewModel.currentLang.collectAsState()
    val isTelugu = currentLang == SessionPrefs.LANG_TELUGU
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf<String?>(null) }
    var lastFile by remember { mutableStateOf<File?>(null) }
    var showWhatsAppPreview by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val exporter = rememberExporter()

    Scaffold(
        modifier = modifier,
        topBar = {
            com.shankaravam.festival.presentation.common.TempleAppBar(
                title = if (isTelugu) "నివేదికలు & భాగస్వామ్యం" else "Reports & Sharing",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val event = state.event
            if (event == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(32.dp)
                ) {
                    Text(
                        if (isTelugu) "నివేదికల కోసం ఒక ఈవెంట్‌ను ఎంచుకోండి లేదా సృష్టించండి." else "Select or create an event to export reports.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            // 1. Executive Financial Summary Hero Card
            ExecutiveFinancialSummaryCard(
                event = event,
                totals = state.totals,
                donationCount = state.donations.size,
                expenseCount = state.expenses.size,
                correctionCount = state.corrections.size,
                isTelugu = isTelugu
            )

            Text(
                text = if (isTelugu) "ఎగుమతి & భాగస్వామ్య ఎంపికలు" else "Export & Share Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )

            // 2. Action Card: PDF Financial Statement
            ModernReportActionCard(
                title = if (isTelugu) "పూర్తి పీడీఎఫ్ నివేదిక (PDF)" else "PDF Financial Statement",
                subtitle = if (isTelugu)
                    "మొత్తాలు, దాతల జాబితా మరియు ఖర్చుల సమగ్ర నివేదిక. ముద్రణ మరియు కమిటీ ఆడిట్ కోసం సిద్ధం."
                else
                    "Executive totals, full donor roster, and categorized expenses. Ready for print & committee audit.",
                icon = Icons.Filled.Description,
                iconTint = CrimsonRose,
                iconBackground = CrimsonWash,
                buttonText = if (isTelugu) "పీడీఎఫ్ రూపొందించి షేర్ చేయండి" else "Generate & Share PDF",
                working = working == "pdf",
                onAction = {
                    scope.launch {
                        working = "pdf"
                        runCatching {
                            val file = exporter.exportPdf(
                                event.name,
                                event.templeName.ifBlank { event.name },
                                state.donations,
                                state.expenses,
                                state.corrections
                            )
                            lastFile = file
                            exporter.shareFile(file, "application/pdf")
                        }
                        working = null
                    }
                }
            )

            // 3. Action Card: Excel / CSV Spreadsheets
            ExcelSpreadsheetsCard(
                isTelugu = isTelugu,
                working = working,
                onExportDonations = {
                    scope.launch {
                        working = "csv-d"
                        runCatching {
                            val file = exporter.exportDonationsCsv(state.donations)
                            lastFile = file
                            exporter.shareFile(file, "text/csv")
                        }
                        working = null
                    }
                },
                onExportExpenses = {
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

            // 4. Action Card: WhatsApp Broadcast with Collapsible Preview
            val whatsAppText = remember(event, state.totals, state.corrections) {
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
            }

            WhatsAppBroadcastCard(
                whatsAppText = whatsAppText,
                isTelugu = isTelugu,
                isPreviewExpanded = showWhatsAppPreview,
                onTogglePreview = { showWhatsAppPreview = !showWhatsAppPreview },
                onShare = { exporter.shareText(whatsAppText) },
                onCopy = { clipboardManager.setText(AnnotatedString(whatsAppText)) }
            )

            // 5. Last File Shared Pill
            lastFile?.let { file ->
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${file.name} (${file.length() / 1024} KB)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = {
                                val mime = if (file.name.endsWith(".pdf")) "application/pdf" else "text/csv"
                                exporter.shareFile(file, mime)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share again",
                                tint = TempleSaffron,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 6. Offline Trust Footer
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = TempleSaffron,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isTelugu)
                            "100% ఆఫ్‌లైన్ నివేదికలు • పరికరంలోనే భద్రపరచబడతాయి • గోప్యత హామీ"
                        else
                            "100% Offline Generation • Stored safely on device • Privacy Guaranteed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Executive Summary Card with 3-column financial snapshot and metrics chips. */
@Composable
private fun ExecutiveFinancialSummaryCard(
    event: Event,
    totals: BalanceSnapshot,
    donationCount: Int,
    expenseCount: Int,
    correctionCount: Int,
    isTelugu: Boolean
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (event.templeName.isNotBlank() && event.templeName != event.name) {
                        Text(
                            text = event.templeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TempleSaffron,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (event.status.name == "ACTIVE") EmeraldWash else MaroonWash
                ) {
                    Text(
                        text = if (event.status.name == "ACTIVE") {
                            if (isTelugu) "యాక్టివ్" else "Active"
                        } else {
                            if (isTelugu) "ముగిసింది" else "Closed"
                        },
                        color = if (event.status.name == "ACTIVE") EmeraldGreen else CrimsonRose,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3-Column Financial Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Collections
                FinancialMetricBox(
                    title = if (isTelugu) "సేకరించినవి" else "Collected",
                    amount = formatInr(totals.cashCollected),
                    icon = Icons.Filled.ArrowUpward,
                    accentColor = EmeraldGreen,
                    backgroundColor = EmeraldWash,
                    modifier = Modifier.weight(1f)
                )

                // Expenses
                FinancialMetricBox(
                    title = if (isTelugu) "ఖర్చులు" else "Expenses",
                    amount = formatInr(totals.expenseTotal),
                    icon = Icons.Filled.ArrowDownward,
                    accentColor = CrimsonRose,
                    backgroundColor = CrimsonWash,
                    modifier = Modifier.weight(1f)
                )

                // Net Balance
                FinancialMetricBox(
                    title = if (isTelugu) "నికర నిల్వ" else "Net Balance",
                    amount = formatInr(totals.balance),
                    icon = Icons.Filled.Wallet,
                    accentColor = if (totals.balance >= 0) TempleSaffron else CrimsonRose,
                    backgroundColor = if (totals.balance >= 0) SaffronWash else CrimsonWash,
                    modifier = Modifier.weight(1.1f)
                )
            }

            // Micro Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("$donationCount ${if (isTelugu) "విరాళాలు" else "Donations"}", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("$expenseCount ${if (isTelugu) "ఖర్చులు" else "Expenses"}", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                )
                if (correctionCount > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("$correctionCount ${if (isTelugu) "సవరణలు" else "Corrections"}", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = GoldWash),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialMetricBox(
    title: String,
    amount: String,
    icon: ImageVector,
    accentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

/** Modern action card for PDF and primary reports. */
@Composable
private fun ModernReportActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    buttonText: String,
    working: Boolean,
    onAction: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBackground)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onAction,
                enabled = !working,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TempleSaffron,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generating PDF…")
                } else {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(buttonText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Excel / CSV Spreadsheets Card with dual export actions. */
@Composable
private fun ExcelSpreadsheetsCard(
    isTelugu: Boolean,
    working: String?,
    onExportDonations: () -> Unit,
    onExportExpenses: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldWash)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Assessment,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTelugu) "ఎక్సెల్ స్ప్రెడ్‌షీట్‌లు (CSV)" else "Excel / CSV Spreadsheets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isTelugu)
                            "మైక్రోసాఫ్ట్ ఎక్సెల్ లేదా గూగుల్ షీట్స్ కొరకు రా డేటా ఎగుమతి."
                        else
                            "Export tabular audit data compatible with Microsoft Excel & Google Sheets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportDonations,
                    enabled = working == null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (working == "csv-d") {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isTelugu) "విరాళాలు (CSV)" else "Donations CSV",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onExportExpenses,
                    enabled = working == null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (working == "csv-x") {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isTelugu) "ఖర్చులు (CSV)" else "Expenses CSV",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/** WhatsApp Broadcast Card with 1-tap sharing and interactive message preview. */
@Composable
private fun WhatsAppBroadcastCard(
    whatsAppText: String,
    isTelugu: Boolean,
    isPreviewExpanded: Boolean,
    onTogglePreview: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldWash)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTelugu) "వాట్సాప్ కమిటీ బ్రాడ్‌కాస్ట్" else "WhatsApp Committee Broadcast",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isTelugu)
                            "ఉత్సవ కమిటీ గ్రూపులలో శీఘ్ర భాగస్వామ్యానికి సరిపోయే సంక్షిప్త సందేశం."
                        else
                            "Short Telugu + English message formatted for festival and temple WhatsApp groups.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTelugu) "వాట్సాప్‌లో పంపు" else "Share to WhatsApp", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onTogglePreview,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isPreviewExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isTelugu) "ప్రివ్యూ" else "Preview")
                }
            }

            AnimatedVisibility(
                visible = isPreviewExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTelugu) "సందేశం ప్రివ్యూ:" else "Message Preview:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TempleSaffron
                            )
                            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = whatsAppText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberExporter(): com.shankaravam.festival.core.export.ReportExporter {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) { com.shankaravam.festival.core.export.ReportExporter(context) }
}
