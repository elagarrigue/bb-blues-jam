package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AssignmentOverlayTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val song = SongId("crossroads")
    private val lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.GUITAR)))

    private fun assignment(
        id: Long,
        name: String,
        state: Assignment.State = Assignment.State.Sending,
        songId: SongId = song,
        ordinal: Int = 2,
    ) = Assignment(
        id = id,
        jamDate = date,
        songId = songId,
        title = "Crossroads",
        instrument = Instrument.GUITAR,
        ordinal = SlotPosition(ordinal),
        musicianName = requireNotNull(MusicianName.parseOrNull(name)),
        state = state,
    )

    @Test
    fun `latest sending assignment fills the original ordinal after open-first partition`() {
        val result = lineup.overlayAssignments(date, song, listOf(assignment(1, "Nico"), assignment(2, "Pato")))
        assertEquals(Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.GUITAR, "Pato"))), result)
    }

    @Test
    fun `failed unrelated and already occupied assignments never change the lineup`() {
        val result = lineup.overlayAssignments(
            date,
            song,
            listOf(
                assignment(1, "Nico", Assignment.State.Failed(WriteOutcome.Offline)),
                assignment(2, "Pato", songId = SongId("red-house")),
                assignment(3, "Luz", ordinal = 1),
            ),
        )
        assertEquals(lineup, result)
    }
}
