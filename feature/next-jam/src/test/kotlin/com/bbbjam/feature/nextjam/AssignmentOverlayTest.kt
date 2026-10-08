package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.SlotClear
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.lineup.toLineupPanel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `sending clear opens only matching cached musician slot and failures reveal confirmed state`() {
        val clear = SlotClear(
            id = 1,
            jamDate = date,
            songId = song,
            title = "Crossroads",
            instrument = Instrument.GUITAR,
            ordinal = SlotPosition(1),
            musicianName = "Tincho",
            state = SlotClear.State.Sending,
        )
        assertEquals(
            Lineup(listOf(Slot(Instrument.GUITAR), Slot(Instrument.GUITAR))),
            lineup.overlaySlotClears(date, song, listOf(clear)),
        )
        assertEquals(
            lineup,
            lineup.overlaySlotClears(date, song, listOf(clear.copy(musicianName = "Other"))),
        )
        assertEquals(
            lineup,
            lineup.overlaySlotClears(
                date,
                song,
                listOf(clear.copy(state = SlotClear.State.Failed(WriteOutcome.Offline))),
            ),
        )
    }

    @Test
    fun `admin panel renders the cleared slot as pending and suppresses both slot actions`() {
        val clear = SlotClear(
            id = 1,
            jamDate = date,
            songId = song,
            title = "Crossroads",
            instrument = Instrument.GUITAR,
            ordinal = SlotPosition(1),
            musicianName = "Tincho",
            state = SlotClear.State.Sending,
        )
        val projected = lineup.overlaySlotClears(date, song, listOf(clear))
        val panel = projected.toLineupPanel(
            extras = emptyList(),
            onAssign = { _, _ -> error("clear in flight must block assignment") },
            onClear = { _, _, _ -> error("clear in flight must block duplicate clear") },
            isClearing = { instrument, position ->
                instrument == clear.instrument && position == clear.ordinal
            },
        )

        assertEquals(listOf("Quitando…", "LIBRE"), panel.openSlots.map { it.detail })
        assertEquals(listOf("Guitarra: Quitando…", "Guitarra: libre"), panel.openSlots.map { it.contentDescription })
        assertEquals(null, panel.openSlots.first().action)
        assertTrue(panel.openSlots.last().action != null)
        assertTrue(panel.filledSlots.isEmpty())
    }
}
