package com.durgamma.festival.presentation.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.durgamma.festival.core.export.ReportContent
import com.durgamma.festival.core.theme.DeepMaroon
import com.durgamma.festival.core.theme.TempleGold
import com.durgamma.festival.core.util.formatInr
import com.durgamma.festival.domain.model.Expense
import com.durgamma.festival.domain.model.ExpenseStatus
import com.durgamma.festival.presentation.common.containerViewModel
import com.durgamma.festival.presentation.correction.CorrectDialog

/**
 * Expense ledger (plan §18). Cancel voids (never deletes); Correct routes
 * through the grace-window/correction flow shared with donations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseListViewModel = containerViewModel { ExpenseListViewModel(it) }
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var toCancel by remember { mutableStateOf<Expense?>(null) }
    var toCorrect by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.consumeError() }
    }
    LaunchedEffect(state.lastCorrection) {
        if (state.lastCorrection != null) {
            snackbar.showSnackbar("Correction recorded — original preserved.")
            viewModel.consumeCorrection()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("Expenses (${state.expenses.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DeepMaroon,
                    titleContentColor = TempleGold,
                    navigationIconContentColor = TempleGold
                )
            )
        },
        floatingActionButton = {
            if (state.hasEvent) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Expense") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                label = { Text("Search description / vendor") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (state.availableCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.availableCategories.take(5).forEach { category ->
                        FilterChip(
                            selected = state.categoryFilter == category,
                            onClick = {
                                viewModel.setCategory(if (state.categoryFilter == category) null else category)
                            },
                            label = { Text(category, maxLines = 1) }
                        )
                    }
                    if (state.categoryFilter != null) {
                        IconButton(onClick = { viewModel.setCategory(null) }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear category")
                        }
                    }
                }
            }
            if (!state.hasEvent) {
                EmptyExpensesHint("Select or create an event to see expenses.")
            } else if (state.expenses.isEmpty()) {
                EmptyExpensesHint(
                    if (state.totalCount == 0) "No expenses yet — tap + Expense to add one."
                    else "No expenses match the active filters."
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = state.expenses, key = { it.id }) { expense ->
                        ExpenseCard(
                            expense = expense,
                            modifier = Modifier.animateItem(),
                            onCorrect = { toCorrect = expense },
                            onCancel = { toCancel = expense }
                        )
                    }
                }
            }
        }
    }

    toCancel?.let { expense ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text("Cancel expense?") },
            text = {
                Text(
                    "${expense.description} (${formatInr(expense.amount)}) stays in the ledger " +
                        "marked Cancelled — it is never deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelExpense(expense)
                    toCancel = null
                }) { Text("Cancel expense") }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text("Keep") } }
        )
    }

    toCorrect?.let { expense ->
        CorrectDialog(
            title = "Fix expense",
            originalAmount = expense.amount,
            addedTimeMillis = expense.addedTime,
            onDismiss = { toCorrect = null },
            onConfirm = { newAmount, reason ->
                viewModel.correctExpense(expense, newAmount, reason)
                toCorrect = null
            }
        )
    }
}

@Composable
private fun EmptyExpensesHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    modifier: Modifier = Modifier,
    onCorrect: () -> Unit,
    onCancel: () -> Unit
) {
    val cancelled = expense.status == ExpenseStatus.CANCELLED
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    expense.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatInr(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(onClick = {}, label = { Text(expense.category) })
                if (cancelled) {
                    AssistChip(onClick = {}, label = { Text("Cancelled") })
                }
                if (expense.receiptPath != null) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = "Has receipt",
                        tint = Color(0xFF2E7D32)
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (!cancelled) {
                    IconButton(onClick = onCorrect) {
                        Icon(Icons.Filled.Edit, contentDescription = "Fix amount")
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Cancel expense")
                    }
                }
            }
            Text(
                "${ReportContent.formatTime(expense.dateMillis).substring(0, 10)}" +
                    (expense.vendor?.let { " • $it" } ?: "") +
                    (expense.paidBy.ifBlank { null }?.let { " • paid by $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
