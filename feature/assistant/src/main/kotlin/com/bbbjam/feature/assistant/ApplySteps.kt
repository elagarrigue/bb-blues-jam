package com.bbbjam.feature.assistant

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.MoveSongOutcome
import com.bbbjam.core.data.setlist.RemoveSongOutcome
import com.bbbjam.core.data.setlist.SetlistSongAdder
import com.bbbjam.core.data.setlist.SetlistSongMover
import com.bbbjam.core.data.setlist.SetlistSongRemover
import com.bbbjam.core.model.JamSong
import java.time.LocalDate
import java.time.LocalTime

/**
 * The phases of a [ProposalApplier] run, written into [store] as they happen. A phase returns true when the
 * run should go on and false once it ended the run itself (stopped or aborted). Internal to the applier: the
 * only way in is its three entry points.
 */
internal class ApplySteps(
    private val adder: SetlistSongAdder,
    private val remover: SetlistSongRemover,
    private val mover: SetlistSongMover,
    private val creator: JamCreator,
    private val store: RunStore,
    private val stopRequested: (Long) -> Boolean,
) {
    suspend fun append(turnId: Long, plan: ApplyPlan.Ready) {
        if (addAll(turnId, plan.jamDate, plan.toAdd)) store.end(turnId, ApplyRun.State.Finished)
    }

    suspend fun replace(turnId: Long, plan: ReplacePlan.Ready) {
        if (addAll(turnId, plan.jamDate, plan.toAdd) && removeAll(turnId, plan) && reorder(turnId, plan)) {
            store.end(turnId, ApplyRun.State.Finished)
        }
    }

    suspend fun create(turnId: Long, date: LocalDate, startTime: LocalTime, venue: String, songs: List<ProposedSong>) {
        when (val outcome = creator.createJam(date, startTime, venue)) {
            is CreateJamOutcome.Created -> {
                store.update(turnId) { it.copy(jam = JamStep.Created(outcome.date), jamDate = outcome.date) }
                if (addAll(turnId, outcome.date, songs)) store.end(turnId, ApplyRun.State.Finished)
            }

            is CreateJamOutcome.NotCreated -> {
                store.update(turnId) { it.copy(jam = JamStep.Failed(JamFailure.Write(outcome.reason))) }
                store.end(turnId, ApplyRun.State.Aborted(outcome.reason))
            }

            CreateJamOutcome.ScriptOutdated -> {
                store.update(turnId) { it.copy(jam = JamStep.Failed(JamFailure.ScriptOutdated)) }
                store.end(turnId, ApplyRun.State.ScriptOutdated)
            }
        }
    }

    private suspend fun addAll(turnId: Long, date: LocalDate, songs: List<ProposedSong>): Boolean {
        for (song in songs) {
            if (!addOne(turnId, date, song)) return false
        }
        return true
    }

    private suspend fun addOne(turnId: Long, date: LocalDate, song: ProposedSong): Boolean {
        if (stopRequested(turnId)) return store.stop(turnId)
        store.setSong(turnId, song, SongApply.State.Adding)
        return when (val step = classify(adder.addSong(date, song.songId, song.key))) {
            is Step.Record -> {
                store.setSong(turnId, song, step.state)
                true
            }

            is Step.Abort -> {
                store.setSong(turnId, song, SongApply.State.Failed(step.reason))
                store.abort(turnId, step.reason)
            }
        }
    }

    private suspend fun removeAll(turnId: Long, plan: ReplacePlan.Ready): Boolean {
        for (song in plan.toRemove) {
            if (!removeOne(turnId, plan.jamDate, song)) return false
        }
        return true
    }

    private suspend fun removeOne(turnId: Long, date: LocalDate, song: JamSong): Boolean {
        if (stopRequested(turnId)) return store.stop(turnId)
        store.setRemoval(turnId, song.songId, SongRemoval.State.Removing)
        val reason = (remover.removeSong(date, song.songId) as? RemoveSongOutcome.NotRemoved)?.reason
        val code = (reason as? WriteOutcome.Rejected)?.code
        return when {
            reason == null || code == NOT_LISTED -> {
                store.setRemoval(turnId, song.songId, SongRemoval.State.Removed)
                true
            }

            code == DUPLICATE_ID -> {
                store.setRemoval(turnId, song.songId, SongRemoval.State.Failed(reason))
                true
            }

            else -> {
                store.setRemoval(turnId, song.songId, SongRemoval.State.Failed(reason))
                store.abort(turnId, reason)
            }
        }
    }

    /** Orders the list as the proposal, unless a removal failed (the run is a failure anyway, so it is not tried). */
    private suspend fun reorder(turnId: Long, plan: ReplacePlan.Ready): Boolean {
        val run = store.run(turnId)
        return if (run.removalFailedCount > 0) {
            store.setOrder(turnId, OrderStep.NotAttempted)
            true
        } else {
            moveAll(turnId, plan, run)
        }
    }

    private suspend fun moveAll(turnId: Long, plan: ReplacePlan.Ready, run: ApplyRun): Boolean {
        val removed = run.removals.filter { it.state == SongRemoval.State.Removed }.map { it.song.songId }.toSet()
        val added = run.songs.filter { it.state is SongApply.State.Added || it.state == SongApply.State.Skipped }
            .map { it.song.songId }
        val present = plan.current.filter { it !in removed } + added.filter { it !in plan.current }
        val moves = reorderMoves(present, plan.target.filter { it in present })
        for ((index, move) in moves.withIndex()) {
            if (!moveOne(turnId, plan.jamDate, move, index, moves.size)) return false
        }
        store.setOrder(turnId, OrderStep.Ordered)
        return true
    }

    private suspend fun moveOne(turnId: Long, date: LocalDate, move: SongMove, done: Int, total: Int): Boolean {
        if (stopRequested(turnId)) return store.stop(turnId)
        store.setOrder(turnId, OrderStep.Moving(done, total))
        val outcome = mover.moveSong(date, move.songId, move.toPosition)
        return if (outcome is MoveSongOutcome.NotMoved) {
            store.setOrder(turnId, OrderStep.Failed(outcome.reason))
            store.abort(turnId, outcome.reason)
        } else {
            true
        }
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
        const val NOT_LISTED = "song_not_in_setlist"
        const val DUPLICATE_ID = "duplicate_song"
        val PER_SONG_CODES = setOf("unknown_song", "invalid_song", "invalid_key")
    }
}
