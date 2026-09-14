package com.shankaravam.festival.domain.model

import com.shankaravam.festival.core.tts.AnnouncementLanguage
import com.shankaravam.festival.core.tts.buildDonationAnnouncement
import com.shankaravam.festival.core.tts.buildRosterItemAnnouncement
import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.domain.usecase.calculateBalance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * T0.2 effective-amount matrix: latest-wins reader, writer chaining,
 * balance/voice agreement. Pure JVM — no Android needed.
 */
class EffectiveAmountsTest {

    private fun correction(
        id: String,
        targetId: String,
        type: CorrectionTargetType,
        original: Double,
        delta: Double,
        createdAt: Long
    ) = Correction(
        id = id,
        eventId = "e1",
        targetRecordId = targetId,
        targetType = type,
        originalAmount = original,
        deltaAmount = delta,
        reason = "fix",
        createdAt = createdAt
    )

    private fun donation(id: String, amount: Double, status: DonationStatus = DonationStatus.RECEIVED) =
        Donation(id = id, eventId = "e1", donorName = "Donor $id", amount = amount, status = status, addedTime = 1L)

    private fun expense(id: String, amount: Double, status: ExpenseStatus = ExpenseStatus.ACTIVE) =
        Expense(id = id, eventId = "e1", amount = amount, description = "d", category = "c", dateMillis = 1L, status = status)

    @Test
    fun no_corrections_returns_base_row() {
        assertEquals(500.0, effectiveDonationAmount(donation("d1", 500.0), emptyList()))
        assertEquals(200.0, effectiveExpenseAmount(expense("x1", 200.0), emptyList()))
    }

    @Test
    fun latest_wins_500_600_700_regression_sum_of_deltas_is_wrong() {
        // Old writer semantics: every row carries base-original (500).
        val log = listOf(
            correction("c1", "d1", CorrectionTargetType.DONATION, 500.0, 100.0, 1000L),
            correction("c2", "d1", CorrectionTargetType.DONATION, 500.0, 200.0, 2000L)
        )
        // Σ-deltas would give 500+100+200 = 800 (wrong); latest gives 700.
        assertEquals(700.0, effectiveDonationAmount(donation("d1", 500.0), log))
    }

    @Test
    fun incremental_writer_rows_chain_correctly_without_backfill() {
        // Fixed writer semantics: each row chains off the effective figure.
        val log = listOf(
            correction("c1", "d1", CorrectionTargetType.DONATION, 500.0, 100.0, 1000L),
            correction("c2", "d1", CorrectionTargetType.DONATION, 600.0, 100.0, 2000L),
            correction("c3", "d1", CorrectionTargetType.DONATION, 700.0, 50.0, 3000L)
        )
        assertEquals(750.0, effectiveDonationAmount(donation("d1", 500.0), log))
    }

    @Test
    fun same_millis_tie_breaks_by_id_deterministically() {
        // UUIDs carry no time order; same-millis ties are arbitrary but stable.
        val log = listOf(
            correction("c-1", "d1", CorrectionTargetType.DONATION, 500.0, 100.0, 1000L),
            correction("c-2", "d1", CorrectionTargetType.DONATION, 500.0, 200.0, 1000L)
        )
        assertEquals(700.0, effectiveDonationAmount(donation("d1", 500.0), log))
        assertEquals(
            effectiveDonationAmount(donation("d1", 500.0), log),
            effectiveDonationAmount(donation("d1", 500.0), log.reversed())
        )
    }

    @Test
    fun mixed_targets_and_types_are_filtered() {
        val log = listOf(
            correction("c1", "other", CorrectionTargetType.DONATION, 1.0, 9999.0, 3000L),
            correction("c2", "d1", CorrectionTargetType.EXPENSE, 1.0, 9999.0, 3000L),
            correction("c3", "d1", CorrectionTargetType.DONATION, 500.0, 4500.0, 2000L)
        )
        assertEquals(5000.0, effectiveDonationAmount(donation("d1", 500.0), log))
        // Mirror: expense ignores donation-typed rows.
        val xlog = listOf(
            correction("c4", "x1", CorrectionTargetType.DONATION, 1.0, 9999.0, 3000L),
            correction("c5", "x1", CorrectionTargetType.EXPENSE, 200.0, 50.0, 2000L)
        )
        assertEquals(250.0, effectiveExpenseAmount(expense("x1", 200.0), xlog))
    }

    @Test
    fun negative_effective_floors_at_zero() {
        val log = listOf(
            correction("c1", "d1", CorrectionTargetType.DONATION, 500.0, -600.0, 1000L)
        )
        assertEquals(0.0, effectiveDonationAmount(donation("d1", 500.0), log))
    }

