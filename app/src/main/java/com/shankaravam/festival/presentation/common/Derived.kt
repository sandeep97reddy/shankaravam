package com.shankaravam.festival.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/**
 * Keyed memoized derivation for dashboard strings and counters.
 * Recomputes only when [keys] change — keeps formatting out of recomposition
 * (skill: derivedStateOf).
 */
@Composable
fun <T> derivedTotal(vararg keys: Any?, calculation: () -> T): State<T> =
    remember(*keys) { derivedStateOf(calculation) }
