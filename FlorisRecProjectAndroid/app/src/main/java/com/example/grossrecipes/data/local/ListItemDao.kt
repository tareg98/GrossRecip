package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ListItemDao {

    @Query("SELECT * FROM list_items")
    fun observeAllItems(): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE listId = :listId")
    suspend fun getForList(listId: String): List<ListItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ListItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
