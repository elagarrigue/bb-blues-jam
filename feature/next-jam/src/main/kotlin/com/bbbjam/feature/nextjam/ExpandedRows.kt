package com.bbbjam.feature.nextjam

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * Which rows of the upcoming jam are expanded: local presentation state, not data (no mutation, so
 * nothing for D-13). Keyed by the song's id ([songIds], the `id_tema` text), not by its position:
 * a removal (`admin-remove-song-from-setlist`) renumbers the later songs, so keyed by position the
 * song that moves up into a removed song's place would inherit its expansion. Keyed by call order or
 * list index instead, a dropped row would hand its expansion to a neighbour too.
 *
 * [jamDate] scopes the ids to one jam: a new upcoming jam reads as all collapsed without recreating
 * the state. Stale ids are harmless and never pruned. A hand-edited tab may repeat an id; both rows
 * then expand together, which is harmless.
 */
internal data class ExpandedRows(val jamDate: LocalDate?, val songIds: Set<String>) {
    fun isExpanded(date: LocalDate, songId: SongId): Boolean = date == jamDate && songId.value in songIds

    /** Flips one row and leaves every other row as it was; another jam's date starts afresh. */
    fun toggle(date: LocalDate, songId: SongId): ExpandedRows = when {
        date != jamDate -> ExpandedRows(date, setOf(songId.value))
        songId.value in songIds -> copy(songIds = songIds - songId.value)
        else -> copy(songIds = songIds + songId.value)
    }

    companion object {
        val NONE = ExpandedRows(jamDate = null, songIds = emptySet())

        /** `[date or null, ArrayList<String>]`: both bundle-safe, so expansion survives rotation. */
        val Saver: Saver<ExpandedRows, Any> = listSaver(
            save = { listOf(it.jamDate?.toString(), ArrayList(it.songIds)) },
            restore = { saved ->
                val date = (saved[0] as String?)?.let(LocalDate::parse)
                ExpandedRows(date, (saved[1] as List<*>).map { it as String }.toSet())
            },
        )
    }
}
