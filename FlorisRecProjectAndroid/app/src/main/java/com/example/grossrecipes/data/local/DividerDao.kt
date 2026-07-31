package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DividerDao {

    @Query("SELECT * FROM dividers")
    fun observeAll(): Flow<List<DividerEntity>>

    // "IS", not "=" - afterItemId is nullable (null means "top of list"),
    // and SQL's "=" never matches against NULL the way you'd want here.
    @Query("SELECT * FROM dividers WHERE listId = :listId AND afterItemId IS :afterItemId LIMIT 1")
    suspend fun getAt(listId: String, afterItemId: String?): DividerEntity?

    @Upsert
    suspend fun upsert(divider: DividerEntity)

    @Query("DELETE FROM dividers WHERE listId = :listId AND afterItemId IS :afterItemId")
    suspend fun deleteAt(listId: String, afterItemId: String?)

    /** Cleans up a divider anchored to an item that just got deleted, so it doesn't linger orphaned. */
    @Query("DELETE FROM dividers WHERE afterItemId = :itemId")
    suspend fun deleteByAnchorItem(itemId: String)

    /** Wipes all local dividers - used on logout so a different account doesn't see stale data. */
    @Query("DELETE FROM dividers")
    suspend fun deleteAll()
}
