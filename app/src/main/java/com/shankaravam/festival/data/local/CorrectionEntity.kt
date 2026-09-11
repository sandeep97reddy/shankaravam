package com.shankaravam.festival.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "corrections",
    indices = [Index("eventId"), Index("targetRecordId")]
)
data class CorrectionEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val targetRecordId: String,
    val targetType: String,
    val originalAmount: Double,
    val deltaAmount: Double,
    val reason: String,
    val correctedBy: String,
    val createdAt: Long,
    val syncStatus: String
)
