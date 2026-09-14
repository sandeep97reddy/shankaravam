package com.shankaravam.festival.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionDao {
    @Query("SELECT * FROM corrections WHERE eventId = :eventId ORDER BY createdAt DESC")
    fun observeForEvent(eventId: String): Flow<List<CorrectionEntity>>

    @Query("SELECT * FROM corrections WHERE targetRecordId = :targetId ORDER BY createdAt ASC")
    fun observeForTarget(targetId: String): Flow<List<CorrectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(correction: CorrectionEntity)

    @Query("SELECT * FROM corrections WHERE syncStatus IN ('LOCAL_ONLY','PENDING_UPLOAD','SYNC_FAILED') ORDER BY createdAt ASC")
    suspend fun pendingSync(): List<CorrectionEntity>

    /**
     * Event-scoped pending rows for cloud upload (P0 fix — see
     * DonationDao.pendingSyncForEvent). Sync must never use the unscoped query.
     */
    @Query("SELECT * FROM corrections WHERE eventId = :eventId AND syncStatus IN ('LOCAL_ONLY','PENDING_UPLOAD','SYNC_FAILED') ORDER BY createdAt ASC")
    suspend fun pendingSyncForEvent(eventId: String): List<CorrectionEntity>

    /** G6 sync bookkeeping — no version column exists; append-only anyway. */
    @Query("UPDATE corrections SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncState(id: String, status: String)

    /** F3 corrections ingest: existence check for idempotent set-by-id download. */
    @Query("SELECT COUNT(*) FROM corrections WHERE id = :id")
    suspend fun countById(id: String): Int

    // Append-only: no update, no per-row delete.
    // Event-scoped delete exists ONLY for DeleteLocalEventUseCase, gated on
    // isCloudEvent==false (local-only test festivals). Never call for synced events.
    @Query("DELETE FROM corrections WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)
}
