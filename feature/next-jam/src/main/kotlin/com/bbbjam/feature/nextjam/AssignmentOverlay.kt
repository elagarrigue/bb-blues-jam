package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** Shows only the newest in-flight name at a fixed slot; failed writes never enter the overlay. */
internal fun Lineup.overlayAssignments(date: LocalDate, songId: SongId, assignments: List<Assignment>): Lineup {
    val latest = assignments.asSequence()
        .filter { it.jamDate == date && it.songId == songId && it.state == Assignment.State.Sending }
        .associateBy { SlotKey(it.instrument, it.ordinal.value) }
    if (latest.isEmpty()) return this
    val next = slots.toMutableList()
    var changed = false
    latest.forEach { (key, assignment) ->
        val index = slots.indices
            .filter { slots[it].instrument == key.instrument }
            .getOrNull(key.ordinal - 1)
        if (index != null && slots[index].isOpen) {
            next[index] = Slot(key.instrument, assignment.musicianName.value)
            changed = true
        }
    }
    return if (changed) Lineup(next) else this
}

internal fun AdminState.isSavingAssignment(date: LocalDate, songId: SongId): Boolean = assignments.any {
    it.jamDate == date && it.songId == songId && it.state == Assignment.State.Sending
}

private data class SlotKey(val instrument: Instrument, val ordinal: Int)
