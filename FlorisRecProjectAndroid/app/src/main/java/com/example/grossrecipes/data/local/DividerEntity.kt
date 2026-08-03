package com.example.grossrecipes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-placed visual divider at a fixed spot in a list, purely a local
 * organizational aid, never synced (see ListsRepository.toggleDivider).
 *
 * Anchored to a raw position ([gapIndex] - 0 means "above the very first
 * item", 1 means "between the 1st and 2nd item", and so on), NOT to a
 * specific item's id. This used to be item-anchored so a divider would stay
 * attached to the same visual gap even as items above it changed - but that
 * meant dragging an item to reorder the list dragged its divider along with
 * it, which looked like the divider belonged to the item instead of the
 * list. Anchoring to a plain position instead means a divider stays exactly
 * where you dropped it - whichever items end up on either side of it as the
 * list gets reordered, added to, or trimmed.
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
    indices = [Index(value = ["listId", "gapIndex"], unique = true)]
)
data class DividerEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val gapIndex: Int
)
