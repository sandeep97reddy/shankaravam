package com.shankaravam.festival.presentation.donation

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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.presentation.common.TempleAppBar
import com.shankaravam.festival.presentation.common.containerViewModel

/**
 * Counter-optimized donation form (plan §8–§9). Stateless: all state lives in
 * the ViewModel's single uiState; save commits to Room in <10ms + haptic.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DonationEntryScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DonationEntryViewModel = containerViewModel { DonationEntryViewModel(it) }
) {
    val form by viewModel.form.collectAsState()
    val eventId by viewModel.currentEventId.collectAsState()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(form.saveState) {
        when (val s = form.saveState) {
            is SaveState.Saved -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.consumeSaved()
                onDone()
            }
            is SaveState.Error -> snackbar.showSnackbar(s.message)
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TempleAppBarWithBack(title = "New donation", onBack = onDone)
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
                value = form.donorName,
                onValueChange = { v -> viewModel.update { it.copy(donorName = v) } },
                label = { Text("Donor name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.pronunciation,
                onValueChange = { v -> viewModel.update { it.copy(pronunciation = v) } },
                label = { Text("Pronunciation (Telugu, for announcement)") },
                placeholder = { Text("రమేష్") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !form.isNonCash,
                    onClick = { viewModel.update { it.copy(isNonCash = false) } },
                    label = { Text("Cash / UPI") }
                )
                FilterChip(
                    selected = form.isNonCash,
                    onClick = { viewModel.update { it.copy(isNonCash = true) } },
                    label = { Text("Item / Service") }
                )
            }
            if (!form.isNonCash) {
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
            } else {
                OutlinedTextField(
                    value = form.itemDescription,
                    onValueChange = { v -> viewModel.update { it.copy(itemDescription = v) } },
                    label = { Text("Item description *") },
                    placeholder = { Text("10 kg rice bag") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.quantityText,
                        onValueChange = { v ->
                            if (v.all { c -> c.isDigit() || c == '.' }) {
                                viewModel.update { it.copy(quantityText = v) }
                            }
                        },
                        label = { Text("Qty") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = form.unit,
                        onValueChange = { v -> viewModel.update { it.copy(unit = v) } },
                        label = { Text("Unit") },
                        placeholder = { Text("kg") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            PaymentMethodDropdown(form.paymentMethod) { v ->
                viewModel.update { it.copy(paymentMethod = v) }
            }
            StatusDropdown(form.status) { v -> viewModel.update { it.copy(status = v) } }

            Text("Tags", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TAG_SUGGESTIONS.forEach { tag ->
                    FilterChip(
                        selected = tag in form.tags,
                        onClick = { viewModel.toggleTag(tag) },
                        label = { Text(tag) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = form.newTagText,
                    onValueChange = { v -> viewModel.update { it.copy(newTagText = v) } },
                    label = { Text("Custom tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.addCustomTag() }) { Text("Add") }
            }
            if (form.tags.any { it !in TAG_SUGGESTIONS }) {
                Text(
                    "Custom: " + form.tags.filter { it !in TAG_SUGGESTIONS }.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Announce publicly")
                Switch(
                    checked = form.announcementEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(announcementEnabled = v) } }
                )
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
                if (form.saveState == SaveState.Saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text("Save donation")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected, onValueChange = {},
            readOnly = true, label = { Text("Payment method") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PAYMENT_METHODS.forEach { method ->
                DropdownMenuItem(
                    text = { Text(method) },
                    onClick = { onSelect(method); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(selected: DonationStatus, onSelect: (DonationStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected.name.lowercase().replace('_', ' '),
            onValueChange = {},
            readOnly = true, label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DonationStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.name.lowercase().replace('_', ' ')) },
                    onClick = { onSelect(status); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TempleAppBarWithBack(title: String, onBack: () -> Unit) {
    androidx.compose.material3.CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = com.shankaravam.festival.core.theme.DeepMaroon,
            titleContentColor = com.shankaravam.festival.core.theme.TempleGold,
            navigationIconContentColor = com.shankaravam.festival.core.theme.TempleGold
        )
    )
}
