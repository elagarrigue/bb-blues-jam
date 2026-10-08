package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal class LineupControls(
    val lineupChanges: List<LineupChange>,
    val lineupEditing: String?,
    val reservations: LineupReservations,
    val onLineupEditor: (LocalDate, SongId, LineupEditorUiModel.Event) -> Unit,
    val onLineupCount: (LocalDate, SongId, Instrument, LineupEditorLineUiModel.Event) -> Unit,
)

@Composable
internal fun rememberLineupControls(
    setlist: SetlistRepository,
    snapshot: JamsSnapshot?,
    isAdmin: Boolean,
): LineupControls {
    val scope = rememberCoroutineScope()
    val currentIsAdmin by rememberUpdatedState(isAdmin)
    val lineupChanges by remember { setlist.observeLineupChanges() }.collectAsState(initial = emptyList())
    var lineupEditing by rememberSaveable { mutableStateOf<String?>(null) }
    val reservations = remember { LineupReservations() }
    val currentLineupChanges by rememberUpdatedState(lineupChanges)
    val currentSnapshot by rememberUpdatedState(snapshot)
    val onLineupEditor: (LocalDate, SongId, LineupEditorUiModel.Event) -> Unit = { date, id, event ->
        val key = removalKey(date, id)
        when (event) {
            LineupEditorUiModel.Event.Open -> lineupEditing = key
            LineupEditorUiModel.Event.Done -> if (lineupEditing == key) lineupEditing = null
        }
    }
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
                if (target in 0..com.bbbjam.core.model.Lineup.defaultCount(instrument) &&
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
    return LineupControls(lineupChanges, lineupEditing, reservations, onLineupEditor, onLineupCount)
}
