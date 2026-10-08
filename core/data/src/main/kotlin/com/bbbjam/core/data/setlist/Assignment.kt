package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** One assignment request still sending or refused by the Sheet. */
data class Assignment(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val instrument: Instrument,
    val ordinal: SlotPosition,
    val musicianName: MusicianName,
    val state: State,
) {
    sealed interface State {
        data object Sending : State
        data class Failed(val reason: WriteOutcome) : State
    }
}
