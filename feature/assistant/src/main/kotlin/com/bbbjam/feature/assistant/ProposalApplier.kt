package com.bbbjam.feature.assistant

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.SetlistSongAdder
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One proposal song in a run and what happened to it. */
data class SongApply(val song: ProposedSong, val state: State) {
    sealed interface State {
        data object Waiting : State

        data object Adding : State

        data class Added(val position: Int) : State

        /** Already in the list: known before the run, or the server's `song_already_in_setlist`. */
        data object Skipped : State

        data class Failed(val reason: WriteOutcome) : State

        data object NotAttempted : State
    }
}

/**
 * One load of a proposal into [jamDate]. [planned] is how many songs the run tried to add (the
 * plan's `toAdd`); [songs] also holds the ones skipped before it began.
 */
data class ApplyRun(val jamDate: LocalDate, val planned: Int, val songs: List<SongApply>, val state: State) {
    sealed interface State {
        data object Running : State

        /** Every planned song was tried (some may have failed one by one: see [failedCount]). */
        data object Finished : State

        data object Stopped : State

        /** A systemic outcome ended it; [reason] is that outcome. */
        data class Aborted(val reason: WriteOutcome) : State

        /** The scope was cancelled (the process is going away). */
        data object Interrupted : State
    }

    val addedCount: Int get() = songs.count { it.state is SongApply.State.Added }
    val failedCount: Int get() = songs.count { it.state is SongApply.State.Failed }
    val skippedCount: Int get() = songs.count { it.state == SongApply.State.Skipped }

    /** True only when the run finished and no song failed: the one case that is a success. */
    val isSuccess: Boolean get() = state == State.Finished && failedCount == 0
}

/**
 * Runs the confirmed load of a proposal, one song at a time, in proposal order, through the
 * [SetlistSongAdder] (the action contract, D-13). A Koin `single` with a module-supplied [scope],
 * so a run survives leaving the tab; it holds no Android type (D-02). It only runs what [apply] is
 * given, and [apply] is called only from the admin's confirm tap (D-24).
 *
 * Failure handling (A5): a per-song refusal (`unknown_song`, `invalid_song`, `invalid_key`) lets the
 * run continue; a duplicate the server reports is [SongApply.State.Skipped]; every other outcome
 * (access refused, offline, unavailable, `unknown_jam`, `jam_not_editable`, unknown codes) aborts,
 * because later adds would fail alike and an offline answer may hide a write.
 */
class ProposalApplier(private val adder: SetlistSongAdder, private val scope: CoroutineScope) {
    private val lock = Any()
    private val state = MutableStateFlow<Map<Long, ApplyRun>>(emptyMap())
    private val stopRequests = mutableSetOf<Long>()

    /** The latest run of each chat turn, by turn id. */
    val runs: StateFlow<Map<Long, ApplyRun>> = state.asStateFlow()

    /**
     * Starts loading [plan] for [turnId]. Returns false, doing nothing, when any run is still running
     * (at most one run at a time in the process).
     */
    fun apply(turnId: Long, plan: ApplyPlan.Ready): Boolean = synchronized(lock) {
        if (state.value.values.any { it.state == ApplyRun.State.Running }) return false
        stopRequests.remove(turnId)
        val songs = plan.toAdd.map { SongApply(it, SongApply.State.Waiting) } +
            plan.skipped.map { SongApply(it, SongApply.State.Skipped) }
        state.update { it + (turnId to ApplyRun(plan.jamDate, plan.toAdd.size, songs, ApplyRun.State.Running)) }
        scope.launch { execute(turnId, plan) }
        true
    }

    /** Asks the run of [turnId] to stop after the song in flight; the rest are not attempted. */
    fun stop(turnId: Long) {
        synchronized(lock) {
            if (state.value[turnId]?.state == ApplyRun.State.Running) stopRequests += turnId
        }
    }

    private fun stopRequested(turnId: Long) = synchronized(lock) { turnId in stopRequests }

    private fun update(turnId: Long, change: (ApplyRun) -> ApplyRun) {
        state.update { runs -> runs[turnId]?.let { runs + (turnId to change(it)) } ?: runs }
    }

    private fun setSong(turnId: Long, song: ProposedSong, to: SongApply.State) = update(turnId) { run ->
        run.copy(songs = run.songs.map { if (it.song.songId == song.songId) it.copy(state = to) else it })
    }

    private fun end(turnId: Long, finalState: ApplyRun.State) = update(turnId) { run ->
        run.copy(
            songs = run.songs.map {
                when (it.state) {
                    SongApply.State.Waiting -> it.copy(state = SongApply.State.NotAttempted)

                    // Cancelled mid-add: whether the Sheet got it is unknown.
                    SongApply.State.Adding -> it.copy(state = SongApply.State.Failed(WriteOutcome.Offline))

                    else -> it
                }
            },
            state = finalState,
        )
    }

    private suspend fun execute(turnId: Long, plan: ApplyPlan.Ready) {
        try {
            addSongs(turnId, plan)
        } finally {
            // Cancellation or an unexpected exception: never leave a run "Running" (it blocks every later run).
            if (state.value[turnId]?.state == ApplyRun.State.Running) end(turnId, ApplyRun.State.Interrupted)
        }
    }

    private suspend fun addSongs(turnId: Long, plan: ApplyPlan.Ready) {
        for (song in plan.toAdd) {
            if (stopRequested(turnId)) return end(turnId, ApplyRun.State.Stopped)
            setSong(turnId, song, SongApply.State.Adding)
            val outcome = adder.addSong(plan.jamDate, song.songId, song.key)
            when (val step = classify(outcome)) {
                is Step.Record -> setSong(turnId, song, step.state)

                is Step.Abort -> {
                    setSong(turnId, song, SongApply.State.Failed(step.reason))
                    return end(turnId, ApplyRun.State.Aborted(step.reason))
                }
            }
        }
        end(turnId, ApplyRun.State.Finished)
    }

    private sealed interface Step {
        data class Record(val state: SongApply.State) : Step

        data class Abort(val reason: WriteOutcome) : Step
    }

    private fun classify(outcome: AddSongOutcome): Step = when (outcome) {
        is AddSongOutcome.Added -> Step.Record(SongApply.State.Added(outcome.position))

        is AddSongOutcome.NotAdded -> {
            val reason = outcome.reason
            when {
                reason is WriteOutcome.Rejected && reason.code == DUPLICATE -> Step.Record(SongApply.State.Skipped)

                reason is WriteOutcome.Rejected && reason.code in PER_SONG_CODES ->
                    Step.Record(SongApply.State.Failed(reason))

                else -> Step.Abort(reason)
            }
        }
    }

    private companion object {
        const val DUPLICATE = "song_already_in_setlist"
        val PER_SONG_CODES = setOf("unknown_song", "invalid_song", "invalid_key")
    }
}
