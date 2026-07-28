package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutboxEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: OutboxEventEntity)

    @Query("SELECT * FROM outbox_events ORDER BY seq ASC")
    suspend fun getAll(): List<OutboxEventEntity>

    @Query("DELETE FROM outbox_events WHERE seq = :seq")
    suspend fun deleteBySeq(seq: Long)

    /** Wipes the whole outbox - used on logout, and after a fully successful push. */
    @Query("DELETE FROM outbox_events")
    suspend fun deleteAll()
}
