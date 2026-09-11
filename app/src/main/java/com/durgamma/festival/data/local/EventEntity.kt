package com.durgamma.festival.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val templeName: String,
    val location: String,
    val startDateMillis: Long?,
    val endDateMillis: Long?,
    val defaultLanguage: String,
    val status: String,
    val globalHeadId: String,
    val creatorId: String,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val syncStatus: String
)
