package com.shankaravam.festival.domain.repository

import com.shankaravam.festival.domain.model.ActivityRecord
import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.Event
import com.shankaravam.festival.domain.model.Expense
import com.shankaravam.festival.domain.model.SyncStatus
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
    /** G4 audio-cache bookkeeping — never bumps version/sync (local artifact). */
    suspend fun updateAudioStatus(id: String, status: AudioStatus, now: Long = System.currentTimeMillis())
    /** G6 sync bookkeeping — never bumps version (sync is not a ledger edit). */
    suspend fun updateSyncState(id: String, status: SyncStatus)
    suspend fun pendingSync(): List<Donation>
    /** Newest row for the duplicate guard — O(1), never observes. */
    suspend fun latestForEvent(eventId: String): Donation?
}

interface ExpenseRepository {
    fun observeForEvent(eventId: String): Flow<List<Expense>>
    fun observeById(id: String): Flow<Expense?>
    suspend fun save(expense: Expense)
    suspend fun cancel(id: String, now: Long = System.currentTimeMillis())
    /** G6 sync bookkeeping — never bumps version (sync is not a ledger edit). */
    suspend fun updateSyncState(id: String, status: SyncStatus)
    suspend fun pendingSync(): List<Expense>
}

interface CorrectionRepository {
    fun observeForEvent(eventId: String): Flow<List<Correction>>
    fun observeForTarget(targetId: String): Flow<List<Correction>>
    suspend fun record(correction: Correction)
    /** G6 sync bookkeeping for the append-only correction log. */
    suspend fun updateSyncState(id: String, status: SyncStatus)
    suspend fun pendingSync(): List<Correction>
}

interface ActivityRepository {
    fun observeRecent(eventId: String, limit: Int = 200): Flow<List<ActivityRecord>>
    suspend fun log(entry: ActivityRecord)
}
