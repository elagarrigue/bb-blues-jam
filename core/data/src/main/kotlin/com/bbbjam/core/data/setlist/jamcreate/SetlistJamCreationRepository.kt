package com.bbbjam.core.data.setlist.jamcreate

import java.time.LocalDate
import java.time.LocalTime

/** Creating the next jam row, once, as `BORRADOR` (`assistant-create-jam`, D-25, D-13). */
interface SetlistJamCreationRepository {
    /**
     * Appends a `BORRADOR` row to `Jams` through the admin POST path. Room changes only when the
     * server echoes the row. Creates no setlist tab: the first `addSong` does. Queued in call order
     * with the other mutations; cancelling the caller never cancels the write.
     */
    suspend fun createJam(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome
}
