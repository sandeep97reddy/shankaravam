package com.durgamma.festival.domain.usecase

import com.durgamma.festival.core.util.Outcome
import com.durgamma.festival.core.util.newRecordId
import com.durgamma.festival.domain.model.ActivityActions
import com.durgamma.festival.domain.model.ActivityRecord
import com.durgamma.festival.domain.model.Expense
import com.durgamma.festival.domain.model.SyncStatus
import com.durgamma.festival.domain.repository.ActivityRepository
import com.durgamma.festival.domain.repository.ExpenseRepository

class SaveExpenseUseCase(
    private val expenses: ExpenseRepository,
    private val activity: ActivityRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        eventId: String,
        amount: Double,
        description: String,
        category: String,
        dateMillis: Long? = null,
        paidBy: String = "",
        paymentMethod: String = "Cash",
        vendor: String? = null,
        notes: String? = null,
        receiptPath: String? = null,
        addedBy: String = ""
    ): Outcome<Expense> {
        if (amount <= 0) return Outcome.Err("Amount must be greater than zero")
        if (description.isBlank()) return Outcome.Err("Description is required")
        if (category.isBlank()) return Outcome.Err("Category is required")

        val now = clock()
        val expense = Expense(
            id = newRecordId(),
            eventId = eventId,
            amount = amount,
            description = description.trim(),
            category = category.trim(),
            dateMillis = dateMillis ?: now,
            paidBy = paidBy,
            paymentMethod = paymentMethod,
            vendor = vendor?.trim()?.ifEmpty { null },
            notes = notes?.trim()?.ifEmpty { null },
            receiptPath = receiptPath,
            addedBy = addedBy,
            addedTime = now,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        expenses.save(expense)
        activity.log(
            ActivityRecord(
                id = newRecordId(),
                eventId = eventId,
                actionType = ActivityActions.EXPENSE_ADDED,
                details = expense.id,
                actorId = addedBy,
                timestamp = now
            )
        )
        return Outcome.Ok(expense)
    }
}
