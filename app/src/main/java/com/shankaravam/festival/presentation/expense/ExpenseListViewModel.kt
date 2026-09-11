package com.shankaravam.festival.presentation.expense

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.newRecordId
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.ActivityActions
import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.AccessPolicy
import com.shankaravam.festival.domain.model.roleOf
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.CorrectionTargetType
import com.shankaravam.festival.domain.model.Expense
import com.shankaravam.festival.domain.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ExpenseListUiState(
    val expenses: List<Expense> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val categoryFilter: String? = null,
    val availableCategories: List<String> = emptyList(),
    val hasEvent: Boolean = false,
    val lastCorrection: Correction? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseListViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.expenseRepository
    private val activityRepo = container.activityRepository

    private val query = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<String?>(null)
    private val lastCorrection = MutableStateFlow<Correction?>(null)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ExpenseListUiState> =
        container.sessionPrefs.currentEventId.flatMapLatest { eventId ->
            if (eventId == null) {
                flowOf(ExpenseListUiState())
            } else {
                combine(
                    repo.observeForEvent(eventId),
                    query,
                    categoryFilter
                ) { expenses: List<Expense>, q: String, category: String? ->
                    val filtered = expenses.filter { e ->
                        (category == null || e.category == category) &&
                            (q.isBlank() ||
                                e.description.contains(q, ignoreCase = true) ||
                                (e.vendor?.contains(q, ignoreCase = true) == true))
                    }
                    ExpenseListUiState(
                        expenses = filtered,
                        totalCount = expenses.size,
                        query = q,
                        categoryFilter = category,
                        availableCategories = expenses.map { it.category }.distinct().sorted(),
                        hasEvent = true
                    )
                }.combine(lastCorrection) { state, correction ->
                    state.copy(lastCorrection = correction)
                }.combine(error) { state, err ->
                    state.copy(error = err)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseListUiState())

    fun setQuery(q: String) { query.value = q }
    fun setCategory(category: String?) { categoryFilter.value = category }

    fun consumeError() { error.value = null }
    fun consumeCorrection() { lastCorrection.value = null }

    /** Cancel keeps the row (plan §18) and logs who voided it. */
    fun cancelExpense(expense: Expense, actor: String = "") {
        if (!AccessPolicy.canCancelExpense(roleOf(container.sessionPrefs.myRole(expense.eventId)))) {
            error.value = "Cancelling expenses needs a collector role."
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.cancel(expense.id, now)
            activityRepo.log(
                ActivityRecord(
                    id = newRecordId(),
                    eventId = expense.eventId,
                    actionType = ActivityActions.RECORD_CANCELLED,
                    details = expense.id,
                    actorId = actor,
                    timestamp = now
                )
            )
        }
    }

    /** Amount fix: grace-window direct edit, otherwise appended Correction. */
    fun correctExpense(expense: Expense, newAmount: Double, reason: String, actor: String = "") {
        if (!AccessPolicy.canCorrect(roleOf(container.sessionPrefs.myRole(expense.eventId)))) {
            error.value = "Fixing entries needs a collector role."
            return
        }
        viewModelScope.launch {
            when (
                val result = container.correctRecord(
                    eventId = expense.eventId,
                    targetRecordId = expense.id,
                    targetType = CorrectionTargetType.EXPENSE,
                    originalAmount = expense.amount,
                    addedTimeMillis = expense.addedTime,
                    newAmount = newAmount,
                    reason = reason,
                    correctedBy = actor
                ) {
                    val now = System.currentTimeMillis()
                    repo.save(
                        expense.copy(
                            amount = newAmount,
                            updatedAt = now,
                            version = expense.version + 1,
                            syncStatus = SyncStatus.PENDING_UPLOAD
                        )
                    )
                }
            ) {
                is Outcome.Ok -> lastCorrection.value = result.value
                is Outcome.Err -> error.value = result.message
            }
        }
    }
}
