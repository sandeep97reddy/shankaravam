package com.shankaravam.festival.data.local

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

    /**
     * P1 fix: the duplicate guard needs exactly one row. Loading the whole
     * event ledger (observeForEvent + maxBy) is O(N) memory per save tap —
     * this is O(1). createdAt (not addedTime) matches the guard's clock.
     */
    @Query("SELECT * FROM donations WHERE eventId = :eventId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForEvent(eventId: String): DonationEntity?

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

    /**
     * G6 sync bookkeeping. Uploading a row never bumps version — the version
     * counts ledger edits, and sync must not look like one.
     */
    @Query("UPDATE donations SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncState(id: String, status: String)

    // No @Delete for ledger voids: voided rows are flagged CANCELLED, never removed (plan §16).
    // Event-scoped deletes below exist ONLY for DeleteLocalEventUseCase, gated on
    // isCloudEvent==false (local-only test festivals). Never call for synced events.

    /** Pre-fetch for local scrub file cleanup (Step 1) — ids map to cacheDir/audio files. */
    @Query("SELECT id FROM donations WHERE eventId = :eventId")
    suspend fun getDonationIdsForEvent(eventId: String): List<String>

    /** Display-only count for the delete confirmation dialog. */
    @Query("SELECT COUNT(*) FROM donations WHERE eventId = :eventId")
    suspend fun countForEvent(eventId: String): Int

    /** Local-scrub only (see above). Executed inside withTransaction cascade. */
    @Query("DELETE FROM donations WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)
}
