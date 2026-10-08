package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** Only Sending changes draw ahead of the confirmed cache; a failure is the revert. */
internal fun List<LineupChange>.pendingLineup(date: LocalDate, songId: SongId, confirmed: Lineup): Lineup =
    filter { it.jamDate == date && it.songId == songId && it.state == LineupChange.State.Sending }
        .groupBy { it.instrument }.values.map { entries -> entries.maxBy { it.id } }
        .fold(confirmed) { lineup, change -> lineup.withSlotCount(change.instrument, change.count) ?: lineup }

internal fun AdminState.isSavingLineup(date: LocalDate, id: SongId): Boolean {
    val sending = lineupChanges.filter { it.jamDate == date && it.songId == id }
        .any { it.state == LineupChange.State.Sending }
    return sending || reservations.has(date, id)
}
