package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** One clear request still sending or refused by the Sheet. */
data class SlotClear(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val instrument: Instrument,
    val ordinal: SlotPosition,
    val musicianName: String,
    val state: State,
) {
    sealed interface State {
        data object Sending : State
        data class Failed(val reason: WriteOutcome) : State
    }
}

sealed interface ClearSlotOutcome {
    data object Cleared : ClearSlotOutcome
    data class NotCleared(val reason: WriteOutcome) : ClearSlotOutcome
}
