package com.shankaravam.festival.presentation.expense

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shankaravam.festival.core.export.ReportContent
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.core.theme.CrimsonRose
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.presentation.common.ClosedEventBanner
import com.shankaravam.festival.presentation.common.ModernTextField
import com.shankaravam.festival.presentation.common.TempleAppBar
import com.shankaravam.festival.presentation.common.containerViewModel
import com.shankaravam.festival.presentation.donation.PAYMENT_METHODS
import java.util.Calendar

/**
 * Modernized Expense entry screen:
 * - ModernTextField with leading icons and highlighted red star mark (*)
 * - Category chips with clear layout
 * - Clean date selector
 * - Fast background receipt compression
 * - Instant Room commit (<10ms) with haptic feedback
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
    val isClosed by viewModel.isEventClosed.collectAsState()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val strings = appStrings()

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
            TempleAppBar(
                title = strings.newExpenseTitle,
                onBack = onDone
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
                Text(
                    strings.noEventYet,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. Closed-festival lock (renders nothing while open).
            ClosedEventBanner(isClosed = isClosed)

            // 1. Amount ₹ (Required *)
            ModernTextField(
                value = form.amountText,
                onValueChange = { v ->
                    if (v.all { c -> c.isDigit() || c == '.' }) {
                        viewModel.update { it.copy(amountText = v) }
                    }
                },
                label = strings.expenseAmountLabel,
                isRequired = true,
                prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = CrimsonRose) },
                leadingIcon = Icons.Filled.CurrencyRupee,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // 2. Description (Required *)
            ModernTextField(
                value = form.description,
                onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                label = strings.expenseDescriptionLabel,
                isRequired = true,
                placeholder = if (strings.languageCode == "te") "ఉదా: పూజా సామాగ్రి, మైక్ సెట్, లైటింగ్" else "e.g. Pooja items, Flowers, Sound System",
                leadingIcon = Icons.Filled.Description,
                singleLine = true
            )

            // 3. Category Chips
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Category, contentDescription = null, tint = CrimsonRose, modifier = Modifier.size(18.dp))
                        Text(strings.categoryLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EXPENSE_CATEGORIES.forEach { category ->
                            val isSelected = form.category == category && form.customCategory.isBlank()
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.update { it.copy(category = category, customCategory = "") } },
                                label = { Text(category, fontSize = 12.sp, maxLines = 1) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonRose.copy(alpha = 0.15f),
                                    selectedLabelColor = CrimsonRose
                                )
                            )
                        }
                    }

                    ModernTextField(
                        value = form.customCategory,
                        onValueChange = { v -> viewModel.update { it.copy(customCategory = v) } },
                        label = strings.customCategoryLabel,
                        singleLine = true
                    )
                }
            }

            // 4. Date Row
            DateModernRow(form.dateMillis, label = strings.dateLabel) { picked ->
                viewModel.update { it.copy(dateMillis = picked) }
            }

            // 5. Payment Method & Paid By
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var methodExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = methodExpanded,
                    onExpandedChange = { methodExpanded = !methodExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = form.paymentMethod,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.paidViaLabel) },
                        leadingIcon = { Icon(Icons.Filled.Payment, contentDescription = null, tint = CrimsonRose) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodExpanded) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = CrimsonRose,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
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

                ModernTextField(
                    value = form.paidBy,
                    onValueChange = { v -> viewModel.update { it.copy(paidBy = v) } },
                    label = strings.paidByLabel,
                    leadingIcon = Icons.Filled.Person,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // 6. Vendor / Shop
            ModernTextField(
                value = form.vendor,
                onValueChange = { v -> viewModel.update { it.copy(vendor = v) } },
                label = strings.vendorLabel,
                leadingIcon = Icons.Filled.Store,
                singleLine = true
            )

            // 7. Receipt Attachment Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val receipt = form.receiptState) {
                        is ReceiptState.None, is ReceiptState.Error -> {
                            OutlinedButton(
                                onClick = { receiptPicker.launch("image/*") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(strings.attachReceiptAction)
                            }
                            if (receipt is ReceiptState.Error) {
                                Text(receipt.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ReceiptState.Attaching -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp).padding(end = 8.dp)
                                )
                                Text("Compressing receipt (WebP ~100KB)…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ReceiptState.Attached -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = CrimsonRose)
                                    Text(
                                        "${receipt.fileName} (~${receipt.sizeKb} KB)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(onClick = { viewModel.clearReceipt() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove receipt")
                                }
                            }
                        }
                    }
                }
            }

            // 8. Notes
            ModernTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = strings.notesLabel,
                leadingIcon = Icons.AutoMirrored.Filled.Notes,
                singleLine = false,
                minLines = 2
            )

            Spacer(Modifier.height(4.dp))

            // 9. Large Prominent Save Button
            Button(
                onClick = { viewModel.save() },
                enabled = form.canSave && !isClosed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRose),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (form.saveState == ExpenseSaveState.Saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp).padding(end = 8.dp)
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = strings.saveExpenseAction,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DateModernRow(
    currentMillis: Long,
    label: String,
    onPick: (Long) -> Unit
) {
    val context = LocalContext.current
    OutlinedCard(
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = CrimsonRose)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = ReportContent.formatTime(currentMillis).substring(0, 10),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
