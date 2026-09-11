package com.shankaravam.festival.domain.model

import androidx.compose.runtime.Immutable

/** Append-only history row (plan §17). */
@Immutable
data class ActivityRecord(
    val id: String,
    val eventId: String,
    val actionType: String,
    val details: String? = null,
    val actorId: String = "",
    val timestamp: Long = 0L
)

object ActivityActions {
    const val EVENT_CREATED = "event_created"
    const val EVENT_CLOSED = "event_closed"
    const val DONATION_ADDED = "donation_added"
    const val EXPENSE_ADDED = "expense_added"
    const val CORRECTION_ADDED = "correction_added"
    const val RECORD_CANCELLED = "record_cancelled"
    const val STATUS_CHANGED = "status_changed"
}