    @Test
    fun groupCorrectionsByTarget_groups_by_record_id() {
        val log = listOf(
            correction("c1", "d1", CorrectionTargetType.DONATION, 500.0, 100.0, 1000L),
            correction("c2", "d1", CorrectionTargetType.DONATION, 500.0, 200.0, 2000L),
            correction("c3", "x1", CorrectionTargetType.EXPENSE, 200.0, 50.0, 1500L)
        )
        val grouped = groupCorrectionsByTarget(log)
        assertEquals(2, grouped.size)
        assertEquals(2, grouped["d1"]!!.size)
        assertEquals(1, grouped["x1"]!!.size)
    }

    @Test
    fun balance_uses_effective_for_cash_pledged_and_expenses() {
        val donations = listOf(
            donation("d1", 500.0, DonationStatus.RECEIVED),
            donation("d2", 10000.0, DonationStatus.PLEDGED)
        )
        val expenses = listOf(expense("x1", 200.0))
        val grouped = groupCorrectionsByTarget(
            listOf(
                correction("c1", "d1", CorrectionTargetType.DONATION, 500.0, 4500.0, 2000L),
                correction("c2", "d2", CorrectionTargetType.DONATION, 10000.0, -4000.0, 2000L),
                correction("c3", "x1", CorrectionTargetType.EXPENSE, 200.0, 500.0, 2000L)
            )
        )
        val snap = calculateBalance(donations, expenses, grouped)
        assertEquals(5000.0, snap.cashCollected)
        assertEquals(6000.0, snap.pledgedTotal)
        assertEquals(700.0, snap.expenseTotal)
        assertEquals(4300.0, snap.balance)
    }

    @Test
    fun balance_excludes_cancelled_and_non_cash_even_with_corrections() {
        val donations = listOf(
            donation("d1", 500.0, DonationStatus.CANCELLED),
            Donation(id = "d2", eventId = "e1", donorName = "Rice", amount = 0.0, status = DonationStatus.RECEIVED, isNonCash = true, addedTime = 1L)
        )
        val grouped = groupCorrectionsByTarget(
            listOf(
                correction("c1", "d1", CorrectionTargetType.DONATION, 500.0, 4500.0, 2000L),
                correction("c2", "d2", CorrectionTargetType.DONATION, 0.0, 9999.0, 2000L)
            )
        )
        val snap = calculateBalance(donations, expenses = emptyList(), correctionsByTarget = grouped)
        assertEquals(0.0, snap.cashCollected)
        assertEquals(0.0, snap.balance)
        assertEquals(1, snap.nonCashCount)
    }

    @Test
    fun balance_default_param_keeps_old_call_sites_raw() {
        val donations = listOf(donation("d1", 500.0))
        assertEquals(
            calculateBalance(donations, emptyList()),
            calculateBalance(donations, emptyList(), emptyMap())
        )
    }

    @Test
    fun voice_builders_speak_effective_figure_by_default_raw() {
        val d = donation("d1", 500.0).copy(donorName = "Ramesh")
        val raw = buildDonationAnnouncement(d, "Fest", AnnouncementLanguage.ENGLISH)
        val fixed = buildDonationAnnouncement(d, "Fest", AnnouncementLanguage.ENGLISH, effectiveAmount = 5000.0)
        assertTrue(formatInr(500.0) in raw)
        assertTrue(formatInr(5000.0) in fixed)
        assertNotEquals(raw, fixed)

        val rawRoster = buildRosterItemAnnouncement(d, AnnouncementLanguage.ENGLISH)
        val fixedRoster = buildRosterItemAnnouncement(d, AnnouncementLanguage.ENGLISH, effectiveAmount = 5000.0)
        assertTrue(formatInr(5000.0) in fixedRoster)
        assertNotEquals(rawRoster, fixedRoster)
    }

    @Test
    fun legacy_cache_trusted_only_without_known_correction() {
        // F1: uncorrected rows keep the one-release dual-read transition…
        assertTrue(legacyCacheCovers(500.0, null))
        assertTrue(legacyCacheCovers(500.0, 500.0))
        // …corrected rows never play donation-keyed legacy clips.
        assertFalse(legacyCacheCovers(500.0, 5000.0))
        assertFalse(legacyCacheCovers(500.0, 0.0))
    }

    @Test
    fun voice_telugu_changes_words_with_effective_amount() {
        val d = Donation(
            id = "d1", eventId = "e1", donorName = "Ramesh",
            pronunciationText = "రమేష్", amount = 500.0, addedTime = 1L
        )
        val raw = buildDonationAnnouncement(d, "ఉత్సవం", AnnouncementLanguage.TELUGU)
        val fixed = buildDonationAnnouncement(d, "ఉత్సవం", AnnouncementLanguage.TELUGU, effectiveAmount = 5000.0)
        assertNotEquals(raw, fixed)
        assertTrue("ఐదు వేల" in fixed)
    }
}
