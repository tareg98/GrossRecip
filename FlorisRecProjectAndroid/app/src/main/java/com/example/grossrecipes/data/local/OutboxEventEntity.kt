package com.example.grossrecipes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.grossrecipes.data.eventGson
import com.sirolf2009.grossrecipes.event.Event

/**
 * The local "outbox": every event created on this phone that hasn't been
 * confirmed by the server yet. Per the event-sourcing design, this is the
 * ONLY thing the app needs to hold onto for sync purposes - once an event is
 * pushed successfully, it's deleted from here (the server now owns the
 * permanent record of it).
 *
 * [seq] is the only thing that's really "ours" - a local auto-increment used
 * both to push events in the exact order they were created on this phone,
 * and as the row's own key for deleting it once synced (unlike our old
 * generic envelope, gross-recipes-common's [Event] has no single universal
 * "this event's own id" field, so [seq] is what we key on locally instead).
 * [event] is stored as JSON under the hood (Room can't store an arbitrary
 * interface-typed column directly) - see [EventConverter].
 */
@Entity(tableName = "outbox_events")
@TypeConverters(EventConverter::class)
data class OutboxEventEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val event: Event
)

class EventConverter {
    @TypeConverter
    fun fromEvent(event: Event): String = eventGson.toJson(event, Event::class.java)

    @TypeConverter
    fun toEvent(json: String): Event = eventGson.fromJson(json, Event::class.java)
}
