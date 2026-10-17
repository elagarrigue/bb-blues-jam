package com.bbbjam.feature.assistant.run

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** The three ways a proposal's songs reach a jam (`assistant-proposal-targets`). */
enum class ApplyMode { ADD, REPLACE, CREATE }

/** One proposal song in a run and what happened to it. */
data class SongApply(val song: ProposedSong, val state: State) {
    sealed interface State {
        data object Waiting : State

        data object Adding : State

        data class Added(val position: Int) : State

        /** Already in the list: known before the run, or the server's `song_already_in_setlist`. */
        data object Skipped : State

        /** A replace keeps a song that was already listed, with its key and musicians. */
        data object Kept : State

        data class Failed(val reason: WriteOutcome) : State

        data object NotAttempted : State
    }
}

/** One listed song a replace takes out, and what happened to it. */
data class SongRemoval(val song: JamSong, val state: State) {
    sealed interface State {
        data object Waiting : State

        data object Removing : State

        data object Removed : State

        data class Failed(val reason: WriteOutcome) : State

        data object NotAttempted : State
    }
}

/** The reorder phase of a replace. */
sealed interface OrderStep {
    data object Waiting : OrderStep

    /** [done] of [total] moves are confirmed. */
    data class Moving(val done: Int, val total: Int) : OrderStep

    data object Ordered : OrderStep

    data class Failed(val reason: WriteOutcome) : OrderStep

    data object NotAttempted : OrderStep
}

/** The creation step of a create run. */
sealed interface JamStep {
    data object Creating : JamStep

    /** [date] is the one the server echoed: the songs are loaded into it. */
    data class Created(val date: LocalDate) : JamStep

    data class Failed(val reason: JamFailure) : JamStep
}

/**
 * One run of a proposal against a jam. [jamDate] is null until a create run has a jam. [planned] is how
 * many songs the run tries to add; [songs] also holds the ones skipped or kept without a call. [removals],
 * [order] and [target] belong to a replace, [jam] to a create.
 */
data class ApplyRun(
    val mode: ApplyMode,
    val jamDate: LocalDate?,
    val planned: Int,
    val songs: List<SongApply>,
    val state: State,
    val removals: List<SongRemoval> = emptyList(),
    val order: OrderStep? = null,
    val jam: JamStep? = null,
    val target: List<SongId> = emptyList(),
) {
    sealed interface State {
        data object Running : State

        /** Every planned step was tried (some may have failed one by one: see [isSuccess]). */
        data object Finished : State

        data object Stopped : State

        /** A systemic outcome ended it; [reason] is that outcome. */
        data class Aborted(val reason: WriteOutcome) : State

        /** A create run whose script does not know `createJam` yet. */
        data object ScriptOutdated : State

        /** The scope was cancelled (the process is going away). */
        data object Interrupted : State
    }

    val addedCount: Int get() = songs.count { it.state is SongApply.State.Added }
    val failedCount: Int get() = songs.count { it.state is SongApply.State.Failed }
    val skippedCount: Int get() = songs.count { it.state == SongApply.State.Skipped }
    val removedCount: Int get() = removals.count { it.state == SongRemoval.State.Removed }
    val removalFailedCount: Int get() = removals.count { it.state is SongRemoval.State.Failed }

    /** True only when the run finished and no step failed or was left out: the one case that is a success. */
    val isSuccess: Boolean
        get() = state == State.Finished && failedCount == 0 && removalFailedCount == 0 &&
            (order == null || order == OrderStep.Ordered) && (jam == null || jam is JamStep.Created)
}
