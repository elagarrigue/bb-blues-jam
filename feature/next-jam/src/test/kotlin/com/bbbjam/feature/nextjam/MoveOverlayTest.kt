package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveOverlayTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val songs = listOf("one", "two", "three", "four").mapIndexed { index, id ->
        JamSong(index + 1, SongId(id), id, "artist", Key("A"), Lineup.default())
    }

    @Test
    fun `sending absolute moves reorder and renumber while failures are ignored`() {
        val moves = listOf(
            move(3, "four", 1),
            move(1, "two", 1),
            move(2, "three", 2, SetlistMove.State.Failed(WriteOutcome.Offline)),
            move(4, "one", 4, moveDate = date.plusDays(1)),
        )

        val ordered = moves.displayOrder(date, songs)

        assertEquals(listOf("four", "two", "one", "three"), ordered.map { it.songId.value })
        assertEquals(listOf(1, 2, 3, 4), ordered.map { it.position })
    }

    @Test
    fun `a refreshed list already holding the target is stable`() {
        val refreshed = listOf("two", "one", "three", "four").mapIndexed { index, id ->
            songs.single { it.songId.value == id }.copy(position = index + 1)
        }

        assertEquals(refreshed, listOf(move(1, "two", 1)).displayOrder(date, refreshed))
    }

    private fun move(
        id: Long,
        songId: String,
        position: Int,
        state: SetlistMove.State = SetlistMove.State.Sending,
        moveDate: LocalDate = date,
    ) = SetlistMove(id, moveDate, SongId(songId), songId, position, state)
}
