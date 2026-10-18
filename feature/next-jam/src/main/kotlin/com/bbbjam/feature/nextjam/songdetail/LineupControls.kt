package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.data.setlist.SetlistLineupRepository
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.feature.nextjam.LineupEditorLineUiModel
import com.bbbjam.feature.nextjam.overlay.pendingLineup
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal class LineupControls(
    val lineupChanges: List<LineupChange>,
    val reservations: LineupReservations,
    val onLineupCount: (LocalDate, SongId, Instrument, LineupEditorLineUiModel.Event) -> Unit,
)

@Composable
internal fun rememberLineupControls(
    setlist: SetlistLineupRepository,
    snapshot: JamsSnapshot?,
    isAdmin: Boolean,
): LineupControls {
    val scope = rememberCoroutineScope()
    val currentIsAdmin by rememberUpdatedState(isAdmin)
    val lineupChanges by remember { setlist.observeLineupChanges() }.collectAsState(initial = emptyList())
    val reservations = remember { LineupReservations() }
    val currentLineupChanges by rememberUpdatedState(lineupChanges)
    val currentSnapshot by rememberUpdatedState(snapshot)
    val onLineupCount: (LocalDate, SongId, Instrument, LineupEditorLineUiModel.Event) -> Unit =
        { date, id, instrument, event ->
            val jam = currentSnapshot?.upcoming
            val song = (jam?.setlist as? Setlist.Available)?.songs?.singleOrNull { it.songId == id }
            if (currentIsAdmin && jam?.date == date && song != null) {
                val shown = reservations.overlay(
                    date,
                    id,
                    currentLineupChanges.pendingLineup(date, id, song.lineup),
                )
                val delta = if (event == LineupEditorLineUiModel.Event.Add) 1 else -1
                val target = shown.count(instrument) + delta
                if (target in 0..com.bbbjam.core.model.Lineup.maxCount(instrument) &&
                    shown.withSlotCount(instrument, target) != null
                ) {
                    val token = reservations.reserve(date, id, instrument, target)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            setlist.setSlotCount(date, id, instrument, target)
                        } finally {
                            reservations.release(date, id, instrument, token)
                        }
                    }
                }
            }
        }
    return LineupControls(lineupChanges, reservations, onLineupCount)
}
