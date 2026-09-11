package com.shankaravam.festival.core.util

import kotlin.random.Random

private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/** 6-char invite codes (no look-alikes: 0/O, 1/I/L excluded). Uppercase by contract. */
fun generateShareCode(random: Random = Random.Default): String =
    buildString {
        repeat(6) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
    }

fun isValidShareCode(code: String): Boolean =
    code.length == 6 && code.all { it in CODE_ALPHABET }
