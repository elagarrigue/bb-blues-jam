package com.bbbjam.core.data.jams

import com.bbbjam.core.data.jams.SetlistRowIssue.InvalidKey
import com.bbbjam.core.data.jams.SetlistRowIssue.InvalidPosition
import com.bbbjam.core.data.jams.SetlistRowIssue.InvalidSongId
import com.bbbjam.core.data.jams.SetlistRowIssue.MissingKey
import com.bbbjam.core.data.jams.SetlistRowIssue.MissingPosition
import com.bbbjam.core.data.jams.SetlistRowIssue.MissingSongId
import com.bbbjam.core.data.remote.SetlistRowDto
import com.bbbjam.core.data.remote.SlotsDto
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** One jam's setlist state, the slot columns of its songs (see [MappedJam]) and its issues. */
internal data class MappedSetlist(
    val setlist: Setlist,
    val slotColumns: Map<Int, List<Int>> = emptyMap(),
    val issues: List<SetlistIssue> = emptyList(),
)

/**
 * Turns one published jam's tab rows into a [Setlist] (`docs/sheet-schema.md`, jam tab and Mapper
 * rules). An invalid row is dropped and reported and the rest stay [Setlist.Available], sorted by
 * position and never renumbered here: positions are the read identity (user approval P4; setlist
 * mutations find a row by song id, `admin-remove-song-from-setlist`). Every row
 * whose position parses and is shared is dropped, valid or not. When rows existed and all were
 * dropped the setlist is [Setlist.Unavailable] with [SetlistProblem.INVALID_ROWS]. The key is
 * always the tab's, never the catalog default (D-08).
 */
internal object SetlistMapper {
    private val POSITION = Regex("[0-9]+")

    /** `Nombre (instrumento)`: a name, optional spaces, an instrument in parentheses (user approval P1). */
    private val EXTRA_PARTICIPANT = Regex("""([^;()]+?)\s*\(([^;()]+)\)""")
    private const val NOT_IN_LINEUP = "-"

    fun map(date: LocalDate, rows: List<SetlistRowDto>): MappedSetlist {
        val checked = rows.mapIndexed { position, row -> checkRow(position + 1, row) }
        val positionCounts = checked.mapNotNull { it.position }.groupingBy { it }.eachCount()
        val kept = mutableListOf<CheckedRow>()
        val issues = mutableListOf<SetlistIssue>()
        checked.forEach { row ->
            val shared = row.position != null && positionCounts.getValue(row.position) > 1
            val rowIssues = if (shared) row.issues + SetlistRowIssue.DuplicatePosition else row.issues
            if (rowIssues.isNotEmpty()) issues += SetlistIssue.RowIssues(date, row.index, rowIssues)
            if (rowIssues.none { it.dropsRow }) kept += row
        }
        val dropped = rows.size - kept.size
        if (kept.isEmpty() && dropped > 0) {
            return MappedSetlist(Setlist.Unavailable(SetlistProblem.INVALID_ROWS), issues = issues)
        }
        val sorted = kept.sortedBy { it.position }
        return MappedSetlist(
            Setlist.Available(sorted.map { checkNotNull(it.song) }, droppedRows = dropped),
            slotColumns = sorted.associate { checkNotNull(it.position) to it.columns },
            issues = issues,
        )
    }

    /**
     * One row after the per-row checks: the song when no issue drops it, its slot columns, and every
     * issue found (a malformed `Otros` entry included).
     */
    private class CheckedRow(
        val index: Int,
        val position: Int?,
        val song: JamSong?,
        val columns: List<Int>,
        val issues: List<SetlistRowIssue>,
    )

    private fun checkRow(index: Int, row: SetlistRowDto): CheckedRow {
        val issues = mutableListOf<SetlistRowIssue>()
        val position = required(row.position, ::parsePosition, MissingPosition, InvalidPosition, issues)
        val songId = required(row.songId, SongId::parseOrNull, MissingSongId, InvalidSongId, issues)
        val title = row.title.trimmedCell()
        if (title == null) issues += SetlistRowIssue.MissingTitle
        val artist = row.artist.trimmedCell()
        if (artist == null) issues += SetlistRowIssue.MissingArtist
        val key = required(row.key, Key::parseOrNull, MissingKey, InvalidKey, issues)
        val slots = lineupSlots(row.slots)
        val extras = extraParticipants(row.extraParticipants.trimmedCell(), issues)
        // A required field is null exactly when it recorded an issue that drops the row.
        val song = if (issues.none { it.dropsRow }) {
            JamSong(
                position = checkNotNull(position),
                songId = checkNotNull(songId),
                title = checkNotNull(title),
                artist = checkNotNull(artist),
                key = checkNotNull(key),
                lineup = Lineup(slots.map { it.second }),
                extraParticipants = extras,
            )
        } else {
            null
        }
        return CheckedRow(index, position, song, slots.map { it.first }, issues)
    }

    /** The parsed value of a required cell, or null after recording [missing] or [invalid]. */
    private fun <T : Any> required(
        raw: String?,
        parse: (String) -> T?,
        missing: SetlistRowIssue,
        invalid: SetlistRowIssue,
        issues: MutableList<SetlistRowIssue>,
    ): T? {
        val cell = raw.trimmedCell()
        val value = cell?.let(parse)
        when {
            cell == null -> issues += missing
            value == null -> issues += invalid
        }
        return value
    }

    /** Decimal digits only (`2.5` and `-1` are not positions), at least 1. */
    private fun parsePosition(text: String): Int? =
        if (POSITION.matches(text)) text.toIntOrNull()?.takeIf { it >= 1 } else null

    /**
     * The slot of each column not holding `-`, paired with its 0-based column, in column order. An
     * empty cell is an open slot; anything else is the musician's name.
     */
    private fun lineupSlots(slots: SlotsDto): List<Pair<Int, Slot>> =
        Lineup.DEFAULT_INSTRUMENTS.zip(slots.cells()).mapIndexedNotNull { column, (instrument, raw) ->
            val cell = raw.trimmedCell()
            if (cell == NOT_IN_LINEUP) null else column to Slot(instrument, cell)
        }

    /**
     * `Otros` split on `;`, each entry trimmed, empty entries ignored, order kept. A malformed entry
     * is left out and recorded; the song is kept (user approval P1).
     */
    private fun extraParticipants(cell: String?, issues: MutableList<SetlistRowIssue>): List<ExtraParticipant> =
        cell.orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { entry ->
            parseExtraParticipant(entry) ?: run {
                issues += SetlistRowIssue.MalformedExtraParticipant
                null
            }
        }

    private fun parseExtraParticipant(entry: String): ExtraParticipant? {
        val match = EXTRA_PARTICIPANT.matchEntire(entry) ?: return null
        val name = match.groupValues[1].trim()
        val instrument = match.groupValues[2].trim()
        return if (name.isEmpty() || instrument.isEmpty()) null else ExtraParticipant(name, instrument)
    }
}
