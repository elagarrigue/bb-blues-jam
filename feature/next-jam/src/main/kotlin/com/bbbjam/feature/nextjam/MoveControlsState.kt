package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal data class MoveControlsState(val moves: List<SetlistMove>, val onMove: (LocalDate, SongId, Int) -> Unit)

@Composable
internal fun rememberMoveControls(setlist: SetlistRepository, snapshot: JamsSnapshot?): MoveControlsState {
    val moves by remember { setlist.observeMoves() }.collectAsState(initial = emptyList())
    val currentSnapshot by rememberUpdatedState(snapshot)
    val currentMoves by rememberUpdatedState(moves)
    val scope = rememberCoroutineScope()
    val moveTargets = remember { mutableMapOf<String, Int>() }
    val moveCounts = remember { mutableMapOf<String, Int>() }
    val onMove: (LocalDate, SongId, Int) -> Unit = { date, songId, direction ->
        val songs = currentSnapshot?.upcoming?.takeIf { it.date == date }
            ?.setlist?.let { (it as? Setlist.Available)?.songs }.orEmpty()
        val displayed = currentMoves.displayOrder(date, songs)
        val currentIndex = displayed.indexOfFirst { it.songId == songId }
        val key = removalKey(date, songId)
        val currentPosition = moveTargets[key] ?: (currentIndex + 1)
        val target = currentPosition + direction
        if (currentIndex >= 0 && target in 1..songs.size) {
            moveTargets[key] = target
            moveCounts[key] = (moveCounts[key] ?: 0) + 1
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                setlist.moveSong(date, songId, target)
                val remaining = (moveCounts[key] ?: 1) - 1
                if (remaining == 0) {
                    moveCounts.remove(key)
                    moveTargets.remove(key)
                } else {
                    moveCounts[key] = remaining
                }
            }
        }
    }
    return MoveControlsState(moves, onMove)
}

internal fun RowAdmin.moveAction(songId: SongId, enabled: Boolean, label: String, clickLabel: String, direction: Int) =
    MoveActionUiModel(
        label = label,
        clickLabel = clickLabel,
        enabled = enabled,
        events = EventHandler(key = "${removalKey(jam.date, songId)}|move|$direction") { event ->
            if (event == MoveActionUiModel.Event.Move) state.onMove(jam.date, songId, direction)
        },
    )

internal fun RowAdmin.isMoveSending(song: JamSong) = state.moves.any {
    it.jamDate == jam.date && it.songId == song.songId && it.state == SetlistMove.State.Sending
}

internal fun RowAdmin.moveModel(
    row: SongRowUiModel,
    song: JamSong,
    total: Int,
    removal: RemovalUiModel,
): MoveUiModel? = if (removal is RemovalUiModel.Removing) {
    null
} else {
    MoveUiModel(
        positionLine = MoveCopy.position(row.position, total),
        up = moveAction(song.songId, row.position > 1, NextJamCopy.MOVE_UP, "subir un lugar", -1),
        down = moveAction(song.songId, row.position < total, NextJamCopy.MOVE_DOWN, "bajar un lugar", 1),
    )
}
