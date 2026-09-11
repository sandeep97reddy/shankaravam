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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.presentation.common.ModernTextField
import com.shankaravam.festival.presentation.common.QuickAmountRow
import com.shankaravam.festival.presentation.common.TempleAppBar
import com.shankaravam.festival.presentation.common.containerViewModel

/**
 * Counter-optimized donation form with modernized input boxes,
 * leading icons, highlighted star marks (*), quick auspicious amount chips,
 * and bilingual support. Commits to Room in <10ms + haptic feedback.
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
    val strings = appStrings()

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
            TempleAppBar(
                title = strings.newDonationTitle,
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
            // 1. Donor Name (Required *)
            ModernTextField(
                value = form.donorName,
                onValueChange = { v -> viewModel.update { it.copy(donorName = v) } },
                label = strings.donorNameLabel,
                isRequired = true,
                leadingIcon = Icons.Filled.Person,
                singleLine = true,
                placeholder = if (strings.languageCode == "te") "ఉదా: రమేష్ రావు" else "e.g. Ramesh Rao"
            )

            // 2. Telugu Pronunciation (For speaker)
            ModernTextField(
                value = form.pronunciation,
                onValueChange = { v -> viewModel.update { it.copy(pronunciation = v) } },
                label = strings.teluguPronunciationLabel,
                placeholder = strings.pronunciationPlaceholder,
                leadingIcon = Icons.Filled.RecordVoiceOver,
                singleLine = true
            )

            // 3. Donation Type Toggle Chips (Cash/UPI vs Material/Item)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !form.isNonCash,
                    onClick = { viewModel.update { it.copy(isNonCash = false) } },
                    label = { Text(strings.cashOrUpi, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TempleSaffron,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = form.isNonCash,
                    onClick = { viewModel.update { it.copy(isNonCash = true) } },
                    label = { Text(strings.itemOrService, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TempleSaffron,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }

            // 4. Amount or Item Fields
            if (!form.isNonCash) {
                // Quick Auspicious Amount Chips (101, 501, 1116, 2116, 5116)
                Text(
                    text = strings.quickAmountsLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                QuickAmountRow(
                    currentAmount = form.amountText,
                    onAmountSelected = { amt ->
                        viewModel.update { it.copy(amountText = amt) }
                    }
                )

                ModernTextField(
                    value = form.amountText,
                    onValueChange = { v ->
                        if (v.all { c -> c.isDigit() || c == '.' }) {
                            viewModel.update { it.copy(amountText = v) }
                        }
                    },
                    label = strings.amountLabel,
                    isRequired = true,
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = TempleSaffron) },
                    leadingIcon = Icons.Filled.CurrencyRupee,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            } else {
                ModernTextField(
                    value = form.itemDescription,
                    onValueChange = { v -> viewModel.update { it.copy(itemDescription = v) } },
                    label = strings.itemDescriptionLabel,
                    isRequired = true,
                    placeholder = if (strings.languageCode == "te") "ఉదా: 25 కేజీల బియ్యం బస్తా" else "e.g. 25 kg rice bag",
                    leadingIcon = Icons.Filled.Inventory2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernTextField(
                        value = form.quantityText,
                        onValueChange = { v ->
                            if (v.all { c -> c.isDigit() || c == '.' }) {
                                viewModel.update { it.copy(quantityText = v) }
                            }
                        },
                        label = strings.quantityLabel,
                        leadingIcon = Icons.Filled.Scale,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    ModernTextField(
                        value = form.unit,
                        onValueChange = { v -> viewModel.update { it.copy(unit = v) } },
                        label = strings.unitLabel,
                        placeholder = "kg",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. Payment Method & Status Dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentMethodDropdown(
                    selected = form.paymentMethod,
                    label = strings.paymentMethodLabel,
                    modifier = Modifier.weight(1f)
                ) { v ->
                    viewModel.update { it.copy(paymentMethod = v) }
                }

                StatusDropdown(
                    selected = form.status,
                    label = strings.statusLabel,
                    modifier = Modifier.weight(1f)
                ) { v ->
                    viewModel.update { it.copy(status = v) }
                }
            }

            // 6. Tags Section
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
                        Icon(Icons.Filled.Tag, contentDescription = null, tint = TempleSaffron, modifier = Modifier.size(18.dp))
                        Text(strings.tagsLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TAG_SUGGESTIONS.forEach { tag ->
                            FilterChip(
                                selected = tag in form.tags,
                                onClick = { viewModel.toggleTag(tag) },
                                label = { Text(tag, fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModernTextField(
                            value = form.newTagText,
                            onValueChange = { v -> viewModel.update { it.copy(newTagText = v) } },
                            label = strings.customTagLabel,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.addCustomTag() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.addTagAction)
                        }
                    }
                }
            }

            // 7. Announce Publicly Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (form.announcementEnabled) TempleSaffron else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strings.announcePublicly,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = form.announcementEnabled,
                        onCheckedChange = { v -> viewModel.update { it.copy(announcementEnabled = v) } }
                    )
                }
            }

            // 8. Notes
            ModernTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = strings.notesLabel,
                leadingIcon = Icons.Filled.Notes,
                singleLine = false,
                minLines = 2
            )

            Spacer(Modifier.height(4.dp))

            // 9. Large Prominent Save Button
            Button(
                onClick = { viewModel.save() },
                enabled = form.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (form.saveState == SaveState.Saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp)
                    )
                }
                Icon(Icons.Filled.Verified, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = strings.saveDonationAction,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodDropdown(
    selected: String,
    label: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.Payment, contentDescription = null, tint = TempleSaffron) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = TempleSaffron,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
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
private fun StatusDropdown(
    selected: DonationStatus,
    label: String,
    modifier: Modifier = Modifier,
    onSelect: (DonationStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = TempleSaffron,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DonationStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }) },
                    onClick = { onSelect(status); expanded = false }
                )
            }
        }
    }
}
