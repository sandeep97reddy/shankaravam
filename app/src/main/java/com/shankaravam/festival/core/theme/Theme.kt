package com.shankaravam.festival.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = TempleSaffron,
    onPrimary = Color.White,
    primaryContainer = DivineAmber,
    onPrimaryContainer = SacredCharcoal,
    secondary = DeepMaroon,
    onSecondary = Color.White,
    tertiary = TempleGold,
    surface = WarmIvory,
    onSurface = SacredCharcoal,
    surfaceVariant = Color(0xFFF3E7D3),
    onSurfaceVariant = Color(0xFF5D4037),
    background = WarmIvory,
    onBackground = SacredCharcoal
)

private val DarkScheme = darkColorScheme(
    primary = TempleSaffron,
    onPrimary = SacredCharcoal,
    primaryContainer = DeepMaroon,
    onPrimaryContainer = TempleGold,
    secondary = TempleGold,
    onSecondary = SacredCharcoal,
    tertiary = DivineAmber,
    surface = SacredCharcoal,
    onSurface = WarmIvory,
    surfaceVariant = Color(0xFF2E1F16),
    onSurfaceVariant = Color(0xFFE8D5B7),
    background = SacredCharcoal,
    onBackground = WarmIvory
)

@Composable
fun ShankaRavamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ShankaRavamTypography,
        shapes = ShankaRavamShapes,
        content = content
    )
}
