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

/** Invite-code lifetime: 10 days from publish (feature #2). */
const val CODE_TTL_MILLIS: Long = 10L * 24 * 60 * 60 * 1000

/** Code-doc lifecycle: active accepts joins, closed rejects them. */
const val CODE_STATUS_ACTIVE = "active"
const val CODE_STATUS_CLOSED = "closed"

/**
 * True when a code doc still accepts joins. Missing fields (codes
 * published before expiry existed) grandfather as live — otherwise the
 * head's own current code would die on the next join.
 */
fun isCodeLive(status: String?, expiresAt: Long?, now: Long): Boolean {
    if (status != null && status.trim().lowercase() != CODE_STATUS_ACTIVE) return false
    if (expiresAt != null && expiresAt < now) return false
    return true
}
