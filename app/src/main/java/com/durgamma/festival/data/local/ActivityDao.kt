package com.durgamma.festival.data.local

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

    // Append-only: no update, no delete.
}
