package com.shankaravam.festival.domain.usecase

import androidx.compose.runtime.Immutable
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.Expense
import com.shankaravam.festival.domain.model.ExpenseStatus
import com.shankaravam.festival.domain.model.effectiveDonationAmount
import com.shankaravam.festival.domain.model.effectiveExpenseAmount

/**
 * Balance formula (plan §19): collected confirmed+received cash minus active
 * expenses. Pledged and non-cash never enter the balance.
 * Pure function — safe to call from derivedStateOf (G3).
 */
@Immutable
data class BalanceSnapshot(
    val cashCollected: Double = 0.0,
    val pledgedTotal: Double = 0.0,
    val nonCashCount: Int = 0,
    val expenseTotal: Double = 0.0,
    val balance: Double = 0.0,
    val donorCount: Int = 0,
    val expenseCount: Int = 0
)

/**
 * T0.2: sums **effective** (post-correction) amounts via [correctionsByTarget]
 * (grouped by target record id — see `groupCorrectionsByTarget`). Defaults to
 * empty so pre-migration call sites keep compiling; pass the grouped log
 * everywhere money is totaled.
 */
fun calculateBalance(
    donations: List<Donation>,
    expenses: List<Expense>,
    correctionsByTarget: Map<String, List<Correction>> = emptyMap()
): BalanceSnapshot {
    val live = donations.filter { it.status != DonationStatus.CANCELLED }
    val cash = live.filter { it.countsTowardBalance }
        .sumOf { effectiveDonationAmount(it, correctionsByTarget[it.id].orEmpty()) }
    val pledged = live.filter {
        it.status == DonationStatus.PLEDGED || it.status == DonationStatus.PARTIALLY_RECEIVED
    }.sumOf { effectiveDonationAmount(it, correctionsByTarget[it.id].orEmpty()) }
    val activeExpenses = expenses.filter { it.status == ExpenseStatus.ACTIVE }
    val expenseTotal = activeExpenses
        .sumOf { effectiveExpenseAmount(it, correctionsByTarget[it.id].orEmpty()) }
    return BalanceSnapshot(
        cashCollected = cash,
        pledgedTotal = pledged,
        nonCashCount = live.count { it.isNonCash },
        expenseTotal = expenseTotal,
        balance = cash - expenseTotal,
        donorCount = live.map { it.donorName.trim().lowercase() }.toSet().size,
        expenseCount = activeExpenses.size
    )
}
