package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.SlotClear
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** Makes only the matching in-flight clear open in the admin projection; failures never overlay. */
internal fun Lineup.overlaySlotClears(date: LocalDate, songId: SongId, clears: List<SlotClear>): Lineup {
    val latest = clears.asSequence()
        .filter { it.jamDate == date && it.songId == songId && it.state == SlotClear.State.Sending }
        .associateBy { ClearSlotKey(it.instrument, it.ordinal.value) }
    if (latest.isEmpty()) return this
    val next = slots.toMutableList()
    var changed = false
    latest.forEach { (key, clear) ->
        val index = slots.indices.filter { slots[it].instrument == key.instrument }.getOrNull(key.ordinal - 1)
        if (index != null && slots[index].musicianName == clear.musicianName) {
            next[index] = Slot(key.instrument)
            changed = true
        }
    }
    return if (changed) Lineup(next) else this
}

internal fun List<SlotClear>.isClearing(
    date: LocalDate,
    songId: SongId,
    instrument: Instrument,
    ordinal: Int,
): Boolean = any {
    it.jamDate == date && it.songId == songId && it.instrument == instrument && it.ordinal.value == ordinal &&
        it.state == SlotClear.State.Sending
}

private data class ClearSlotKey(val instrument: Instrument, val ordinal: Int)
