package com.bbbjam.core.model

/**
 * One [Song] scheduled in one [Jam]. It carries that night's [key] (set by the admin, D-08), the
 * [title] and [artist] it needs to render, its [lineup], and any [extraParticipants] playing outside
 * the lineup, which never affect open slots (D-18). Its identity is the jam's date plus [position].
 */
data class JamSong(
    val position: Int,
    val songId: SongId,
    val title: String,
    val artist: String,
    val key: Key,
    val lineup: Lineup,
    val extraParticipants: List<ExtraParticipant> = emptyList(),
) {
    init {
        require(position >= 1) { "Invalid setlist position $position: positions start at 1" }
    }
}
