package com.durgamma.festival.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [Index("eventId"), Index("status"), Index("syncStatus")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val amount: Double,
    val description: String,
    val category: String,
    val dateMillis: Long,
    val paidBy: String,
    val paymentMethod: String,
    val vendor: String?,
    val notes: String?,
    val receiptPath: String?,
    val addedBy: String,
    val addedTime: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val version: Long,
    val syncStatus: String
)
