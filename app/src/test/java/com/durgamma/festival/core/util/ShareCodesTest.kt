package com.durgamma.festival.core.util

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
}
