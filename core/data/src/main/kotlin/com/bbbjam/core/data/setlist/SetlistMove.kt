package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** One absolute-position move that is still sending or has failed. */
data class SetlistMove(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val toPosition: Int,
    val state: State,
) {
    sealed interface State {
        /** The move is queued or in flight. */
        data object Sending : State

        /** The confirmed order did not change. */
        data class Failed(val reason: WriteOutcome) : State
    }
}
