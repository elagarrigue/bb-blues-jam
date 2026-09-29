package com.bbbjam.core.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * One monthly session. Its [date] is its identity (`Jams.fecha`). The [setlist] positions are
 * exactly 1..n in list order, so a gap, a duplicate or an out-of-order list cannot be built.
 */
data class Jam(
    val date: LocalDate,
    val startTime: LocalTime,
    val venue: String,
    val status: JamStatus,
    val setlist: List<JamSong>,
) {
    init {
        val positions = setlist.map { it.position }
        require(positions == (1..setlist.size).toList()) {
            "Setlist positions $positions of the $date jam must be 1..${setlist.size} in order"
        }
    }

    /**
     * True when the jam's date is before [today]; on its own date a jam is not historical. The
     * caller owns the clock and the time zone.
     */
    fun isHistorical(today: LocalDate): Boolean = date.isBefore(today)
}
