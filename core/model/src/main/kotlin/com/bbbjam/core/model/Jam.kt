package com.bbbjam.core.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * One monthly session. Its [date] is its identity (`Jams.fecha`). Its [setlist] is a state, not a
 * bare list: [Setlist.Available], [Setlist.Withheld] or [Setlist.Unavailable]. A published jam is
 * never withheld; a draft may be withheld (a musician's read) or available (the admin's).
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
}
