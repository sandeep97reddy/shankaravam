package com.shankaravam.festival.presentation.donation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.DonationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Error(val message: String) : SaveState
    data class Saved(val donationId: String) : SaveState
}

@Immutable
data class DonationFormState(
    val donorName: String = "",
    val pronunciation: String = "",
    val amountText: String = "",
    val isNonCash: Boolean = false,
    val itemDescription: String = "",
    val quantityText: String = "",
    val unit: String = "",
    val paymentMethod: String = "Cash",
    val tags: List<String> = emptyList(),
    val newTagText: String = "",
    val status: DonationStatus = DonationStatus.RECEIVED,
    val announcementEnabled: Boolean = true,
    val notes: String = "",
    val saveState: SaveState = SaveState.Idle
) {
    val canSave: Boolean
        get() = saveState != SaveState.Saving &&
            donorName.isNotBlank() &&
            ((amountText.toDoubleOrNull() ?: 0.0) > 0 || itemDescription.isNotBlank())
}

val PAYMENT_METHODS = listOf("Cash", "UPI", "Bank Transfer", "Cheque")
val TAG_SUGGESTIONS = listOf(
    "Cash", "UPI", "Sponsor", "Saree", "Flowers",
    "Rice", "Food", "Decoration", "Material", "Service", "Other"
)

class DonationEntryViewModel(container: AppContainer) : ViewModel() {
    private val saveDonation = container.saveDonation
    val currentEventId: StateFlow<String?> = container.sessionPrefs.currentEventId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val prefs: SessionPrefs = container.sessionPrefs

    private val _form = MutableStateFlow(DonationFormState())
    val form: StateFlow<DonationFormState> = _form.asStateFlow()

    fun update(transform: (DonationFormState) -> DonationFormState) {
        _form.update {
            // Any edit after an error clears the error; after Saved stays until consumed.
            val next = transform(it)
            if (it.saveState is SaveState.Error && next.saveState is SaveState.Error) {
                next.copy(saveState = SaveState.Idle)
            } else next
        }
    }

    fun toggleTag(tag: String) = _form.update {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun addCustomTag() = _form.update {
        val tag = it.newTagText.trim()
        if (tag.isEmpty() || tag in it.tags) it.copy(newTagText = "")
        else it.copy(tags = it.tags + tag, newTagText = "")
    }

    fun save(addedBy: String = "") {
        val eventId = currentEventId.value ?: return
        val f = _form.value
        _form.update { it.copy(saveState = SaveState.Saving) }
        viewModelScope.launch {
            val result = saveDonation(
                eventId = eventId,
                donorName = f.donorName,
                amount = f.amountText.toDoubleOrNull() ?: 0.0,
                isNonCash = f.isNonCash,
                itemDescription = f.itemDescription.ifBlank { null },
                quantity = f.quantityText.toDoubleOrNull(),
                unit = f.unit.ifBlank { null },
                pronunciationText = f.pronunciation.ifBlank { null },
                paymentMethod = f.paymentMethod,
                tags = f.tags,
                status = f.status,
                announcementEnabled = f.announcementEnabled,
                notes = f.notes.ifBlank { null },
                addedBy = addedBy
            )
            _form.update {
                when (result) {
                    is com.shankaravam.festival.core.util.Outcome.Ok ->
                        DonationFormState(saveState = SaveState.Saved(result.value.id))
                    is com.shankaravam.festival.core.util.Outcome.Err ->
                        it.copy(saveState = SaveState.Error(result.message))
                }
            }
        }
    }

    fun consumeSaved() {
        _form.update { DonationFormState() }
    }

    fun currentSortHint(): String = prefs.donationSort
}
