package com.shankaravam.festival.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE eventId = :eventId ORDER BY dateMillis DESC")
    fun observeForEvent(eventId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: String): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE syncStatus IN ('LOCAL_ONLY','PENDING_UPLOAD','SYNC_FAILED') ORDER BY createdAt ASC")
    suspend fun pendingSync(): List<ExpenseEntity>

    /**
     * Event-scoped pending rows for cloud upload (P0 fix — see
     * DonationDao.pendingSyncForEvent). Sync must never use the unscoped query.
     */
    @Query("SELECT * FROM expenses WHERE eventId = :eventId AND syncStatus IN ('LOCAL_ONLY','PENDING_UPLOAD','SYNC_FAILED') ORDER BY createdAt ASC")
    suspend fun pendingSyncForEvent(eventId: String): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Query("UPDATE expenses SET status = :status, updatedAt = :now, version = version + 1, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    /** G6 sync bookkeeping — no version bump (see DonationDao). */
    @Query("UPDATE expenses SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncState(id: String, status: String)

    /**
     * Phase-3 receipts (upload path): records the gateway path and re-queues
     * the row for delta upload. Local-only bookkeeping otherwise — version
     * and updatedAt are ledger content and never move here (the push stamp
     * carries visibility to peers via max(local,now) at cloud entry).
     */
    @Query("UPDATE expenses SET receiptUrl = :url, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun attachReceiptUrl(id: String, url: String)

    /**
     * Phase-3 receipts (download path): adopts a peer's gateway path.
     * Sync state untouched — adopting must never re-upload (no ping-pong).
     */
    @Query("UPDATE expenses SET receiptUrl = :url WHERE id = :id")
    suspend fun applyRemoteReceiptUrl(id: String, url: String)

    /** F3 push-stamp mirror (see DonationDao.markSynced). Version untouched. */
    @Query("UPDATE expenses SET syncStatus = :status, updatedAt = :stampedAt WHERE id = :id")
    suspend fun markSynced(id: String, status: String, stampedAt: Long)

    // No @Delete for ledger cancels: cancelled expenses stay in the ledger (plan §18).
    // Event-scoped deletes below exist ONLY for DeleteLocalEventUseCase, gated on
    // isCloudEvent==false (local-only test festivals). Never call for synced events.

    /** Pre-fetch for local scrub file cleanup (Step 1) — local WebP receipt paths. */
    @Query("SELECT receiptPath FROM expenses WHERE eventId = :eventId AND receiptPath IS NOT NULL")
    suspend fun getReceiptPathsForEvent(eventId: String): List<String>

    /** Display-only count for the delete confirmation dialog. */
    @Query("SELECT COUNT(*) FROM expenses WHERE eventId = :eventId")
    suspend fun countForEvent(eventId: String): Int

    /** Local-scrub only (see above). Executed inside withTransaction cascade. */
    @Query("DELETE FROM expenses WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)
}
