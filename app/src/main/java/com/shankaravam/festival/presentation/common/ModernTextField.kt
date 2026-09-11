package com.shankaravam.festival.presentation.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.core.theme.RequiredRed
import com.shankaravam.festival.core.theme.TempleSaffron

/**
 * Modernized text input box with rounded geometry, soft container fill,
 * prominent leading icon, and an eye-catching red asterisk (*) for required fields.
 */
@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    readOnly: Boolean = false
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    if (isRequired) {
                        Text(
                            text = " *",
                            color = RequiredRed,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            },
            placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } },
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else TempleSaffron
                    )
                }
            },
            trailingIcon = trailingIcon,
            prefix = prefix,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = isError,
            supportingText = if (isError && errorMessage != null) {
                { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
            } else null,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            readOnly = readOnly,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedBorderColor = TempleSaffron,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                errorBorderColor = RequiredRed
            )
        )
    }
}

/**
 * Traditional auspicious festival amounts (₹101, ₹501, ₹1116, ₹2116, ₹5116).
 * Enables lightning-fast 1-tap counter data entry.
 */
@Composable
fun QuickAmountRow(
    currentAmount: String,
    onAmountSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    amounts: List<Int> = listOf(101, 501, 1116, 2116, 5116, 10116)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        amounts.forEach { amt ->
            val amtStr = amt.toString()
            val isSelected = currentAmount == amtStr
            FilterChip(
                selected = isSelected,
                onClick = { onAmountSelected(amtStr) },
                label = {
                    Text(
                        "₹$amtStr",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TempleSaffron,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}
