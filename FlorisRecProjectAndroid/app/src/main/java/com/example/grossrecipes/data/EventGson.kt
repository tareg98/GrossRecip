package com.example.grossrecipes.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.sirolf2009.grossrecipes.event.Event
import java.time.ZonedDateTime
import java.util.UUID

/**
 * gross-recipes-common's [Event] is one interface with ~10 different classes
 * implementing it (ListCreated, ListItemChecked, etc.) - plain Gson has no
 * idea which concrete class a given blob of JSON is supposed to become, so it
 * needs a tag alongside the real fields to know which one to build. This
 * mirrors your friend's own EventGsonInitializer.kt exactly (confirmed from
 * the real source, not guessed): {"clazz": "<fully qualified class name>",
 * "event-value": {...the event's own fields...}}.
 */
private const val CLASS_FIELD = "clazz"
private const val VALUE_FIELD = "event-value"

private class EventTypeAdapter(private val plainGson: Gson) : JsonSerializer<Event>, JsonDeserializer<Event> {
    override fun serialize(src: Event, typeOfSrc: java.lang.reflect.Type, context: JsonSerializationContext): JsonElement {
        return com.google.gson.JsonObject().apply {
            addProperty(CLASS_FIELD, src.javaClass.name)
            add(VALUE_FIELD, plainGson.toJsonTree(src, src.javaClass))
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): Event {
        val obj = json.asJsonObject
        val className = obj.get(CLASS_FIELD)?.asString
            ?: throw IllegalArgumentException("Event JSON missing \"$CLASS_FIELD\": $json")
        @Suppress("UNCHECKED_CAST")
        val concreteClass = Class.forName(className) as Class<out Event>
        return plainGson.fromJson(obj.get(VALUE_FIELD), concreteClass)
    }
}

private class ZonedDateTimeAdapter : JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {
    override fun serialize(src: ZonedDateTime, typeOfSrc: java.lang.reflect.Type, context: JsonSerializationContext) =
        JsonPrimitive(src.toString())

    override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): ZonedDateTime =
        ZonedDateTime.parse(json.asString)
}

private class UuidAdapter : JsonSerializer<UUID>, JsonDeserializer<UUID> {
    override fun serialize(src: UUID, typeOfSrc: java.lang.reflect.Type, context: JsonSerializationContext) =
        JsonPrimitive(src.toString())

    override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): UUID =
        UUID.fromString(json.asString)
}

/** Gson instance that knows how to (de)serialize [Event] (see [EventTypeAdapter]), [ZonedDateTime], and [UUID]. */
val eventGson: Gson = run {
    val plainGson = GsonBuilder()
        .registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter())
        .registerTypeAdapter(UUID::class.java, UuidAdapter())
        .create()
    GsonBuilder()
        .registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter())
        .registerTypeAdapter(UUID::class.java, UuidAdapter())
        .registerTypeAdapter(Event::class.java, EventTypeAdapter(plainGson))
        .create()
}
