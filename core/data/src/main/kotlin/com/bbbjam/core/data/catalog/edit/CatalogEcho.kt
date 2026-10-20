package com.bbbjam.core.data.catalog.edit

import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.remote.AppsScriptEnvelope
import com.bbbjam.core.data.remote.CatalogSongEchoDto
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The wire shape of the catalog writes (`admin-catalog-tab`): the request fields and the `song` echo the
 * server answers, which the repository mirrors into Room only when it is valid.
 */

private const val SONG = "song"
private const val TITLE = "title"
private const val ARTIST = "artist"
private const val DEFAULT_KEY = "defaultKey"

/** The request fields of [this] song: title, artist and default key; never an id (the server makes it). */
internal fun SongFields.toRequest(): Map<String, JsonElement> = mapOf(
    TITLE to JsonPrimitive(title),
    ARTIST to JsonPrimitive(artist),
    DEFAULT_KEY to JsonPrimitive(defaultKey.value),
)

/** The echoed song when it is one Room can hold: a slug id, a key and a non-blank title and artist. */
internal fun songEchoOf(body: JsonObject): CatalogSongEchoDto? {
    val element = body[SONG] ?: return null
    return decodeEcho(element)?.takeIf { it.isStorable() }
}

private fun decodeEcho(element: JsonElement): CatalogSongEchoDto? = try {
    AppsScriptEnvelope.json.decodeFromJsonElement(CatalogSongEchoDto.serializer(), element)
} catch (ignored: SerializationException) {
    null
} catch (ignored: IllegalArgumentException) {
    null
}

private fun CatalogSongEchoDto.isStorable(): Boolean =
    SongId.parseOrNull(id) != null && Key.parseOrNull(defaultKey) != null &&
        title.isNotBlank() && artist.isNotBlank()

/** A new cached row for the echo: the Sheet-only columns empty, the order set by the DAO. */
internal fun CatalogSongEchoDto.toEntity() = CatalogSongEntity(
    id = id,
    sheetOrder = 0,
    title = title,
    artist = artist,
    defaultKey = defaultKey,
    tempo = null,
    difficulty = null,
    tags = emptyList(),
    songsterrId = null,
)
