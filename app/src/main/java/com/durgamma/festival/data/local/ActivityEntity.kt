package com.durgamma.festival.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activities",
    indices = [Index("eventId"), Index("timestamp")]
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val actionType: String,
    val details: String?,
    val actorId: String,
    val timestamp: Long
)
