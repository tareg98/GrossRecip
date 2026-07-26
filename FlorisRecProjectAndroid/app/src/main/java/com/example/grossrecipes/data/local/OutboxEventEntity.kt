package com.example.grossrecipes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * The local "outbox": every event created on this phone that hasn't been
 * confirmed by the server yet. Per the event-sourcing design, this is the
 * ONLY thing the app needs to hold onto for sync purposes - once an event is
 * pushed successfully, it's deleted from here (the server now owns the
 * permanent record of it).
 *
 * [seq] is a local-only auto-increment used purely to guarantee we push
 * events in the exact order they were created on this phone (wall-clock
 * [timestamp] alone could tie if two events happen in the same millisecond).
 * [id] is the event's own id and is what actually gets sent to the server.
 *
 * [payload] is stored as a JSON string under the hood (Room can't store a
 * Map column directly) - see [OutboxPayloadConverter].
 */
@Entity(tableName = "outbox_events")
@TypeConverters(OutboxPayloadConverter::class)
data class OutboxEventEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val id: String,
    val type: String,
    val entityId: String,
    val payload: Map<String, String?>,
    val timestamp: Long,
    val deviceId: String
)

class OutboxPayloadConverter {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String?>>() {}.type

    @TypeConverter
    fun fromPayload(payload: Map<String, String?>): String = gson.toJson(payload)

    @TypeConverter
    fun toPayload(json: String): Map<String, String?> = gson.fromJson(json, mapType) ?: emptyMap()
}
