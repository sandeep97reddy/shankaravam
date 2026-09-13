package com.shankaravam.festival.presentation.event

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.shankaravam.festival.core.theme.eventColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.SaffronWash
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.presentation.common.ModernTextField
import com.shankaravam.festival.presentation.common.containerViewModel

/**
 * Modernized Current Event Selector Banner.
 * Sleek card displaying the active festival with quick switch dropdown and new event dialog.
 */
@Composable
fun CurrentEventBanner(
    modifier: Modifier = Modifier,
    viewModel: EventViewModel = containerViewModel { c ->
        EventViewModel(c, c.sessionPrefs)
    }
) {
    val state by viewModel.uiState.collectAsState()
    val strings = appStrings()
    var showMenu by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val currentEventColor = state.currentEvent?.let { eventColorFor(it.id) } ?: TempleSaffron
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = currentEventColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Festival,
                        contentDescription = null,
                        tint = currentEventColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = strings.currentEvent.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = currentEventColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.currentEvent?.let {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(currentEventColor, CircleShape)
                        )
                    }
                    Text(
                        text = state.currentEvent?.name ?: strings.noEventYet,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                state.currentEvent?.let {
                    val subtext = listOf(it.templeName, it.location).filter { s -> s.isNotBlank() }.joinToString(" • ")
                    if (subtext.isNotBlank()) {
                        Text(
                            text = subtext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.events.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = strings.switchButton,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        state.events.forEach { event ->
                            val isCurrent = event.id == state.currentEvent?.id
                            val dotColor = eventColorFor(event.id)
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(dotColor, CircleShape)
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            event.name,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) TempleSaffron else MaterialTheme.colorScheme.onSurface
                                        )
                                        val sub = listOf(event.templeName, event.location)
                                            .filter { s -> s.isNotBlank() }
                                            .joinToString(" • ")
                                        if (sub.isNotBlank()) {
                                            Text(
                                                sub,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.selectEvent(event.id)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { showCreate = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        strings.newButton,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateEventModernDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, temple, location ->
                if (viewModel.createEvent(name, temple, location)) {
                    showCreate = false
                }
            }
        )
    }
}

/**
 * Modernized Create Event Dialog with leading icons, quick festival suggestions, and red * marks.
 */
@Composable
private fun CreateEventModernDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    val strings = appStrings()
    var name by remember { mutableStateOf("") }
    var temple by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val festivalPresets = if (strings.languageCode == "te") {
        listOf("వినాయక చవితి", "శ్రీరామ నవమి", "దేవీ నవరాత్రులు", "హనుమాన్ జయంతి", "మహా శివరాత్రి")
    } else {
        listOf("Vinayaka Chavithi", "Sri Rama Navami", "Devi Navaratri", "Hanuman Jayanthi", "Maha Shivaratri")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Celebration, contentDescription = null, tint = TempleSaffron)
                Text(
                    text = strings.createNewEvent,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Quick Festival Suggestions Chips
                Text(
                    text = strings.festivalSuggestions,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    festivalPresets.forEach { preset ->
                        FilterChip(
                            selected = name == preset,
                            onClick = { name = preset; error = false },
                            label = { Text(preset, fontSize = 12.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TempleSaffron.copy(alpha = 0.15f),
                                selectedLabelColor = TempleSaffron
                            )
                        )
                    }
                }

                ModernTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = strings.eventNameLabel,
                    isRequired = true,
                    placeholder = if (strings.languageCode == "te") "ఉదా: వినాయక చవితి" else "e.g. Vinayaka Chavithi",
                    leadingIcon = Icons.Filled.Festival,
                    singleLine = true,
                    isError = error,
                    errorMessage = strings.nameRequiredError
                )

                ModernTextField(
                    value = temple,
                    onValueChange = { temple = it },
                    label = strings.templeOrgLabel,
                    placeholder = if (strings.languageCode == "te") "ఉదా: శివాలయం కమిటీ" else "e.g. Siva Temple Committee",
                    leadingIcon = Icons.Filled.AccountBalance,
                    singleLine = true
                )

                ModernTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = strings.locationLabel,
                    placeholder = if (strings.languageCode == "te") "ఉదా: కొట్లగడ్డ / మెయిన్ రోడ్డు" else "e.g. Kotlagadda / Main Road",
                    leadingIcon = Icons.Filled.LocationOn,
                    singleLine = true
                )

                Spacer(Modifier.height(2.dp))
                Text(
                    text = strings.offlineNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = true
                    } else {
                        onCreate(name.trim(), temple.trim(), location.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.createAction, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        }
    )
}
