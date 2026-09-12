package com.shankaravam.festival.core.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateWindowTest {

    @Test
    fun allows_up_to_max_then_blocks_until_window_resets() {
        val window = RateWindow(maxCalls = 3, windowMillis = 1_000L)
        assertTrue(window.takeSlot(0L))
        assertTrue(window.takeSlot(10L))
        assertTrue(window.takeSlot(20L))
        assertFalse(window.takeSlot(30L))
        // Window expiry resets the count.
        assertTrue(window.takeSlot(1_000L))
    }

    @Test
    fun clock_jump_forward_resets() {
        val window = RateWindow(maxCalls = 1, windowMillis = 60_000L)
        assertTrue(window.takeSlot(0L))
        assertFalse(window.takeSlot(1L))
        assertTrue(window.takeSlot(3_600_000L))
    }
}
