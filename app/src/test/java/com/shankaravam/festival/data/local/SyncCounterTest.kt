package com.shankaravam.festival.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F4: member-doc counter pick. The roster title must be a human name whenever
 * one exists — never a `Counter-XXXX` placeholder while a Google identity is
 * on file.
 */
class SyncCounterTest {

    @Test
    fun entered_counter_wins_over_google_name() {
        assertEquals("Main Gate", pickSyncCounter("Main Gate", "Ramesh Kumar"))
    }

    @Test
    fun blank_counter_falls_back_to_google_name() {
        assertEquals("Ramesh Kumar", pickSyncCounter("", "Ramesh Kumar"))
        assertEquals("Ramesh Kumar", pickSyncCounter("   ", "Ramesh Kumar"))
        assertEquals("Ramesh Kumar", pickSyncCounter(null, "Ramesh Kumar"))
    }

    @Test
    fun nothing_on_file_omits_counter() {
        assertNull(pickSyncCounter(null, null))
        assertNull(pickSyncCounter("", "  "))
    }

    @Test
    fun picks_are_trimmed() {
        assertEquals("Gate 2", pickSyncCounter("  Gate 2  ", "Ramesh"))
        assertEquals("Ramesh", pickSyncCounter(null, "  Ramesh  "))
    }
}
