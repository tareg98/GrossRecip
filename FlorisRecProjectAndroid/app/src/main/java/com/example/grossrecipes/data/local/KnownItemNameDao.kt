package com.example.grossrecipes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownItemNameDao {

    @Query("SELECT * FROM known_item_names ORDER BY displayName ASC")
    fun observeAll(): Flow<List<KnownItemNameEntity>>

    // IGNORE, not REPLACE - the first casing a name was ever typed in is the
    // one that keeps showing up as the suggestion, not whatever casing
    // happened to be typed most recently.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: KnownItemNameEntity)

    /** Wipes all local name history - used on logout so a different account doesn't see stale data. */
    @Query("DELETE FROM known_item_names")
    suspend fun deleteAll()
}
