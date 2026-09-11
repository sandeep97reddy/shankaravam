package com.shankaravam.festival.core.tts

import kotlin.math.roundToLong

/**
 * Cash amounts rendered as natural Telugu words (plan §12).
 * Formats with authentic Telugu phrasing:
 * - 101 → "నూట ఒకటి" (amounts: "నూట ఒక రూపాయి")
 * - 116 → "నూట పదహారు రూపాయలు"
 * - 501 → "ఐదు వందల ఒకటి" (amounts: "ఐదు వందల ఒక రూపాయి")
 * - 1116 → "వెయ్యి నూట పదహారు రూపాయలు"
 * - 2116 → "రెండు వేల నూట పదహారు రూపాయలు"
 * - 5116 → "ఐదు వేల నూట పదహారు రూపాయలు"
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
            val q = (n / 100).toInt()
            val rem = n % 100
            val head = if (q == 1) {
                if (rem == 0L) "వంద" else "నూట"
            } else {
                "${ONES[q]} వందల"
            }
            return withRemainder(head, rem)
        }
        if (n < 100_000) {
            val q = n / 1_000
            val rem = n % 1_000
            val head = if (q == 1L) "వెయ్యి" else "${wordsForNumber(q)} వేల"
            return withRemainder(head, rem)
        }
        if (n < 10_000_000) {
            val q = n / 100_000
            val rem = n % 100_000
            val head = if (q == 1L) "ఒక లక్ష" else "${wordsForNumber(q)} లక్షల"
            return withRemainder(head, rem)
        }
        val q = n / 10_000_000
        val rem = n % 10_000_000
        val head = if (q == 1L) "ఒక కోటి" else "${wordsForNumber(q)} కోట్ల"
        return withRemainder(head, rem)
    }

    /** "ఐదు వేల రూపాయలు", "నూట ఒక రూపాయి", "వెయ్యి నూట పదహారు రూపాయలు" */
    fun wordsForAmount(amount: Double): String {
        require(amount >= 0) { "negative amounts are not announced" }
        val whole = amount.toLong()
        var paise = ((amount - whole) * 100).roundToLong()
        var rupees = whole
        if (paise == 100L) {
            rupees += 1
            paise = 0
        }
        val base = when {
            rupees == 0L && paise > 0 -> ""
            rupees == 1L -> "ఒక రూపాయి"
            rupees % 10 == 1L && rupees % 100 != 11L -> {
                // Ends in 1 (e.g. 101, 501, 1001, 2101): "నూట ఒక రూపాయి", "ఐదు వందల ఒక రూపాయి"
                val words = wordsForNumber(rupees)
                if (words.endsWith("ఒకటి")) {
                    "${words.removeSuffix("ఒకటి").trimEnd()} ఒక రూపాయి"
                } else {
                    "$words రూపాయలు"
                }
            }
            else -> "${wordsForNumber(rupees)} రూపాయలు"
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
