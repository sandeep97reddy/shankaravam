package com.shankaravam.festival.presentation.expense

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.core.export.ReceiptCompressor
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.newRecordId
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.EventStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ExpenseSaveState {
    data object Idle : ExpenseSaveState
    data object Saving : ExpenseSaveState
    data class Error(val message: String) : ExpenseSaveState
    data class Saved(val expenseId: String) : ExpenseSaveState
}

sealed interface ReceiptState {
    data object None : ReceiptState
    data object Attaching : ReceiptState
    data class Attached(val fileName: String, val sizeKb: Long) : ReceiptState
    data class Error(val message: String) : ReceiptState
}

@Immutable
data class ExpenseFormState(
    val amountText: String = "",
    val description: String = "",
    val category: String = "Miscellaneous",
    val customCategory: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val paidBy: String = "",
    val paymentMethod: String = "Cash",
    val vendor: String = "",
    val notes: String = "",
    val receiptPath: String? = null,
    val receiptState: ReceiptState = ReceiptState.None,
    val saveState: ExpenseSaveState = ExpenseSaveState.Idle
) {
    val effectiveCategory: String get() = customCategory.trim().ifEmpty { category }
    val canSave: Boolean
        get() = saveState != ExpenseSaveState.Saving &&
            receiptState != ReceiptState.Attaching &&
            (amountText.toDoubleOrNull() ?: 0.0) > 0 &&
            description.isNotBlank() &&
            effectiveCategory.isNotBlank()
}

val EXPENSE_CATEGORIES = listOf(
    "Decorations", "Food / Annadanam", "Flowers", "Sound / Lighting",
    "Transport", "Priest Fees", "Printing / Banners", "Electricity / Generator",
    "Cleaning", "Materials", "Miscellaneous"
)

class ExpenseEntryViewModel(private val container: AppContainer) : ViewModel() {
    val currentEventId: StateFlow<String?> = container.sessionPrefs.currentEventId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** True while the current festival is CLOSED — the entry screen locks (verdict Q2). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isEventClosed: StateFlow<Boolean> = currentEventId.flatMapLatest { id ->
        if (id == null) flowOf(false)
        else container.eventRepository.observeEvent(id).map { it?.status == EventStatus.CLOSED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _form = MutableStateFlow(ExpenseFormState())
    val form: StateFlow<ExpenseFormState> = _form.asStateFlow()

    fun update(transform: (ExpenseFormState) -> ExpenseFormState) {
        _form.update {
            val next = transform(it)
            if (it.saveState is ExpenseSaveState.Error && next.saveState is ExpenseSaveState.Error) {
                next.copy(saveState = ExpenseSaveState.Idle)
            } else next
        }
    }

    /** Compresses the picked image to ~100KB WebP off the main thread. */
    fun attachReceipt(uri: Uri) {
        _form.update { it.copy(receiptState = ReceiptState.Attaching) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val dest = File(
                        File(container.appContext.filesDir, "receipts"),
                        "receipt_${newRecordId()}.webp"
                    )
                    ReceiptCompressor.compressToWebP(container.appContext.contentResolver, uri, dest)
                }
            }
            _form.update { form ->
                result.fold(
                    onSuccess = { file ->
                        form.copy(
                            receiptPath = file.absolutePath,
                            receiptState = ReceiptState.Attached(
                                fileName = file.name,
                                sizeKb = file.length() / 1024
                            )
                        )
                    },
                    onFailure = { e ->
                        form.copy(
                            receiptState = ReceiptState.Error(
                                e.message ?: "Could not read that image"
                            )
                        )
                    }
                )
            }
        }
    }

    fun clearReceipt() {
        _form.value.receiptPath?.let { runCatching { File(it).delete() } }
        _form.update { it.copy(receiptPath = null, receiptState = ReceiptState.None) }
    }

    fun save(addedBy: String = "") {
        val eventId = currentEventId.value ?: return
        if (isEventClosed.value) {
            _form.update { it.copy(saveState = ExpenseSaveState.Error("This festival is closed — entries are locked.")) }
            return
        }
        // P3 status-aware gate (see DonationEntryViewModel.save): refuse
        // pending/revoked/viewer writes up front instead of stranding them.
        com.shankaravam.festival.domain.model.AccessPolicy.writeBlockedReason(
            com.shankaravam.festival.domain.model.roleOf(container.sessionPrefs.myRole(eventId)),
            com.shankaravam.festival.domain.model.memberStatusOf(container.sessionPrefs.myStatus(eventId))
        )?.let { reason ->
            _form.update { it.copy(saveState = ExpenseSaveState.Error(reason)) }
            return
        }
        val f = _form.value
        val who = addedBy.ifBlank { container.sessionPrefs.attributionName() }
        _form.update { it.copy(saveState = ExpenseSaveState.Saving) }
        viewModelScope.launch {
            val result = container.saveExpense(
                eventId = eventId,
                amount = f.amountText.toDoubleOrNull() ?: 0.0,
                description = f.description,
                category = f.effectiveCategory,
                dateMillis = f.dateMillis,
                paidBy = f.paidBy,
                paymentMethod = f.paymentMethod,
                vendor = f.vendor.ifBlank { null },
                notes = f.notes.ifBlank { null },
                receiptPath = f.receiptPath,
                addedBy = who
            )
            _form.update {
                when (result) {
                    is Outcome.Ok -> ExpenseFormState(saveState = ExpenseSaveState.Saved(result.value.id))
                    is Outcome.Err -> it.copy(saveState = ExpenseSaveState.Error(result.message))
                }
            }
            // F3 immediate upload leg (see DonationEntryViewModel; Gap-1
            // isCloudEvent gate keeps unpublished events offline).
            // Coalesced (45 s window): rapid entries share one sync run.
            if (result is Outcome.Ok && container.sessionPrefs.cloudSyncEnabled && container.sessionPrefs.isCloudEvent(eventId)) {
                launch { runCatching { container.syncService.syncEventSoon(eventId) } }
            }
            // Phase-3 receipts: background gateway PUT (CONNECTED + backoff).
            // The worker no-ops quietly when no gateway URL is configured.
            if (result is Outcome.Ok && result.value.receiptPath != null) {
                runCatching {
                    com.shankaravam.festival.data.work.ReceiptUploadWorker.schedule(
                        container.appContext, result.value.id, eventId
                    )
                }
            }
        }
    }

    fun consumeSaved() {
        _form.update { ExpenseFormState() }
    }
}
