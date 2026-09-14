package com.shankaravam.festival.domain.model

/**
 * Effective-amount domain (T0.2 — the systemic post-grace fix).
 *
 * Outside the 5-minute grace window the ledger row is never rewritten; fixes
 * are appended as [Correction] rows. Every reader that turns money into
 * totals or speech MUST use the effective figure from here — never the raw
 * `donation.amount` / `expense.amount`.
 *
 * **Latest-wins, NOT sum-of-deltas.** Every post-grace write passes
 * `originalAmount = <current effective amount>`, but rows written before this
 * fix carry `originalAmount = <base row amount>` (the untouched row). So for
 * ₹500 → ₹600 → ₹700 the log holds `(orig 500, +100)` then `(orig 500, +200)`
 * under old semantics: Σ-deltas gives ₹800 (wrong), latest
 * (`500 + 200 = 700`) gives ₹700 (right). Latest-by-`(createdAt, id)` is also
 * correct under the fixed writer semantics (each row's `original → effective`
 * trail reads cleanly), so no backfill is needed — one reader rule covers
 * both generations of rows. Tie-break on `id` keeps same-millis writes
 * deterministic.
 *
 * Defensive floor: results are coerced to `>= 0` (the writer already rejects
 * negative targets in `RecordCorrectionUseCase`, this guards hand-built rows).
 */
fun latestCorrectionForTarget(
    corrections: List<Correction>,
    targetRecordId: String,
    targetType: CorrectionTargetType
): Correction? =
    corrections
        .filter { it.targetRecordId == targetRecordId && it.targetType == targetType }
        .maxWithOrNull(compareBy<Correction> { it.createdAt }.thenBy { it.id })

/** Shared core: latest correction's `original + delta`, else the base row. Never negative. */
fun effectiveAmountForTarget(baseAmount: Double, correctionsForTarget: List<Correction>): Double {
    val latest = correctionsForTarget
        .maxWithOrNull(compareBy<Correction> { it.createdAt }.thenBy { it.id })
    return (latest?.effectiveAmount ?: baseAmount).coerceAtLeast(0.0)
}

/** Current spendable/announceable figure for a donation (cash corrections only matter; non-cash rows carry 0.0). */
fun effectiveDonationAmount(donation: Donation, corrections: List<Correction>): Double =
    effectiveAmountForTarget(
        donation.amount,
        corrections.filter { it.targetRecordId == donation.id && it.targetType == CorrectionTargetType.DONATION }
    )

/** Current figure for an expense. */
fun effectiveExpenseAmount(expense: Expense, corrections: List<Correction>): Double =
    effectiveAmountForTarget(
        expense.amount,
        corrections.filter { it.targetRecordId == expense.id && it.targetType == CorrectionTargetType.EXPENSE }
    )

/** Groups an event's correction log by target record id for balance/voice lookups. */
fun groupCorrectionsByTarget(corrections: List<Correction>): Map<String, List<Correction>> =
    corrections.groupBy { it.targetRecordId }

/**
 * F1: a donation-keyed legacy clip (`donation_{id}_{speaker}.mp3`) is only
 * trustworthy when NO correction is known. A corrected row must regenerate
 * CAS (and speak native meanwhile) — the legacy file speaks the old figure
 * and prefetch must not treat its presence as cached. `null` (unknown)
 * preserves the one-release dual-read transition for unmigrated callers.
 */
fun legacyCacheCovers(baseAmount: Double, effectiveAmount: Double?): Boolean =
    effectiveAmount == null || effectiveAmount == baseAmount
