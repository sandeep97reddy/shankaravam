package com.shankaravam.festival.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.core.theme.CrimsonRose
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.presentation.common.DeveloperAttributionCard
import com.shankaravam.festival.core.theme.CrimsonRoseLight
import com.shankaravam.festival.core.theme.CrimsonWash
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.EmeraldGreen
import com.shankaravam.festival.core.theme.EmeraldGreenLight
import com.shankaravam.festival.core.theme.GoldWash
import com.shankaravam.festival.core.theme.RadiantGold
import com.shankaravam.festival.core.theme.SaffronWash
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.domain.usecase.BalanceSnapshot
import com.shankaravam.festival.presentation.common.ShankaRavamDashboardHeader
import com.shankaravam.festival.presentation.common.RevokedAccessBanner
import com.shankaravam.festival.presentation.common.containerViewModel
import com.shankaravam.festival.presentation.common.derivedTotal
import com.shankaravam.festival.presentation.event.CurrentEventBanner

/**
 * Redesigned, modern, uncluttered Festival Dashboard:
 * - App Name "ShankaRavam" / "శంఖారావం" at top-left with divine Sudarshana Chakra emblem
 * - Settings gear top-right (language lives in Admin Settings & Voice)
 * - Active event selector
 * - High-impact Financial Net Balance Card
 * - Dual Prominent Actions right at the top (Donate, Expense entry)
 * - Clean 4-tile Quick Navigation Hub (Announce, Donations, Expenses, History)
 * - Designed for maximum clarity for both English-speaking and rural volunteers.
 */
