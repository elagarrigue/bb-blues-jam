package com.bbbjam.core.model

/**
 * A catalog entry, owned by the Sheet (D-04). [defaultKey] is only a suggestion; a jam's key lives
 * on [JamSong] (D-08). [songsterrId] and the enrichment fields ([mbid], [artistArea],
 * [deezerTrackId], [previewUrl], [artworkUrl]) are optional and may stay absent forever (D-09).
 */
data class Song(
    val id: SongId,
    val title: String,
    val artist: String,
    val defaultKey: Key,
    val tempo: Tempo? = null,
    val tags: List<String> = emptyList(),
    val difficulty: Difficulty? = null,
    val songsterrId: Long? = null,
    val mbid: String? = null,
    val artistArea: String? = null,
    val deezerTrackId: Long? = null,
    val previewUrl: String? = null,
    val artworkUrl: String? = null,
)
