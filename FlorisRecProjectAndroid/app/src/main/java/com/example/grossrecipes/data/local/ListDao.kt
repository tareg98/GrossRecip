package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {

    @Query("SELECT * FROM lists ORDER BY sortOrder ASC")
    fun observeLists(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists ORDER BY sortOrder ASC")
    suspend fun getAllOnce(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ListEntity?

    // Real @Upsert (SQL "INSERT ... ON CONFLICT DO UPDATE"), NOT
    // @Insert(onConflict = REPLACE). REPLACE resolves a conflict by deleting
    // the existing row and inserting a new one - and since list_items has an
    // ON DELETE CASCADE foreign key to lists.id, that delete-then-insert wiped
    // every item in a list any time we updated the list itself (share,
    // recolor, toggle the checked-section arrow, etc). @Upsert does a real
    // UPDATE when the row exists, so the FK cascade never fires.
    @Upsert
    suspend fun upsert(list: ListEntity)

    @Upsert
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
