package com.sirolf2009.grossrecipes.events.entity

import com.google.gson.reflect.TypeToken
import com.sirolf2009.modulith.module.GsonSingleton.gson
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * Postgres has no native "map of string to nullable string" column type, so
 * this stores [Event.payload] as a plain JSON text column and converts it
 * back on the way out. Mirrors the same trick the Android app uses for its
 * own local outbox table (OutboxPayloadConverter) - same idea, same shape.
 */
@Converter
class PayloadConverter : AttributeConverter<Map<String, String?>, String> {
    private val mapType = object : TypeToken<Map<String, String?>>() {}.type

    override fun convertToDatabaseColumn(attribute: Map<String, String?>?): String =
        gson.toJson(attribute ?: emptyMap<String, String?>())

    override fun convertToEntityAttribute(dbData: String?): Map<String, String?> =
        if (dbData.isNullOrBlank()) emptyMap() else gson.fromJson(dbData, mapType) ?: emptyMap()
}
