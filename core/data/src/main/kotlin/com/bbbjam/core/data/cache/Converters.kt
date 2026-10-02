package com.bbbjam.core.data.cache

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Room converters: tags are stored as a JSON array, so a tag may hold any character. */
internal class Converters {
    @TypeConverter
    fun tagsToJson(tags: List<String>): String = Json.encodeToString(TAGS, tags)

    @TypeConverter
    fun jsonToTags(json: String): List<String> = Json.decodeFromString(TAGS, json)

    private companion object {
        val TAGS = ListSerializer(String.serializer())
    }
}