@Composable
fun DashboardScreen(
    onAddDonation: () -> Unit,
    onViewDonations: () -> Unit,
    onAnnounce: () -> Unit,
    onAddExpense: () -> Unit,
    onViewExpenses: () -> Unit,
    onHistory: () -> Unit,
    onReports: () -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = containerViewModel { DashboardViewModel(it) }
) {
    val state by viewModel.uiState.collectAsState()
    val unsynced by viewModel.unsyncedCount.collectAsState()
    val strings = appStrings()

    LaunchedEffect(state.event?.id) {
        if (state.event != null) viewModel.refreshUnsynced()
    }

    // Memoized formatted numbers
    val collectedText by derivedTotal(state.totals) { formatInr(state.totals.cashCollected) }
    val balanceText by derivedTotal(state.totals) { formatInr(state.totals.balance) }
    val pledgedText by derivedTotal(state.totals) { formatInr(state.totals.pledgedTotal) }
    val expenseText by derivedTotal(state.totals) { formatInr(state.totals.expenseTotal) }

    Scaffold(
        modifier = modifier,
        topBar = {
            ShankaRavamDashboardHeader(
                onOpenSettings = onSettings
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Current Event Selector Banner
            item {
                CurrentEventBanner()
            }

            // 1b. Revoked-access notice (renders nothing unless revoked).
            item {
                RevokedAccessBanner(eventId = state.event?.id)
            }

            if (state.event == null) {
                item {
                    NoEventPlaceholderCard(
                        strings = strings,
                        onJoinClick = onSync
                    )
                }
            } else {
                // 2. High-Impact Financial Balance Hero Card
                item {
                    FinancialHeroCard(
                        balanceText = balanceText,
                        collectedText = collectedText,
                        expenseText = expenseText,
                        pledgedText = pledgedText,
                        balanceValue = state.totals.balance,
                        deficitWarning = strings.deficitWarning
                    )
                }

                // 3. Dual Top Prominent Primary Actions (Donate & Expense entry)
                item {
                    DualActionHeader(
                        onAddDonation = onAddDonation,
                        onAddExpense = onAddExpense,
                        addDonationText = strings.addDonation,
                        addExpenseText = strings.addExpense
                    )
                }

                // 4. Status Chips Row (Donors, Non-cash, Sync status)
                item {
                    MetricsChipRow(
                        donorCount = state.totals.donorCount,
                        nonCashCount = state.totals.nonCashCount,
                        unsyncedCount = unsynced,
                        donorsLabel = strings.donorsCount,
                        nonCashLabel = strings.nonCashCount,
                        unsyncedLabel = strings.unsyncedCount
                    )
                }

                // 5. Clean 4-Tile Quick Navigation Hub
                item {
                    QuickNavigationHub(
                        onAnnounce = onAnnounce,
                        onDonations = onViewDonations,
                        onExpenses = onViewExpenses,
                        onHistory = onHistory,
                        announcementsTile = strings.announcementsTile,
                        announcementsSubtitle = strings.announcementsSubtitle,
                        donationsTile = strings.donationsTile,
                        donationsSubtitle = strings.donationsSubtitle,
                        expensesTile = strings.expensesTile,
                        expensesSubtitle = strings.expensesSubtitle,
                        historyTile = strings.historyTile,
                        historySubtitle = strings.historySubtitle
                    )
                }

                // 6. Secondary Utilities Row (Reports & Cloud Sync)
                item {
                    SecondaryUtilitiesRow(
                        onReports = onReports,
                        onSync = onSync,
                        reportsText = strings.reportsTile,
                        syncText = strings.syncTile
                    )
                }

                // 7. Developer & Designer Attribution Card
                item {
                    DeveloperAttributionCard(
                        currentLang = if (strings.languageCode == "te") SessionPrefs.LANG_TELUGU else SessionPrefs.LANG_ENGLISH,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Roomy, inviting state when no festival event is active, offering quick access to join via committee code.
 */
@Composable
private fun NoEventPlaceholderCard(
    strings: com.shankaravam.festival.core.i18n.AppStrings,
    onJoinClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = TempleGold.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.VolunteerActivism,
                        contentDescription = null,
                        tint = TempleSaffron,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = if (strings.languageCode == "te") "ఇతర కమిటీ ఉత్సవంలో చేరాలా?" else "Joining an Existing Festival?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (strings.languageCode == "te")
                    "మీ కమిటీ నిర్వాహకుడు అందించిన 6-అక్షరాల కోడ్‌తో నేరుగా చేరండి మరియు విరాళాలు సమకాలీకరించండి."
                else
                    "Enter the 6-character committee invite code to sync donations with your team members in real-time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onJoinClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (strings.languageCode == "te") "కోడ్‌తో చేరండి (క్లౌడ్ సింక్)" else "Join with Invite Code (Cloud Sync)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Clean, modern Financial Hero Card with Net Balance and Inflow vs Outflow metrics.
 */
@Composable
private fun FinancialHeroCard(
    balanceText: String,
    collectedText: String,
    expenseText: String,
    pledgedText: String,
    balanceValue: Double,
    deficitWarning: String
) {
    val strings = appStrings()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Net Balance Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (balanceValue >= 0) EmeraldGreenLight else CrimsonRoseLight)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Wallet,
                            contentDescription = null,
                            tint = if (balanceValue >= 0) EmeraldGreen else CrimsonRose,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = strings.netBalance,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = balanceText,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (balanceValue >= 0) MaterialTheme.colorScheme.onSurface else CrimsonRose,
                letterSpacing = (-0.5).sp
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Inflow vs Outflow Two-Column Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Inflow: Total Collected
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.totalCollected,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = collectedText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }

                // Outflow: Total Expenses
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = CrimsonRose,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.totalExpenses,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = expenseText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRose
                    )
                }
            }

            if (balanceValue < 0) {
                Surface(
                    color = CrimsonRoseLight,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = deficitWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonRose,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Top Prominent Dual Action Row:
 * Side-by-side bold buttons for + Donation and - Expense right at the top.
 */
@Composable
private fun DualActionHeader(
    onAddDonation: () -> Unit,
    onAddExpense: () -> Unit,
    addDonationText: String,
    addExpenseText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // + Add Donation Button (Temple Saffron)
        Button(
            onClick = onAddDonation,
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = addDonationText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // - Add Expense Button (Deep Maroon / Rose accent)
        Button(
            onClick = onAddExpense,
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRose),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = addExpenseText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

/**
 * Status and metrics chip row (donors, non-cash, unsynced status).
 */
@Composable
private fun MetricsChipRow(
    donorCount: Int,
    nonCashCount: Int,
    unsyncedCount: Int,
    donorsLabel: String,
    nonCashLabel: String,
    unsyncedLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = {},
            label = { Text("$donorCount $donorsLabel", fontWeight = FontWeight.Medium) },
            shape = RoundedCornerShape(16.dp),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        if (nonCashCount > 0) {
            AssistChip(
                onClick = {},
                label = { Text("$nonCashCount $nonCashLabel", fontWeight = FontWeight.Medium) },
                shape = RoundedCornerShape(16.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
        if (unsyncedCount > 0) {
            AssistChip(
                onClick = {},
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.SyncProblem,
                        contentDescription = null,
                        tint = RadiantGold,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        "$unsyncedCount $unsyncedLabel",
                        color = RadiantGold,
                        fontWeight = FontWeight.Bold
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = RadiantGold.copy(alpha = 0.1f)
                )
            )
        }
    }
}

/**
 * Clean 4-Tile Quick Navigation Hub:
 * 1. Announcements (Broadcast on speaker)
 * 2. Donations Ledger (View list & receipts)
 * 3. Expenses Ledger (Bills & spend)
 * 4. Activity History (Auditable log)
 */
@Composable
private fun QuickNavigationHub(
    onAnnounce: () -> Unit,
    onDonations: () -> Unit,
    onExpenses: () -> Unit,
    onHistory: () -> Unit,
    announcementsTile: String,
    announcementsSubtitle: String,
    donationsTile: String,
    donationsSubtitle: String,
    expensesTile: String,
    expensesSubtitle: String,
    historyTile: String,
    historySubtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavHubCard(
                title = donationsTile,
                subtitle = donationsSubtitle,
                icon = Icons.AutoMirrored.Filled.List,
                iconTint = TempleSaffron,
                iconBackground = SaffronWash,
                onClick = onDonations,
                modifier = Modifier.weight(1f)
            )
            NavHubCard(
                title = announcementsTile,
                subtitle = announcementsSubtitle,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                iconTint = TempleSaffron,
                iconBackground = SaffronWash,
                onClick = onAnnounce,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavHubCard(
                title = expensesTile,
                subtitle = expensesSubtitle,
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                iconTint = CrimsonRose,
                iconBackground = CrimsonWash,
                onClick = onExpenses,
                modifier = Modifier.weight(1f)
            )
            NavHubCard(
                title = historyTile,
                subtitle = historySubtitle,
                icon = Icons.Filled.History,
                iconTint = RadiantGold,
                iconBackground = GoldWash,
                onClick = onHistory,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBackground)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Secondary actions: Reports & Cloud Sync.
 */
@Composable
private fun SecondaryUtilitiesRow(
    onReports: () -> Unit,
    onSync: () -> Unit,
    reportsText: String,
    syncText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedCard(
            onClick = onReports,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = reportsText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        OutlinedCard(
            onClick = onSync,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = syncText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
