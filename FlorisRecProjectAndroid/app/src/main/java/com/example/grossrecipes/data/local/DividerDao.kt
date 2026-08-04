package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DividerDao {

    @Query("SELECT * FROM dividers")
    fun observeAll(): Flow<List<DividerEntity>>

    @Query("SELECT * FROM dividers WHERE listId = :listId AND gapIndex = :gapIndex LIMIT 1")
    suspend fun getAt(listId: String, gapIndex: Int): DividerEntity?

    /** One-shot (not a Flow) read of every divider for a single list - used to shift them when an item leaves/rejoins the unchecked list. */
    @Query("SELECT * FROM dividers WHERE listId = :listId")
    suspend fun getAllForList(listId: String): List<DividerEntity>

    @Upsert
    suspend fun upsert(divider: DividerEntity)

    @Query("DELETE FROM dividers WHERE listId = :listId AND gapIndex = :gapIndex")
    suspend fun deleteAt(listId: String, gapIndex: Int)

    /** Wipes all local dividers - used on logout so a different account doesn't see stale data. */
    @Query("DELETE FROM dividers")
    suspend fun deleteAll()
}
