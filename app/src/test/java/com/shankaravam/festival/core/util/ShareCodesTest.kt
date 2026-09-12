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
}
