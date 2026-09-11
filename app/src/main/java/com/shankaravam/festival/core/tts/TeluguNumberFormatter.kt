package com.shankaravam.festival.core.tts

import kotlin.math.roundToLong

/**
 * Cash amounts rendered as natural Telugu words (plan §12).
 * Verified examples: 5000 → "ఐదు వేల", 10016 → "పది వేల పదహారు".
 * Pure JVM — unit-tested, no Android dependency.
 */
object TeluguNumberFormatter {

    private val ONES = arrayOf(
        "సున్నా", "ఒకటి", "రెండు", "మూడు", "నాలుగు",
        "ఐదు", "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది",
        "పది", "పదకొండు", "పన్నెండు", "పదమూడు", "పద్నాలుగు",
        "పదిహేను", "పదహారు", "పదిహేడు", "పద్దెనిమిది", "పందొమ్మిది"
    )

    private val TENS = mapOf(
        2 to "ఇరవై", 3 to "ముప్పై", 4 to "నలభై", 5 to "యాభై",
        6 to "అరవై", 7 to "డెబ్బై", 8 to "ఎనభై", 9 to "తొంభై"
    )

    /** Words for a non-negative whole number (0..999,99,99,999). */
    fun wordsForNumber(n: Long): String {
        require(n >= 0) { "negative numbers are not announced" }
        if (n < 20) return ONES[n.toInt()]
        if (n < 100) {
            val ten = TENS[(n / 10).toInt()]!!
            val rem = n % 10
            return if (rem == 0L) ten else "$ten ${ONES[rem.toInt()]}"
        }
        if (n < 1_000) {
            val head = if (n / 100 == 1L) "వంద" else "${ONES[(n / 100).toInt()]} వందల"
            return withRemainder(head, n % 100)
        }
        if (n < 100_000) {
            val q = n / 1_000
            val head = if (q == 1L) "వెయ్యి" else "${wordsForNumber(q)} వేల"
            return withRemainder(head, n % 1_000)
        }
        if (n < 10_000_000) {
            val q = n / 100_000
            val head = if (q == 1L) "ఒక లక్ష" else "${wordsForNumber(q)} లక్షల"
            return withRemainder(head, n % 100_000)
        }
        val q = n / 10_000_000
        val head = if (q == 1L) "ఒక కోటి" else "${wordsForNumber(q)} కోట్ల"
        return withRemainder(head, n % 10_000_000)
    }

    /** "ఐదు వేల రూపాయలు" — paise appended only when present. */
    fun wordsForAmount(amount: Double): String {
        require(amount >= 0) { "negative amounts are not announced" }
        val whole = amount.toLong()
        var paise = ((amount - whole) * 100).roundToLong()
        var rupees = whole
        if (paise == 100L) {
            rupees += 1
            paise = 0
        }
        val base = if (rupees == 0L && paise > 0) {
            ""
        } else {
            "${wordsForNumber(rupees)} రూపాయలు"
        }
        return if (paise > 0) {
            val paiseWords = "${wordsForNumber(paise)} పైసలు"
            if (base.isEmpty()) paiseWords else "$base $paiseWords"
        } else {
            base.ifEmpty { "సున్నా రూపాయలు" }
        }
    }

    private fun withRemainder(head: String, rem: Long): String =
        if (rem == 0L) head else "$head ${wordsForNumber(rem)}"
}
