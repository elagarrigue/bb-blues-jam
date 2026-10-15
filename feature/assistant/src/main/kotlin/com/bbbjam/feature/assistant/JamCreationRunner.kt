package com.bbbjam.feature.assistant

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.data.setlist.JamCreator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the creation of a proposed jam stands. */
sealed interface JamCreation {
    data object Creating : JamCreation

    data object Created : JamCreation

    data class Failed(val reason: JamFailure) : JamCreation
}

/** Why a jam was not created. */
sealed interface JamFailure {
    data class Write(val reason: WriteOutcome) : JamFailure

    /** The deployed script does not know `createJam` yet. */
    data object ScriptOutdated : JamFailure

    /** The scope was cancelled mid-request: whether the Sheet got the row is unknown. */
    data object Interrupted : JamFailure
}

/**
 * Creates a confirmed jam through the [JamCreator] (the action contract, D-13). A Koin `single` with a
 * module-supplied [scope], so a request outlives the tab; it holds no Android type (D-02). [create] is
 * called only from the admin's confirm tap (D-24). After a success it asks the jams repository for one
 * refresh, outcome ignored: the repository already mirrored the draft.
 */
class JamCreationRunner(
    private val creator: JamCreator,
    private val jams: JamsRepository,
    private val scope: CoroutineScope,
    private val runLock: RunLock = RunLock(),
) {
    private val lock = Any()
    private val state = MutableStateFlow<Map<Long, JamCreation>>(emptyMap())

    /** The latest creation of each chat turn, by turn id. */
    val creations: StateFlow<Map<Long, JamCreation>> = state.asStateFlow()

    /** Starts creating [jam] for [turnId]. False, doing nothing, while any creation is running. */
    fun create(turnId: Long, jam: ProposedJam): Boolean = synchronized(lock) {
        if (state.value.values.any { it == JamCreation.Creating } || !runLock.tryAcquire()) return false
        state.update { it + (turnId to JamCreation.Creating) }
        scope.launch { execute(turnId, jam) }
        true
    }

    private suspend fun execute(turnId: Long, jam: ProposedJam) {
        try {
            val result = when (val outcome = creator.createJam(jam.date, jam.startTime, jam.venue)) {
                is CreateJamOutcome.Created -> JamCreation.Created
                is CreateJamOutcome.NotCreated -> JamCreation.Failed(JamFailure.Write(outcome.reason))
                CreateJamOutcome.ScriptOutdated -> JamCreation.Failed(JamFailure.ScriptOutdated)
            }
            state.update { it + (turnId to result) }
            if (result == JamCreation.Created) jams.refresh()
        } finally {
            // Cancellation or an unexpected exception: never leave a creation "Creating" (it blocks the next).
            state.update { creations ->
                if (creations[turnId] == JamCreation.Creating) {
                    creations + (turnId to JamCreation.Failed(JamFailure.Interrupted))
                } else {
                    creations
                }
            }
            runLock.release()
        }
    }
}
