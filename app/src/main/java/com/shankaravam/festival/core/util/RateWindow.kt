package com.shankaravam.festival.core.util

/**
 * P4 Sarvam budget guard: at most [maxCalls] slots per rolling [windowMillis].
 * Pure + unit-testable — SessionPrefs only persists [windowStart]/[taken].
 */
class RateWindow(
    val maxCalls: Int,
    val windowMillis: Long,
    var windowStart: Long = 0L,
    var taken: Int = 0
) {
    /** True when a call may proceed (and consumes the slot). Never throws. */
    fun takeSlot(now: Long): Boolean {
        if (now - windowStart >= windowMillis) {
            windowStart = now
            taken = 0
        }
        if (taken >= maxCalls) return false
        taken++
        return true
    }
}
