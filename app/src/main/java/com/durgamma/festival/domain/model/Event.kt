package com.durgamma.festival.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Event(
    val id: String,
    val name: String,
    val templeName: String,
    val location: String,
    val startDateMillis: Long?,
    val endDateMillis: Long?,
    val defaultLanguage: String = "te",
    val status: EventStatus = EventStatus.ACTIVE,
    val globalHeadId: String = "",
    val creatorId: String = "",
    val deviceId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val version: Long = 1L,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
