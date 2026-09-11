package com.durgamma.festival.domain.usecase

import com.durgamma.festival.domain.repository.DonationRepository
import com.durgamma.festival.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Reactive totals for the G3 dashboard. ViewModels collect this with
 * SharingStarted.WhileSubscribed(5000) per the performance skill.
 */
class ObserveEventTotalsUseCase(
    private val donations: DonationRepository,
    private val expenses: ExpenseRepository
) {
    operator fun invoke(eventId: String): Flow<BalanceSnapshot> =
        combine(
            donations.observeForEvent(eventId),
            expenses.observeForEvent(eventId)
        ) { donationList, expenseList -> calculateBalance(donationList, expenseList) }
}
