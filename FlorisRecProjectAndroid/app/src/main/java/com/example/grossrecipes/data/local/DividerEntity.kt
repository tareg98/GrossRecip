package com.example.grossrecipes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-placed visual divider between two adjacent items in a list, or
 * above the very first item when [afterItemId] is null - purely a local
 * organizational aid, never synced (see ListsRepository.toggleDivider).
 *
 * Anchored to a stable item id rather than a raw position/index, so it stays
 * attached to the same visual gap even as items above it are added, checked
 * off, or removed - an index would silently point at the wrong gap the
 * moment the list around it changes shape.
 *
 * The unique index prevents two dividers ever stacking at the exact same
 * gap - toggling just deletes the one that's there instead of adding a
 * second.
 */
@Entity(
    tableName = "dividers",
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["listId", "afterItemId"], unique = true)]
)
data class DividerEntity(
    @PrimaryKey val id: String,
    val listId: String,
    /** Null means "at the very top of the list, before the first item". */
    val afterItemId: String?
)
