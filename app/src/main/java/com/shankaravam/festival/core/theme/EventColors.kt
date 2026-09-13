package com.shankaravam.festival.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Curated temple-inspired color palette for differentiating events (e.g. multiple Vinayaka Chavithis).
 * High contrast, spiritually harmonious colors that look distinctive on both light and dark themes.
 */
val EventDotPalette = listOf(
    Color(0xFFE65100), // Temple Saffron
    Color(0xFF880E4F), // Sacred Deep Crimson
    Color(0xFF1B5E20), // Tulasi Forest Green
    Color(0xFF0D47A1), // Sacred Krishna Blue
    Color(0xFFC2185B), // Kumkum Deep Rose
    Color(0xFFFF8F00), // Divine Amber
    Color(0xFF4A148C), // Royal Deity Purple
    Color(0xFF00695C), // Sanctum Teal
    Color(0xFFBF360C)  // Terracotta Red
)

/** Deterministically maps any event ID to a consistent palette color with zero database migrations. */
fun eventColorFor(eventId: String): Color {
    if (eventId.isBlank()) return EventDotPalette[0]
    // Never abs(): abs(Int.MIN_VALUE) stays negative and would index out of bounds.
    val index = (eventId.hashCode() and Int.MAX_VALUE) % EventDotPalette.size
    return EventDotPalette[index]
}
