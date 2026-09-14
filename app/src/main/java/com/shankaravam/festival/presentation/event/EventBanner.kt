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

import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow

/**
 * Modernized, Roomy Current Event Selector Banner.
 * Sleek card displaying the active festival with generous breathing room,
 * prominent typography, comfortable touch targets, and new event creation.
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        val currentEvent = state.currentEvent
        if (currentEvent != null) {
            val currentEventColor = eventColorFor(currentEvent.id)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Top Tier: Festival Emblem & Informative Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = currentEventColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Festival,
                                contentDescription = null,
                                tint = currentEventColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(currentEventColor, CircleShape)
                            )
                            Text(
                                text = strings.currentEvent.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = currentEventColor,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = currentEvent.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtext = listOf(currentEvent.templeName, currentEvent.location)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        if (subtext.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = subtext,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp
                )

                // Bottom Tier: Roomy Action Buttons
                if (state.events.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    text = strings.switchButton,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
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
                                                    .filter { it.isNotBlank() }
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
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = strings.newButton,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showCreate = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                            modifier = Modifier.height(42.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = strings.newButton,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Zero festival selected / created state: inviting, roomy hero card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TempleSaffron.copy(alpha = 0.14f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Celebration,
                                contentDescription = null,
                                tint = TempleSaffron,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.currentEvent.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TempleSaffron,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = strings.noEventYet,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = strings.createFirstEventHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Button(
                    onClick = { showCreate = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TempleSaffron),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = strings.createNewEvent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
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
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TempleSaffron.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Celebration, contentDescription = null, tint = TempleSaffron, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    text = strings.createNewEvent,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(42.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)
            ) {
                Text(strings.createAction, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text(strings.cancelAction, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
