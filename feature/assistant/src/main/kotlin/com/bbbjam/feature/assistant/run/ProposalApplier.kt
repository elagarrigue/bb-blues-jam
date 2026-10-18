package com.bbbjam.feature.assistant.run

import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.data.setlist.add.SetlistSongAdder
import com.bbbjam.core.data.setlist.jamcreate.JamCreator
import com.bbbjam.core.data.setlist.move.SetlistSongMover
import com.bbbjam.core.data.setlist.remove.SetlistSongRemover
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs the confirmed load of a proposal, one call at a time, through the narrow write contracts (the
 * action contract, D-13). A Koin `single` with a module-supplied [scope], so a run survives leaving the
 * tab; it holds no Android type (D-02). It only runs what [apply], [replace] and [create] are given, and
 * those are called only from the admin's confirm tap (D-24).
 *
 * Three modes (`assistant-proposal-targets`): [apply] appends, [replace] adds, then removes, then reorders
 * (R1), [create] creates a jam and loads every song into the date the server echoed (C2). Failure handling
 * (R4): a per-song refusal lets the run continue, a server duplicate is Skipped, every other outcome aborts
 * the run, because later calls would fail alike and an offline answer may hide a write.
 */
class ProposalApplier(
    adder: SetlistSongAdder,
    remover: SetlistSongRemover,
    mover: SetlistSongMover,
    creator: JamCreator,
    private val scope: CoroutineScope,
    private val runLock: RunLock = RunLock(),
) {
    private val lock = Any()
    private val state = MutableStateFlow<Map<Long, ApplyRun>>(emptyMap())
    private val stopRequests = mutableSetOf<Long>()
    private val store = RunStore(state)
    private val steps = ApplySteps(adder, remover, mover, creator, store, ::stopRequested)

    /** The latest run of each chat turn, by turn id. */
    val runs: StateFlow<Map<Long, ApplyRun>> = state.asStateFlow()

    /** Appends [plan]'s songs for [turnId]. False, doing nothing, when any run is still running. */
    fun apply(turnId: Long, plan: ApplyPlan.Ready): Boolean {
        val songs = plan.toAdd.map { SongApply(it, SongApply.State.Waiting) } +
            plan.skipped.map { SongApply(it, SongApply.State.Skipped) }
        val initial = ApplyRun(ApplyMode.ADD, plan.jamDate, plan.toAdd.size, songs, ApplyRun.State.Running)
        return start(turnId, initial) { steps.append(turnId, plan) }
    }

    /** Makes the jam's list equal to the proposal for [turnId]: adds, then removes, then moves. */
    fun replace(turnId: Long, plan: ReplacePlan.Ready): Boolean {
        val keptIds = plan.kept.mapTo(mutableSetOf()) { it.songId }
        val initial = ApplyRun(
            mode = ApplyMode.REPLACE,
            jamDate = plan.jamDate,
            planned = plan.toAdd.size,
            songs = plan.toAdd.map { SongApply(it, SongApply.State.Waiting) } +
                plan.proposed.filter { it.songId in keptIds }.map { SongApply(it, SongApply.State.Kept) },
            state = ApplyRun.State.Running,
            removals = plan.toRemove.map { SongRemoval(it, SongRemoval.State.Waiting) },
            order = OrderStep.Waiting,
            target = plan.target,
        )
        return start(turnId, initial) { steps.replace(turnId, plan) }
    }

    /** Creates the jam and loads [songs] into the date the server echoed, for [turnId]. */
    fun create(turnId: Long, date: LocalDate, startTime: LocalTime, venue: String, songs: List<ProposedSong>): Boolean {
        val initial = ApplyRun(
            mode = ApplyMode.CREATE,
            jamDate = null,
            planned = songs.size,
            songs = songs.map { SongApply(it, SongApply.State.Waiting) },
            state = ApplyRun.State.Running,
            jam = JamStep.Creating,
        )
        return start(turnId, initial) { steps.create(turnId, date, startTime, venue, songs) }
    }

    /** Asks the run of [turnId] to stop after the call in flight; the rest are not attempted. */
    fun stop(turnId: Long) {
        synchronized(lock) {
            if (state.value[turnId]?.state == ApplyRun.State.Running) stopRequests += turnId
        }
    }

    private fun stopRequested(turnId: Long) = synchronized(lock) { turnId in stopRequests }

    private fun start(turnId: Long, initial: ApplyRun, body: suspend () -> Unit): Boolean = synchronized(lock) {
        if (state.value.values.any { it.state == ApplyRun.State.Running } || !runLock.tryAcquire()) return false
        stopRequests.remove(turnId)
        state.update { it + (turnId to initial) }
        scope.launch {
            try {
                body()
            } finally {
                // Cancellation or an unexpected exception: never leave a run "Running" (it blocks every later run).
                if (state.value[turnId]?.state == ApplyRun.State.Running) store.end(turnId, ApplyRun.State.Interrupted)
                runLock.release()
            }
        }
        true
    }
}
