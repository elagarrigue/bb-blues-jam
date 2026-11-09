package com.bbbjam.feature.nextjam.admin

import com.bbbjam.core.data.setlist.move.SetlistMove
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.reorder.DropSlot
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.SetlistUiModel
import com.bbbjam.feature.nextjam.SongRowUiModel
import com.bbbjam.feature.nextjam.overlay.displayEntries
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DragPreviewTest {
    private val date = LocalDate.of(2026, 11, 28)
    private val ids = listOf("a", "b", "c", "d", "e")

    private fun row(id: String, position: Int, played: Boolean = false) = SongRowUiModel(
        position = position,
        positionLabel = "%02d".format(position),
        rowKey = id,
        title = id,
        key = "A",
        keyDescription = UiText.res(R.string.next_jam_open_detail),
        instruments = emptyList(),
        openDetailLabel = UiText.res(R.string.next_jam_open_detail),
        events = EventHandler {},
        played = played,
    )

    private fun songs(visible: List<String> = ids, played: Set<String> = emptySet()) = SetlistUiModel.Songs(
        rows = visible.map { row(it, ids.indexOf(it) + 1, it in played) },
        droppedRowsNote = null,
        filterBar = null,
        orderKeys = ids,
    )

    @Test
    fun `rows are reordered and numbered by their full preview index`() {
        val keys = previewKeys(ids, "b", DropSlot.After("c"))
        val shown = songs().previewed(keys)
        assertEquals(listOf("a", "c", "b", "d", "e"), shown.rows.map { it.rowKey })
        assertEquals(listOf("01", "02", "03", "04", "05"), shown.rows.map { it.positionLabel })
        assertEquals(listOf(1, 3, 2, 4, 5), shown.rows.map { it.position })
    }

    @Test
    fun `a filtered subset keeps full-setlist numbers and hidden rows count`() {
        val keys = previewKeys(ids, "a", DropSlot.After("c"))
        val shown = songs(visible = listOf("a", "c", "e")).previewed(keys)
        assertEquals(listOf("c", "a", "e"), shown.rows.map { it.rowKey })
        assertEquals(listOf("02", "03", "05"), shown.rows.map { it.positionLabel })
    }

    @Test
    fun `played rows are reordered and keep their played flag`() {
        val shown = songs(played = setOf("a")).previewed(previewKeys(ids, "a", DropSlot.After("b")))
        assertEquals(listOf("b", "a", "c", "d", "e"), shown.rows.map { it.rowKey })
        assertEquals(true, shown.rows[1].played)
        assertEquals("02", shown.rows[1].positionLabel)
    }

    @Test
    fun `null or mismatched keys return the model unchanged`() {
        val model = songs()
        assertSame(model, model.previewed(null))
        assertSame(model, model.previewed(listOf("a", "b", "c")))
        assertSame(model, model.previewed(listOf("a", "b", "c", "d", "zzz")))
    }

    private fun jamSongs() = ids.mapIndexed { index, id ->
        JamSong(index + 1, SongId(id), id, "artist", Key("A"), Lineup.default())
    }

    private fun applyDrop(base: List<SetlistMove>, from: String, toPosition: Int): List<String> {
        val moves = base + SetlistMove(99, date, SongId(from), from, toPosition, SetlistMove.State.Sending)
        return moves.displayEntries(date, jamSongs()).map { it.song.songId.value }
    }

    @Test
    fun `applying the dropped position yields exactly the previewed order`() {
        val overlays = listOf(
            emptyList(),
            listOf(SetlistMove(1, date, SongId("e"), "e", 1, SetlistMove.State.Sending)),
        )
        for (base in overlays) {
            val order = base.displayEntries(date, jamSongs()).map { it.song.songId.value }
            for (from in order) {
                for (slotKey in order) {
                    for (slot in listOf(DropSlot.Before(slotKey), DropSlot.After(slotKey))) {
                        val preview = previewKeys(order, from, slot)
                        assertEquals(preview, applyDrop(base, from, preview.indexOf(from) + 1))
                    }
                }
            }
        }
    }
}
