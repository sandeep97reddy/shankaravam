package com.durgamma.festival.presentation.expense

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.durgamma.festival.core.export.ReportContent
import com.durgamma.festival.core.theme.DeepMaroon
import com.durgamma.festival.core.theme.TempleGold
import com.durgamma.festival.presentation.common.containerViewModel
import com.durgamma.festival.presentation.donation.PAYMENT_METHODS
import java.util.Calendar

/**
 * Expense form (plan §18). Receipt images compress to ~100KB WebP in the
 * background; the save itself commits to Room instantly with a haptic.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEntryScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseEntryViewModel = containerViewModel { ExpenseEntryViewModel(it) }
) {
    val form by viewModel.form.collectAsState()
    val eventId by viewModel.currentEventId.collectAsState()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }

    val receiptPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.attachReceipt(it) } }

    LaunchedEffect(form.saveState) {
        when (val s = form.saveState) {
            is ExpenseSaveState.Saved -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.consumeSaved()
                onDone()
            }
            is ExpenseSaveState.Error -> snackbar.showSnackbar(s.message)
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("New expense") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (eventId == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select or create an event first.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = form.amountText,
                onValueChange = { v ->
                    if (v.all { c -> c.isDigit() || c == '.' }) {
                        viewModel.update { it.copy(amountText = v) }
                    }
                },
                label = { Text("Amount ₹ *") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                label = { Text("Description *") },
                placeholder = { Text("Marigold garlands") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EXPENSE_CATEGORIES.forEach { category ->
                    FilterChip(
                        selected = form.category == category && form.customCategory.isBlank(),
                        onClick = { viewModel.update { it.copy(category = category, customCategory = "") } },
                        label = { Text(category, maxLines = 1) }
                    )
                }
            }
            OutlinedTextField(
                value = form.customCategory,
                onValueChange = { v -> viewModel.update { it.copy(customCategory = v) } },
                label = { Text("Or custom category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            DateRow(form.dateMillis) { picked ->
                viewModel.update { it.copy(dateMillis = picked) }
            }
            var methodExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = methodExpanded,
                onExpandedChange = { methodExpanded = !methodExpanded }
            ) {
                OutlinedTextField(
                    value = form.paymentMethod, onValueChange = {},
                    readOnly = true, label = { Text("Paid via") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodExpanded) },
                    modifier = Modifier.fillMaxWidth()
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = methodExpanded,
                    onDismissRequest = { methodExpanded = false }
                ) {
                    PAYMENT_METHODS.forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method) },
                            onClick = {
                                viewModel.update { it.copy(paymentMethod = method) }
                                methodExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = form.paidBy,
                onValueChange = { v -> viewModel.update { it.copy(paidBy = v) } },
                label = { Text("Paid by") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.vendor,
                onValueChange = { v -> viewModel.update { it.copy(vendor = v) } },
                label = { Text("Vendor / shop") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            when (val receipt = form.receiptState) {
                is ReceiptState.None, is ReceiptState.Error -> {
                    OutlinedButton(
                        onClick = { receiptPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Text("Attach receipt photo")
                    }
                    if (receipt is ReceiptState.Error) {
                        Text(receipt.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is ReceiptState.Attaching -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Compressing receipt…")
                    }
                }
                is ReceiptState.Attached -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "🧾 ${receipt.fileName} (~${receipt.sizeKb} KB)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearReceipt() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove receipt")
                        }
                    }
                }
            }
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.save() },
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (form.saveState == ExpenseSaveState.Saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text("Save expense")
            }
        }
    }
}

@Composable
private fun DateRow(currentMillis: Long, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onPick(picked)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Date: ${ReportContent.formatTime(currentMillis).substring(0, 10)}")
    }
}
