package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.SongId
import java.time.LocalDate

data class ExtraParticipantChange(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val extras: List<ExtraParticipant>,
    val state: State,
) {
    sealed interface State {
        data object Sending : State
        data class Failed(val reason: WriteOutcome) : State
    }
}

sealed interface ExtraParticipantOutcome {
    data object Changed : ExtraParticipantOutcome
    data class NotChanged(val reason: WriteOutcome) : ExtraParticipantOutcome
}
