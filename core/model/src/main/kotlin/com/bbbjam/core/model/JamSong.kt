package com.bbbjam.core.model

/**
 * One [Song] scheduled in one [Jam]. It carries that night's [key] (set by the admin, D-08), the
 * [title] and [artist] it needs to render, its [lineup], and any [extraParticipants] playing outside
 * the lineup, which never affect open slots (D-18). Its identity is the jam's date plus [position].
 *
 * [played] is the admin's "ya tocado" mark (D-31): app-owned like the rest of the upcoming setlist
 * (D-04), false when the Sheet has no `tocado` column. A played song is read-only except for its
 * position.
 */
data class JamSong(
    val position: Int,
    val songId: SongId,
    val title: String,
    val artist: String,
    val key: Key,
    val lineup: Lineup,
    val extraParticipants: List<ExtraParticipant> = emptyList(),
    val played: Boolean = false,
) {
    init {
        require(position >= 1) { "Invalid setlist position $position: positions start at 1" }
    }
}
