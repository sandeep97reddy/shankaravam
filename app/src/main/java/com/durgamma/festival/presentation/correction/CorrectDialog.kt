package com.durgamma.festival.presentation.correction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.durgamma.festival.core.util.formatInr
import com.durgamma.festival.domain.usecase.CORRECTION_GRACE_WINDOW_MS

/**
 * Shared amount-fix dialog (plan §16). Inside the 5-minute grace window the
 * row is fixed directly; outside, a reason is mandatory and a Correction row
 * is appended while the original stays untouched.
 */
@Composable
fun CorrectDialog(
    title: String,
    originalAmount: Double,
    addedTimeMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (newAmount: Double, reason: String) -> Unit
) {
    var amountText by remember { mutableStateOf(trimAmount(originalAmount)) }
    var reason by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val inGraceWindow =
        System.currentTimeMillis() - addedTimeMillis <= CORRECTION_GRACE_WINDOW_MS
    val parsed = amountText.toDoubleOrNull()
    val reasonOk = inGraceWindow || reason.isNotBlank()
    val canConfirm = parsed != null && parsed >= 0 && parsed != originalAmount && reasonOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (inGraceWindow) {
                        "Within the 5-minute window — fixes the entry directly, no ledger row."
                    } else {
                        "Original ${formatInr(originalAmount)} is preserved; your fix is appended as a correction."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v ->
                        if (v.all { c -> c.isDigit() || c == '.' }) amountText = v
                    },
                    label = { Text("Correct amount ₹") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!inGraceWindow) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason *") },
                        placeholder = { Text("Typo entered 5000 instead of 500") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        isError = attempted && reason.isBlank()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                if (canConfirm) onConfirm(parsed!!, reason)
            }) { Text("Save fix") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun trimAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
