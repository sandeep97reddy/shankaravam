package com.durgamma.festival.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Expense(
    val id: String,
    val eventId: String,
    val amount: Double,
    val description: String,
    val category: String,
    val dateMillis: Long,
    val paidBy: String = "",
    val paymentMethod: String = "Cash",
    val vendor: String? = null,
    val notes: String? = null,
    /** Local file path of the WebP-compressed receipt (plan §18). Never a cloud URL in G1–G5. */
    val receiptPath: String? = null,
    val addedBy: String = "",
    val addedTime: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val status: ExpenseStatus = ExpenseStatus.ACTIVE,
    val version: Long = 1L,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD
)
