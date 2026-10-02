package com.bbbjam.core.model

/**
 * What a reader gets of one [Jam]'s setlist. A setlist that cannot be shown is a state with a
 * reason, never an empty list, so "not published" or "broken" is never read as "no songs".
 */
sealed interface Setlist {
    /**
     * The songs, in position order. Positions are the Sheet's `posicion` and are never renumbered,
     * because they are the write identity of a [JamSong]: they are at least 1, unique and
     * ascending, and gaps are allowed. [droppedRows] counts the tab rows left out as invalid (user
     * approval P4 of `jams-repository-cache`), so a screen can say the list is incomplete instead of
     * showing a silent gap. When every row was dropped the setlist is
     * [Unavailable] with [SetlistProblem.INVALID_ROWS], never an empty `Available`.
     */
    data class Available(val songs: List<JamSong>, val droppedRows: Int = 0) : Setlist {
        init {
            require(droppedRows >= 0) { "Negative dropped row count $droppedRows" }
            require(songs.isNotEmpty() || droppedRows == 0) {
                "A setlist whose $droppedRows rows were all dropped is Unavailable(INVALID_ROWS), not empty"
            }
            val positions = songs.map { it.position }
            require(positions.zipWithNext().all { (previous, next) -> previous < next }) {
                "Setlist positions $positions must be unique and ascending"
            }
        }
    }

    /** Not served to this reader: the jam is a draft, so the list is still being assembled. */
    data object Withheld : Setlist

    /** The jam is published but its setlist cannot be read, for [problem]. */
    data class Unavailable(val problem: SetlistProblem) : Setlist
}

/** Why a published jam's [Setlist] is [Setlist.Unavailable]. */
enum class SetlistProblem {
    /** No tab is named after the jam's date. */
    MISSING_TAB,

    /** The tab exists but is not a valid jam tab: a required header is missing or doubled. */
    INVALID_TAB,

    /** The tab has rows and none of them is valid. */
    INVALID_ROWS,

    /** Any other reason, such as an error code this version of the app does not know. */
    UNKNOWN,
}
