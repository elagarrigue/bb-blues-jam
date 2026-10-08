package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** One requested count, never confirmed until the server's slots are mirrored. */
data class LineupChange(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val instrument: Instrument,
    val count: Int,
    val state: State,
) {
    sealed interface State {
        data object Sending : State
        data class Failed(val reason: WriteOutcome) : State
    }
}
