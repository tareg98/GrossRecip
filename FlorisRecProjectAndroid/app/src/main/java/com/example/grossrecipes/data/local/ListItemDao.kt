package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ListItemDao {

    @Query("SELECT * FROM list_items")
    fun observeAllItems(): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE listId = :listId")
    suspend fun getForList(listId: String): List<ListItemEntity>

    // Real @Upsert, not @Insert(onConflict = REPLACE) - see ListDao.upsert for
    // why REPLACE is dangerous with foreign keys (not the direct cause of the
    // list-wiping bug here, since nothing points *at* list_items, but kept
    // consistent so the same mistake can't bite us later).
    @Upsert
    suspend fun upsert(item: ListItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ListItemEntity>)

    @Update
    suspend fun update(item: ListItemEntity)

    @Delete
    suspend fun delete(item: ListItemEntity)

    @Query("DELETE FROM list_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM list_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ListItemEntity?

    /** Wipes all local items - used on logout so a different account doesn't see stale data. */
    @Query("DELETE FROM list_items")
    suspend fun deleteAll()
}
