package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.model.JamSong
import java.time.LocalDate

/** The admin's optimistic order. Failed entries do not overlay, so their confirmed order reappears. */
internal fun List<SetlistMove>.displayOrder(date: LocalDate, songs: List<JamSong>): List<JamSong> =
    displayEntries(date, songs).map { it.song }

internal data class DisplayedJamSong(val song: JamSong, val cachedPosition: Int)

internal fun List<SetlistMove>.displayEntries(date: LocalDate, songs: List<JamSong>): List<DisplayedJamSong> {
    val displayed = songs.map { DisplayedJamSong(it, it.position) }.toMutableList()
    filter { it.jamDate == date && it.state == SetlistMove.State.Sending }
        .sortedBy { it.id }
        .forEach { move ->
            val index = displayed.indexOfFirst { it.song.songId == move.songId }
            if (index >= 0) {
                val song = displayed.removeAt(index)
                displayed.add((move.toPosition - 1).coerceIn(0, displayed.size), song)
            }
        }
    return displayed.mapIndexed { index, entry -> entry.copy(song = entry.song.copy(position = index + 1)) }
}
