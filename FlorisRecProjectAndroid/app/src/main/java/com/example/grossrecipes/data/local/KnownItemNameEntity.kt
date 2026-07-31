package com.example.grossrecipes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A permanent record that some item name has been typed before, for
 * autocomplete suggestions - separate from [ListItemEntity] on purpose, so a
 * name still suggests itself even after every item with that name has since
 * been deleted. Never cleaned up, never synced (gross-recipes-common has no
 * concept of this at all - it's purely a per-device convenience).
 *
 * [normalizedName] (lowercased, trimmed) is the primary key so "Eggs" and
 * "eggs" collapse into one suggestion instead of two; [displayName] keeps
 * whatever casing was actually typed the first time this name was seen, so
 * the suggestion shown to the user looks like real writing, not a forced case.
 */
@Entity(tableName = "known_item_names")
data class KnownItemNameEntity(
    @PrimaryKey val normalizedName: String,
    val displayName: String
)
