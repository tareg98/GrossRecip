package com.example.grossrecipes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * This table is a "materialized view," not the source of truth: it holds
 * whatever you get from replaying every LIST_* event applied so far (see
 * ListsRepository.applyEvent). There's no synced/existsOnServer flag on the
 * row anymore - "has this been synced" is now a question about the outbox
 * (OutboxEventEntity), not about the row itself.
 */
@Entity(tableName = "lists")
@TypeConverters(StringListConverter::class)
data class ListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val owner: String,
    val colorHex: String? = null,
    val sharedWith: List<String> = emptyList(),
    val sharedExternally: Boolean = false,
    val sortOrder: Int = 0,
    val checkedSectionExpanded: Boolean = false
)

class StringListConverter {
    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString("|")

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isBlank()) emptyList() else value.split("|")
}
