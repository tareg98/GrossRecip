package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {

    @Query("SELECT * FROM lists ORDER BY sortOrder ASC")
    fun observeLists(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists ORDER BY sortOrder ASC")
    suspend fun getAllOnce(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: ListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lists: List<ListEntity>)

    @Update
    suspend fun update(list: ListEntity)

    @Delete
    suspend fun delete(list: ListEntity)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM lists")
    suspend fun maxSortOrder(): Int

    /** Wipes all local lists - used on logout so a different account doesn't see stale data. */
    @Query("DELETE FROM lists")
    suspend fun deleteAll()
}
