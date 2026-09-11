package com.durgamma.festival.presentation.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * G1 minimal currency field. G3 extends with Telugu formatting + quick amounts.
 * Stateless per AGENTS.md §5 — state hoisted by caller.
 */
@Composable
fun CurrencyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "₹ Amount",
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.all { it.isDigit() || it == '.' }) onValueChange(input)
        },
        modifier = modifier,
        label = { Text(label) },
        prefix = { Text("₹") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
