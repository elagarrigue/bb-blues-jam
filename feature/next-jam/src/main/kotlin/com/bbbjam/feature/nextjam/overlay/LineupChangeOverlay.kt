package com.bbbjam.feature.nextjam.overlay

import com.bbbjam.core.data.setlist.lineup.LineupChange
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** Only Sending changes draw ahead of the confirmed cache; a failure is the revert. */
internal fun List<LineupChange>.pendingLineup(date: LocalDate, songId: SongId, confirmed: Lineup): Lineup =
    filter { it.jamDate == date && it.songId == songId && it.state == LineupChange.State.Sending }
        .groupBy { it.instrument }.values.map { entries -> entries.maxBy { it.id } }
        .fold(confirmed) { lineup, change -> lineup.withSlotCount(change.instrument, change.count) ?: lineup }
