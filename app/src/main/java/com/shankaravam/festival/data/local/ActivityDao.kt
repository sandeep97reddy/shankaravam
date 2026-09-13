package com.shankaravam.festival.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE eventId = :eventId ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(eventId: String, limit: Int = 200): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: ActivityEntity)

    // Append-only: no update, no per-row delete.
    // Event-scoped delete exists ONLY for DeleteLocalEventUseCase, gated on
    // isCloudEvent==false (local-only test festivals). Table is `activities`.
    @Query("DELETE FROM activities WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)
}
