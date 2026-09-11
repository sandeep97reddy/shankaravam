package com.durgamma.festival.domain.usecase

import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.DonationStatus
import com.durgamma.festival.domain.model.Expense
import com.durgamma.festival.domain.model.ExpenseStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateBalanceTest {

    private fun donation(
        name: String,
        amount: Double,
        status: DonationStatus = DonationStatus.RECEIVED,
        isNonCash: Boolean = false
    ) = Donation(
        id = "$name-$amount-$status",
        eventId = "e1",
        donorName = name,
        amount = amount,
        status = status,
        isNonCash = isNonCash,
        addedTime = 1L
    )

    private fun expense(amount: Double, status: ExpenseStatus = ExpenseStatus.ACTIVE) =
        Expense(
            id = "x-$amount-$status",
            eventId = "e1",
            amount = amount,
            description = "d",
            category = "c",
            dateMillis = 1L,
            status = status
        )

    @Test
    fun balance_excludes_pledged_and_cancelled() {
        val donations = listOf(
            donation("Ramesh", 5000.0, DonationStatus.CONFIRMED),
            donation("Sita", 2000.0, DonationStatus.RECEIVED),
            donation("Pledger", 10000.0, DonationStatus.PLEDGED),
            donation("Partial", 3000.0, DonationStatus.PARTIALLY_RECEIVED),
            donation("Dropped", 9999.0, DonationStatus.CANCELLED),
            donation("Rice Donor", 0.0, DonationStatus.RECEIVED, isNonCash = true)
        )
        val expenses = listOf(expense(1500.0), expense(500.0, ExpenseStatus.CANCELLED))

        val snap = calculateBalance(donations, expenses)

        assertEquals(7000.0, snap.cashCollected)
        assertEquals(13000.0, snap.pledgedTotal)
        assertEquals(1, snap.nonCashCount)
        assertEquals(1500.0, snap.expenseTotal)
        assertEquals(5500.0, snap.balance)
        assertEquals(5, snap.donorCount) // cancelled donor excluded
        assertEquals(1, snap.expenseCount)
    }

    @Test
    fun empty_inputs_yield_zero_snapshot() {
        assertEquals(BalanceSnapshot(), calculateBalance(emptyList(), emptyList()))
    }

    @Test
    fun donor_names_deduplicate_case_insensitively() {
        val donations = listOf(
            donation("Ramesh", 100.0),
            donation(" ramesh ", 200.0),
            donation("RAMESH", 300.0)
        )
        assertEquals(1, calculateBalance(donations, emptyList()).donorCount)
    }
}
