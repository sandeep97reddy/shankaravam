package com.shankaravam.festival.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MemberNameTest {

    @Test
    fun counter_name_wins_over_google_identity() {
        assertEquals(
            "Counter 2",
            resolveMemberName("Counter 2", "Ramesh", "ramesh@gmail.com", "uid-123456")
        )
    }

    @Test
    fun falls_back_through_display_name_then_email() {
        assertEquals("Ramesh", resolveMemberName(null, "Ramesh", "ramesh@gmail.com", "uid-123456"))
        assertEquals("Ramesh", resolveMemberName("  ", "Ramesh", null, "uid-123456"))
        assertEquals("ramesh@gmail.com", resolveMemberName(null, null, "ramesh@gmail.com", "uid-123456"))
    }

    @Test
    fun blank_identity_falls_back_to_short_id_never_raw_uid() {
        val shown = resolveMemberName(null, null, null, "uid-123456")
        assertEquals("ID: …123456", shown)
    }

    @Test
    fun fully_blank_identity_shows_unknown_counter() {
        assertEquals("Unknown counter", resolveMemberName(null, null, null, ""))
    }
}
