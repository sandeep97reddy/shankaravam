package com.shankaravam.festival.core.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareCodesTest {

    @Test
    fun generated_codes_are_valid_and_unique() {
        val codes = List(200) { generateShareCode() }.toSet()
        assertTrue(codes.size > 190, "codes should practically never collide")
        codes.forEach { assertTrue(isValidShareCode(it)) }
    }

    @Test
    fun codes_avoid_lookalike_characters() {
        repeat(50) {
            val code = generateShareCode(Random(7))
            assertFalse('0' in code || 'O' in code || '1' in code || 'I' in code || 'L' in code)
        }
    }

    @Test
    fun validation_rejects_garbage() {
        assertFalse(isValidShareCode(""))
        assertFalse(isValidShareCode("ABC12"))
        assertFalse(isValidShareCode("ABC1234"))
        assertFalse(isValidShareCode("abc123"))
        assertFalse(isValidShareCode("ABC 12"))
        assertTrue(isValidShareCode("ABC234"))
    }

    @Test
    fun deterministic_seed_is_stable() {
        assertEquals(generateShareCode(Random(42)), generateShareCode(Random(42)))
    }

    // ---- feature #2: expiry + closure ----

    @Test
    fun ttl_is_ten_days() {
        assertEquals(10L * 24 * 60 * 60 * 1000, CODE_TTL_MILLIS)
    }

    @Test
    fun legacy_codes_without_fields_grandfather_as_live() {
        assertTrue(isCodeLive(null, null, 999_999_999_999L))
    }

    @Test
    fun active_code_with_future_expiry_is_live() {
        val now = 1_700_000_000_000L
        assertTrue(isCodeLive("active", now + 1_000L, now))
        assertTrue(isCodeLive("ACTIVE", now + CODE_TTL_MILLIS, now))
    }

    @Test
    fun expired_or_closed_codes_are_dead() {
        val now = 1_700_000_000_000L
        assertFalse(isCodeLive("active", now - 1L, now))
        assertFalse(isCodeLive("closed", now + CODE_TTL_MILLIS, now))
        assertFalse(isCodeLive("closed", null, now))
        assertFalse(isCodeLive(null, now - 1L, now))
    }

    // ---- F1: parseJoinCode (typed code, QR payload, URL, garbage) ----

    @Test
    fun parse_accepts_raw_code_case_insensitive() {
        assertEquals("ABC234", parseJoinCode("ABC234"))
        assertEquals("ABC234", parseJoinCode("  abc234  "))
    }

    @Test
    fun parse_accepts_qr_payload_and_urls() {
        assertEquals("ABC234", parseJoinCode("shankaravam://join/ABC234"))
        assertEquals("ABC234", parseJoinCode("shankaravam://join/abc234"))
        assertEquals("ABC234", parseJoinCode("https://shankaravam.app/join/ABC234"))
        assertEquals("ABC234", parseJoinCode("shankaravam://join/ABC234?src=whatsapp"))
    }

    @Test
    fun parse_rejects_garbage() {
        assertEquals(null, parseJoinCode(null))
        assertEquals(null, parseJoinCode(""))
        assertEquals(null, parseJoinCode("   "))
        assertEquals(null, parseJoinCode("hello world"))
        assertEquals(null, parseJoinCode("shankaravam://join/ABC12"))
        assertEquals(null, parseJoinCode("shankaravam://join/"))
    }

    // ---- F3: stampForPush (offline rows must surface at cloud-entry time) ----

    @Test
    fun stamp_keeps_fresh_local_stamps() {
        val now = 1_700_000_000_000L
        assertEquals(
            now + 5_000L,
            com.shankaravam.festival.data.remote.stampForPush(now + 5_000L, now)
        )
    }

    @Test
    fun stamp_lifts_stale_offline_rows_to_now() {
        val now = 1_700_000_000_000L
        // 10:00 AM row uploaded at 11:01 AM → stamped 11:01, peers see it.
        assertEquals(now, com.shankaravam.festival.data.remote.stampForPush(now - 3_600_000L, now))
    }
}
