package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: OutboxEventEntity)

    @Query("SELECT * FROM outbox_events ORDER BY seq ASC")
    suspend fun getAll(): List<OutboxEventEntity>

    // A row only ever leaves the outbox once a sync actually round-trips
    // successfully (see ListsRepository.syncPendingChanges) - so this count
    // is the real, live answer to "is everything actually synced," unlike
    // just checking network connectivity (which says nothing about the
    // server rejecting requests, e.g. an expired token).
    @Query("SELECT COUNT(*) FROM outbox_events")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM outbox_events WHERE seq = :seq")
    suspend fun deleteBySeq(seq: Long)

    /** Wipes the whole outbox - used on logout, and after a fully successful push. */
    @Query("DELETE FROM outbox_events")
    suspend fun deleteAll()
}
