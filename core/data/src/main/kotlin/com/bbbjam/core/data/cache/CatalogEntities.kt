package com.bbbjam.core.data.cache

import com.bbbjam.core.model.Difficulty
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import com.bbbjam.core.model.Tempo

/** The row for [song] at 1-based [sheetOrder]. */
internal fun Song.toEntity(sheetOrder: Int): CatalogSongEntity = CatalogSongEntity(
    id = id.value,
    sheetOrder = sheetOrder,
    title = title,
    artist = artist,
    defaultKey = defaultKey.value,
    tempo = tempo?.name,
    difficulty = difficulty?.name,
    tags = tags,
    songsterrId = songsterrId,
)

/**
 * The song this row holds, built through the domain constructors. Rows are written only from mapped
 * songs, so the id and key are valid; an unknown optional enum name reads as absent.
 */
internal fun CatalogSongEntity.toDomain(): Song = Song(
    id = SongId(id),
    title = title,
    artist = artist,
    defaultKey = Key(defaultKey),
    tempo = Tempo.entries.firstOrNull { it.name == tempo },
    tags = tags,
    difficulty = Difficulty.entries.firstOrNull { it.name == difficulty },
    songsterrId = songsterrId,
)
