package com.bbbjam.core.data.jams

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where "today" comes from for jams: the [clock]'s instant in [zone], bound to
 * [BUENOS_AIRES] (user approval P7). Not the device zone and not UTC: with UTC a 21:00 jam would
 * turn historical at 21:00 local time on its own night. A jam is historical from 00:00 of the next
 * day in Buenos Aires ([com.bbbjam.core.model.Jam.isHistorical]).
 */
class JamCalendar(private val clock: Clock, val zone: ZoneId) {
    /** The current instant. */
    fun now(): Instant = clock.instant()

    /** Today's date in [zone]. */
    fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    companion object {
        /** The jam's own time zone. */
        val BUENOS_AIRES: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    }
}
