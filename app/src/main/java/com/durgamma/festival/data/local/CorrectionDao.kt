package com.durgamma.festival.data.local

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

    // Append-only: no update, no delete.
}
