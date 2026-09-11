package com.shankaravam.festival.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.shankaravam.festival.core.i18n.AppStrings
import com.shankaravam.festival.core.i18n.EnglishStrings
import com.shankaravam.festival.core.i18n.LocalAppStrings

private val LightScheme = lightColorScheme(
    primary = TempleSaffron,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDE1),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = DeepMaroon,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE7EC),
    onSecondaryContainer = DeepMaroon,
    tertiary = RadiantGold,
    onTertiary = Color.White,
    tertiaryContainer = RadiantGoldLight,
    onTertiaryContainer = Color(0xFF78350F),
    surface = Color(0xFFFCFBF8),
    onSurface = SacredCharcoal,
    surfaceVariant = Color(0xFFF5EFE6),
    onSurfaceVariant = Color(0xFF5C4B3F),
    surfaceContainer = Color(0xFFFBF8F2),
    surfaceContainerHigh = Color(0xFFF4ECE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = Color(0xFFE2D6C6),
    outlineVariant = Color(0xFFEFE8DC),
    background = Color(0xFFFAF7F2),
    onBackground = SacredCharcoal,
    error = RequiredRed,
    onError = Color.White,
    errorContainer = CrimsonRoseLight,
    onErrorContainer = CrimsonRose
)

private val DarkScheme = darkColorScheme(
    primary = TempleSaffron,
    onPrimary = SacredCharcoal,
    primaryContainer = Color(0xFF3B1506),
    onPrimaryContainer = Color(0xFFFFD5BD),
    secondary = Color(0xFFE89BA7),
    onSecondary = SacredCharcoal,
    secondaryContainer = DeepMaroon,
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = TempleGold,
    onTertiary = SacredCharcoal,
    surface = Color(0xFF1E1610),
    onSurface = Color(0xFFF5EDE4),
    surfaceVariant = Color(0xFF2C2018),
    onSurfaceVariant = Color(0xFFDCCFBE),
    outline = Color(0xFF4D3B2E),
    outlineVariant = Color(0xFF3A2B20),
    background = SacredCharcoal,
    onBackground = Color(0xFFF5EDE4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun ShankaRavamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    strings: AppStrings = EnglishStrings,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppStrings provides strings) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = ShankaRavamTypography,
            shapes = ShankaRavamShapes,
            content = content
        )
    }
}
