package com.durgamma.festival.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DonationDao {
    /** Newest first — the counter's default view (G3 adds SQL sort variants). */
    @Query("SELECT * FROM donations WHERE eventId = :eventId ORDER BY addedTime DESC")
    fun observeForEvent(eventId: String): Flow<List<DonationEntity>>

    @Query("SELECT * FROM donations WHERE id = :id")
    fun observeById(id: String): Flow<DonationEntity?>

    /** Rows awaiting WorkManager delta upload (G6). Suspend: one-shot, never observed by UI. */
    @Query("SELECT * FROM donations WHERE syncStatus IN ('LOCAL_ONLY','PENDING_UPLOAD','SYNC_FAILED') ORDER BY createdAt ASC")
    suspend fun pendingSync(): List<DonationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(donation: DonationEntity)

    @Query("UPDATE donations SET status = :status, updatedAt = :now, version = version + 1, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    /**
     * G4 audio-cache bookkeeping. Deliberately bumps neither version nor
     * syncStatus — cached audio is a local derived artifact, not ledger content.
     */
    @Query("UPDATE donations SET audioStatus = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateAudioStatus(id: String, status: String, now: Long)

    // No @Delete: voided rows are flagged CANCELLED, never removed (plan §16).
}
