package com.example.grossrecipes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Materialized view built by replaying ITEM_* events - see ListEntity for why there's no sync flag here. */
@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ListItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val checked: Boolean
)
