package com.shankaravam.festival.core.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Classy, non-intrusive haptic feedback utility for ShankaRavam.
 *
 * Uses native Android HapticFeedbackConstants to deliver crisp, hardware-native
 * tactile micro-feedback (ticks, clicks, confirmations) rather than harsh prolonged
 * motor vibrations. Automatically respects system settings and the user's in-app preference.
 */
class AppHaptics(
    private val view: View?,
    private val enabled: Boolean = true
) {
    /** Ultra-light mechanical tick: ideal for quick amount chips, filter chips, dropdown selections. */
    fun tick() {
        if (!enabled || view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Crisp, positive key tap: ideal for primary action buttons (+ Donation, - Expense). */
    fun click() {
        if (!enabled || view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Refined confirmation pulse: ideal for when a financial record is committed to Room DB. */
    fun success() {
        if (!enabled || view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** Gentle tactile warning: ideal for cancelling, voiding, or error states. */
    fun warning() {
        if (!enabled || view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

val LocalAppHaptics = staticCompositionLocalOf {
    AppHaptics(view = null, enabled = false)
}

@Composable
fun rememberAppHaptics(enabled: Boolean = true): AppHaptics {
    val view = LocalView.current
    return remember(view, enabled) {
        AppHaptics(view = view, enabled = enabled)
    }
}
