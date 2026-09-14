package com.shankaravam.festival.presentation.donation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.di.AppContainer
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.EventStatus
import com.shankaravam.festival.domain.model.HONORIFIC_SRI
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
    val honorific: String = HONORIFIC_SRI,
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
    val saveState: SaveState = SaveState.Idle,
    /** Set when the 60 s duplicate guard fires — the dialog owns it. */
    val duplicatePrompt: DuplicateInfo? = null
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

class DonationEntryViewModel(private val container: AppContainer) : ViewModel() {
    private val saveDonation = container.saveDonation
    private val donationRepo = container.donationRepository
    private val eventRepo = container.eventRepository
    val currentEventId: StateFlow<String?> = container.sessionPrefs.currentEventId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** True while the current festival is CLOSED — the entry screen locks (verdict Q2). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isEventClosed: StateFlow<Boolean> = currentEventId.flatMapLatest { id ->
        if (id == null) flowOf(false)
        else eventRepo.observeEvent(id).map { it?.status == EventStatus.CLOSED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val prefs: SessionPrefs = container.sessionPrefs

    private val _form = MutableStateFlow(DonationFormState())
    val form: StateFlow<DonationFormState> = _form.asStateFlow()

    fun update(transform: (DonationFormState) -> DonationFormState) {
        _form.update {
            // Any edit after an error clears the error; after Saved stays until consumed.
            val next = transform(it)
            // Any form edit also retires a stale duplicate prompt (feature #6).
            val unprompted = if (next.duplicatePrompt != null) next.copy(duplicatePrompt = null) else next
            if (it.saveState is SaveState.Error && unprompted.saveState is SaveState.Error) {
                unprompted.copy(saveState = SaveState.Idle)
            } else unprompted
        }
    }

    /** Dialog "Add another": writes despite the guard. "Cancel" dismisses. */
    fun confirmDuplicateSave() {
        if (_form.value.duplicatePrompt == null) return
        save(confirmed = true)
    }

    fun dismissDuplicate() {
        _form.update { it.copy(duplicatePrompt = null) }
    }

    fun toggleTag(tag: String) = _form.update {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun addCustomTag() = _form.update {
        val tag = it.newTagText.trim()
        if (tag.isEmpty() || tag in it.tags) it.copy(newTagText = "")
        else it.copy(tags = it.tags + tag, newTagText = "")
    }

    /**
     * Save with the 60 s duplicate guard (feature #6): first tap surfaces a
     * confirm dialog instead of writing; [confirmDuplicateSave] re-enters
     * with [confirmed] and writes. Any form edit clears a stale prompt.
     */
    fun save(addedBy: String = "", confirmed: Boolean = false) {
        val eventId = currentEventId.value ?: return
        if (isEventClosed.value) {
            _form.update { it.copy(saveState = SaveState.Error("This festival is closed — entries are locked.")) }
            return
        }
        val f = _form.value
        if (f.duplicatePrompt != null && !confirmed) return
        val who = addedBy.ifBlank { prefs.attributionName() }
        _form.update { it.copy(saveState = SaveState.Saving, duplicatePrompt = null) }
        viewModelScope.launch {
            if (!confirmed) {
                val recent = runCatching { donationRepo.latestForEvent(eventId) }.getOrNull()
                val dup = findDuplicateCandidate(
                    recent = recent,
                    donorName = f.donorName,
                    amount = f.amountText.toDoubleOrNull() ?: 0.0,
                    itemDescription = f.itemDescription.ifBlank { null },
                    isNonCash = f.isNonCash,
                    status = f.status,
                    now = System.currentTimeMillis()
                )
                if (dup != null) {
                    _form.update { it.copy(saveState = SaveState.Idle, duplicatePrompt = dup) }
                    return@launch
                }
            }
            val result = saveDonation(
                eventId = eventId,
                donorName = f.donorName,
                amount = f.amountText.toDoubleOrNull() ?: 0.0,
                isNonCash = f.isNonCash,
                itemDescription = f.itemDescription.ifBlank { null },
                quantity = f.quantityText.toDoubleOrNull(),
                unit = f.unit.ifBlank { null },
                pronunciationText = f.pronunciation.ifBlank { null },
                honorific = f.honorific.ifBlank { HONORIFIC_SRI },
                paymentMethod = f.paymentMethod,
                tags = f.tags,
                status = f.status,
                announcementEnabled = f.announcementEnabled,
                notes = f.notes.ifBlank { null },
                addedBy = who
            )
            _form.update {
                when (result) {
                    is com.shankaravam.festival.core.util.Outcome.Ok ->
                        DonationFormState(saveState = SaveState.Saved(result.value.id))
                    is com.shankaravam.festival.core.util.Outcome.Err ->
                        it.copy(saveState = SaveState.Error(result.message))
                }
            }
            // F3 immediate upload leg: while sync is on AND this event is
            // cloud-published, push now (direct call, not the Worker queue)
            // so peers' foreground listeners fire in ~seconds. Seat-first
            // syncEvent makes this safe for pending/viewers; the isCloudEvent
            // gate keeps unpublished local events fully offline (Gap-1).
            if (result is com.shankaravam.festival.core.util.Outcome.Ok && prefs.cloudSyncEnabled && prefs.isCloudEvent(eventId)) {
                launch { runCatching { container.syncService.syncEvent(eventId) } }
            }
        }
    }

    fun consumeSaved() {
        _form.update { DonationFormState() }
    }

    fun currentSortHint(): String = prefs.donationSort
}
