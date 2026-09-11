package com.durgamma.festival.presentation.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.durgamma.festival.presentation.common.containerViewModel

/**
 * Pinned current-event banner (plan §5). Always visible so records never
 * land in the wrong event. Houses the selector + new-event dialog.
 */
@Composable
fun CurrentEventBanner(
    modifier: Modifier = Modifier,
    viewModel: EventViewModel = containerViewModel { c ->
        EventViewModel(c, c.sessionPrefs)
    }
) {
    val state by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Current event", style = MaterialTheme.typography.labelLarge)
                Text(
                    state.currentEvent?.name ?: "No event yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                state.currentEvent?.let {
                    if (it.templeName.isNotBlank()) {
                        Text(it.templeName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            IconButton(onClick = { showMenu = true }, enabled = state.events.isNotEmpty()) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch event")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                state.events.forEach { event ->
                    DropdownMenuItem(
                        text = { Text(event.name) },
                        onClick = {
                            viewModel.selectEvent(event.id)
                            showMenu = false
                        }
                    )
                }
            }
            OutlinedButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("New")
            }
        }
    }

    if (showCreate) {
        CreateEventDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, temple, location ->
                if (viewModel.createEvent(name, temple, location)) showCreate = false
            }
        )
    }
}

@Composable
private fun CreateEventDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var temple by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = false },
                    label = { Text("Event name *") }, singleLine = true,
                    isError = error, supportingText = { if (error) Text("Name is required") }
                )
                OutlinedTextField(
                    value = temple, onValueChange = { temple = it },
                    label = { Text("Temple / organization") }, singleLine = true
                )
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Location") }, singleLine = true
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Works fully offline. Cloud sync can be enabled later (G6).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) error = true else onCreate(name, temple, location)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
