package com.shankaravam.festival.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "donations",
    indices = [Index("eventId"), Index("status"), Index("syncStatus")]
)
data class DonationEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val donorName: String,
    val pronunciationText: String?,
    val amount: Double,
    val currency: String,
    val isNonCash: Boolean,
    val itemDescription: String?,
    val quantity: Double?,
    val unit: String?,
    val paymentMethod: String,
    val tags: List<String>,
    val status: String,
    val announcementEnabled: Boolean,
    val audioStatus: String,
    val notes: String?,
    val addedBy: String,
    val addedTime: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val syncStatus: String
)
