package com.durgamma.festival.domain.usecase

import androidx.compose.runtime.Immutable
import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.DonationStatus
import com.durgamma.festival.domain.model.Expense
import com.durgamma.festival.domain.model.ExpenseStatus

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

fun calculateBalance(donations: List<Donation>, expenses: List<Expense>): BalanceSnapshot {
    val live = donations.filter { it.status != DonationStatus.CANCELLED }
    val cash = live.filter { it.countsTowardBalance }.sumOf { it.amount }
    val pledged = live.filter {
        it.status == DonationStatus.PLEDGED || it.status == DonationStatus.PARTIALLY_RECEIVED
    }.sumOf { it.amount }
    val activeExpenses = expenses.filter { it.status == ExpenseStatus.ACTIVE }
    return BalanceSnapshot(
        cashCollected = cash,
        pledgedTotal = pledged,
        nonCashCount = live.count { it.isNonCash },
        expenseTotal = activeExpenses.sumOf { it.amount },
        balance = cash - activeExpenses.sumOf { it.amount },
        donorCount = live.map { it.donorName.trim().lowercase() }.toSet().size,
        expenseCount = activeExpenses.size
    )
}
