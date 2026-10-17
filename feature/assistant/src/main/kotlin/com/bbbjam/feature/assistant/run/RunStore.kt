package com.bbbjam.feature.assistant.run

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.model.SongId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The runs of a [ProposalApplier] by turn id, and the small writes the phases make into them. */
internal class RunStore(private val state: MutableStateFlow<Map<Long, ApplyRun>>) {
    fun run(turnId: Long): ApplyRun = state.value.getValue(turnId)

    fun update(turnId: Long, change: (ApplyRun) -> ApplyRun) {
        state.update { runs -> runs[turnId]?.let { runs + (turnId to change(it)) } ?: runs }
    }

    fun setSong(turnId: Long, song: ProposedSong, to: SongApply.State) = update(turnId) { run ->
        run.copy(songs = run.songs.map { if (it.song.songId == song.songId) it.copy(state = to) else it })
    }

    fun setRemoval(turnId: Long, id: SongId, to: SongRemoval.State) = update(turnId) { run ->
        run.copy(removals = run.removals.map { if (it.song.songId == id) it.copy(state = to) else it })
    }

    fun setOrder(turnId: Long, to: OrderStep) = update(turnId) { it.copy(order = to) }

    /** Ends the run as stopped; returns false, so a phase can return it. */
    fun stop(turnId: Long): Boolean {
        end(turnId, ApplyRun.State.Stopped)
        return false
    }

    /** Ends the run as aborted by [reason]; returns false, so a phase can return it. */
    fun abort(turnId: Long, reason: WriteOutcome): Boolean {
        end(turnId, ApplyRun.State.Aborted(reason))
        return false
    }

    /** Ends the run: whatever was still waiting was never attempted; a call cut off by a cancel failed as offline. */
    fun end(turnId: Long, finalState: ApplyRun.State) = update(turnId) { run ->
        val interrupted = finalState == ApplyRun.State.Interrupted
        run.copy(
            songs = run.songs.map {
                when (it.state) {
                    SongApply.State.Waiting -> it.copy(state = SongApply.State.NotAttempted)
                    SongApply.State.Adding -> it.copy(state = SongApply.State.Failed(WriteOutcome.Offline))
                    else -> it
                }
            },
            removals = run.removals.map {
                when (it.state) {
                    SongRemoval.State.Waiting -> it.copy(state = SongRemoval.State.NotAttempted)
                    SongRemoval.State.Removing -> it.copy(state = SongRemoval.State.Failed(WriteOutcome.Offline))
                    else -> it
                }
            },
            order = when (val order = run.order) {
                OrderStep.Waiting -> OrderStep.NotAttempted

                is OrderStep.Moving -> if (interrupted) {
                    OrderStep.Failed(
                        WriteOutcome.Offline,
                    )
                } else {
                    OrderStep.NotAttempted
                }

                else -> order
            },
            jam = if (run.jam == JamStep.Creating) JamStep.Failed(JamFailure.Interrupted) else run.jam,
            state = finalState,
        )
    }
}
