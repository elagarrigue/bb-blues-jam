package com.bbbjam.core.data.setlist.jamedit

import com.bbbjam.core.data.admin.WriteOutcome
import java.time.LocalDate
import java.time.LocalTime

/** One pending or failed edit or deletion of a future jam, with what the admin asked for. */
data class JamEdit(val id: Long, val jamDate: LocalDate, val change: Change, val state: State) {
    sealed interface Change {
        data class Update(val newDate: LocalDate, val startTime: LocalTime, val venue: String) : Change

        /** [expectedSongs] is the song count the admin confirmed. */
        data class Delete(val expectedSongs: Int) : Change
    }

    sealed interface State {
        data object Sending : State
        data class Failed(val reason: WriteOutcome) : State

        /** The deployed script does not know `updateJam`/`deleteJam`; retrying is pointless. */
        data object Outdated : State
    }
}
