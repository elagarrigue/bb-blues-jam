package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.JamStatus
import java.time.LocalDate

/** One pending or failed status change of a jam, with the status the admin asked for. */
data class JamStatusChange(val id: Long, val jamDate: LocalDate, val target: JamStatus, val state: State) {
    sealed interface State {
        data object Sending : State
        data class Failed(val reason: WriteOutcome) : State

        /** The deployed script does not know `setJamStatus`; retrying is pointless. */
        data object Outdated : State
    }
}
