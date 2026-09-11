package com.durgamma.festival.domain.repository

import com.durgamma.festival.domain.model.ActivityRecord
import com.durgamma.festival.domain.model.Correction
import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.DonationStatus
import com.durgamma.festival.domain.model.Event
import com.durgamma.festival.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun observeEvents(): Flow<List<Event>>
    fun observeEvent(id: String): Flow<Event?>
    suspend fun save(event: Event)
    suspend fun closeEvent(id: String, now: Long = System.currentTimeMillis())
}

interface DonationRepository {
    fun observeForEvent(eventId: String): Flow<List<Donation>>
    fun observeById(id: String): Flow<Donation?>
    suspend fun save(donation: Donation)
    suspend fun updateStatus(id: String, status: DonationStatus, now: Long = System.currentTimeMillis())
    suspend fun pendingSync(): List<Donation>
}

interface ExpenseRepository {
    fun observeForEvent(eventId: String): Flow<List<Expense>>
    fun observeById(id: String): Flow<Expense?>
    suspend fun save(expense: Expense)
    suspend fun cancel(id: String, now: Long = System.currentTimeMillis())
    suspend fun pendingSync(): List<Expense>
}

interface CorrectionRepository {
    fun observeForEvent(eventId: String): Flow<List<Correction>>
    fun observeForTarget(targetId: String): Flow<List<Correction>>
    suspend fun record(correction: Correction)
}

interface ActivityRepository {
    fun observeRecent(eventId: String, limit: Int = 200): Flow<List<ActivityRecord>>
    suspend fun log(entry: ActivityRecord)
}
