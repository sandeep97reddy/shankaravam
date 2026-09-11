package com.shankaravam.festival.core.util

import java.text.NumberFormat
import java.util.Locale

private val inrFormat: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
        maximumFractionDigits = 0
    }

/** ₹5,000 style, no decimals (paise are never entered at the counter). */
fun formatInr(amount: Double): String = inrFormat.format(amount)
