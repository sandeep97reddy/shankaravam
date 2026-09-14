package com.shankaravam.festival.domain.usecase

import com.shankaravam.festival.domain.model.groupCorrectionsByTarget
import com.shankaravam.festival.domain.repository.CorrectionRepository
import com.shankaravam.festival.domain.repository.DonationRepository
import com.shankaravam.festival.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Reactive totals for the G3 dashboard. ViewModels collect this with
 * SharingStarted.WhileSubscribed(5000) per the performance skill.
 * T0.2: combines the correction log as a third flow so dashboard totals are
 * effective (post-correction) amounts.
 */
class ObserveEventTotalsUseCase(
    private val donations: DonationRepository,
    private val expenses: ExpenseRepository,
    private val corrections: CorrectionRepository
) {
    operator fun invoke(eventId: String): Flow<BalanceSnapshot> =
        combine(
            donations.observeForEvent(eventId),
            expenses.observeForEvent(eventId),
            corrections.observeForEvent(eventId)
        ) { donationList, expenseList, correctionList ->
            calculateBalance(donationList, expenseList, groupCorrectionsByTarget(correctionList))
        }
}
