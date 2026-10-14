package com.bbbjam.core.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * One monthly session. Its [date] is its identity (`Jams.fecha`). Its [setlist] is a state, not a
 * bare list: [Setlist.Available], [Setlist.Withheld] or [Setlist.Unavailable]. A published jam is
 * never withheld; a draft or created jam may be withheld (a musician's read) or available (the
 * admin's).
 */
data class Jam(
    val date: LocalDate,
    val startTime: LocalTime,
    val venue: String,
    val status: JamStatus,
    val setlist: Setlist,
) {
    init {
        require(!(status == JamStatus.PUBLISHED && setlist == Setlist.Withheld)) {
            "The published $date jam cannot have a withheld setlist"
        }
    }

    /**
     * True when the jam's date is before [today]; on its own date a jam is not historical. The
     * caller owns the clock and the time zone.
     */
    fun isHistorical(today: LocalDate): Boolean = date.isBefore(today)

    /** False only for a [JamStatus.DRAFT] jam, which does not exist for musicians (D-26). */
    val isVisibleToMusicians: Boolean
        get() = status != JamStatus.DRAFT

    /**
     * The setlist a musician may see: only a [JamStatus.PUBLISHED] jam's [setlist], itself. A draft
     * or created jam is always [Setlist.Withheld] here, even when its [setlist] is available (an
     * admin's read). Musician screens read this, never [setlist]; the admin slices read [setlist]
     * behind the admin flag.
     */
    fun setlistForMusicians(): Setlist = if (status == JamStatus.PUBLISHED) setlist else Setlist.Withheld
}
