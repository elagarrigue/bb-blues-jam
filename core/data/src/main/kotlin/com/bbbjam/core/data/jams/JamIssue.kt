package com.bbbjam.core.data.jams

import java.time.LocalDate

/**
 * A problem the mapper found in one `Jams` row (`docs/sheet-schema.md`, Mapper rules). Every one
 * of them rejects the jam: the four cells are required (user approval P8).
 */
enum class JamIssue {
    MissingDate,
    InvalidDate,
    MissingStartTime,
    InvalidStartTime,
    MissingVenue,
    MissingStatus,
    InvalidStatus,

    /** The date is on more than one row; every such row is rejected, valid or not (user approval P2). */
    DuplicateDate,
}

/**
 * A `Jams` row that did not become a [com.bbbjam.core.model.Jam]. [index] is 1-based in the
 * response's `jams`, not the Sheet row, because the script skips blank rows. [date] is the trimmed
 * cell, valid or not. [issues] holds every issue of the row, in column order.
 */
data class RejectedJam(val index: Int, val date: String?, val issues: List<JamIssue>)

/**
 * A problem with one setlist row. A row with an issue that [dropsRow] is left out of the setlist and
 * the rest of the setlist is kept (user approval P4); a malformed `Otros` entry drops only that
 * entry (user approval P1).
 */
enum class SetlistRowIssue(val dropsRow: Boolean) {
    MissingPosition(dropsRow = true),

    /** Not a whole number of at least 1: `2.5`, `0` and `-1` are invalid. */
    InvalidPosition(dropsRow = true),

    /** The parsed position is shared with another row; every such row is dropped, valid or not. */
    DuplicatePosition(dropsRow = true),
    MissingSongId(dropsRow = true),
    InvalidSongId(dropsRow = true),
    MissingTitle(dropsRow = true),
    MissingArtist(dropsRow = true),

    /** No key in the tab. It is never filled from the catalog's default key (D-08). */
    MissingKey(dropsRow = true),
    InvalidKey(dropsRow = true),

    /** One `Otros` entry is not `Nombre (instrumento)`; that entry is left out, the song is kept. */
    MalformedExtraParticipant(dropsRow = false),
}

/**
 * Something about a kept jam's setlist worth reporting. None of them hides the jam; the setlist
 * state already says what a reader sees. Reports never hold a musician's name.
 */
sealed interface SetlistIssue {
    /** The jam the issue belongs to. */
    val date: LocalDate

    /** A draft arrived with a setlist, which the contract forbids: it was ignored (fail closed). */
    data class DraftSetlistIgnored(override val date: LocalDate) : SetlistIssue

    /** A published jam arrived with neither a setlist nor a `setlistError`. */
    data class SetlistMissing(override val date: LocalDate) : SetlistIssue

    /** The script reported [code] for this published jam, so its setlist is unavailable. */
    data class SetlistError(override val date: LocalDate, val code: String) : SetlistIssue

    /** Row [index] (1-based in that jam's `setlist`) had [issues]; [dropped] when any of them drops it. */
    data class RowIssues(override val date: LocalDate, val index: Int, val issues: List<SetlistRowIssue>) :
        SetlistIssue {
        val dropped: Boolean
            get() = issues.any { it.dropsRow }
    }
}
