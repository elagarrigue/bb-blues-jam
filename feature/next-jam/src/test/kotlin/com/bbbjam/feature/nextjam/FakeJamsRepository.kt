package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Emits whatever the test puts in [snapshots] (a new subscriber gets the latest one again). Counts
 * subscriptions and refreshes: the presenter starts a refresh only on a Retry event (`list-states`),
 * a pull or the live window (`live-refresh-during-jam`), and a Retry or a pull also re-subscribes.
 *
 * Like the real repository, [refresh] is single-flight: [refreshCalls] counts calls, [fetches] the
 * fetches they started (a call while one is open joins it). With [hold] set, a fetch stays open until
 * the test completes it; [fails] makes the next fetches fail.
 */
class FakeJamsRepository : JamsRepository {
    val snapshots = MutableSharedFlow<JamsSnapshot>(replay = 1)
    var refreshCalls = 0
        private set
    var fetches = 0
        private set
    var subscriptions = 0
        private set
    var hold: CompletableDeferred<Unit>? = null
    var fails = false
    private var inFlight: CompletableDeferred<JamsRefreshOutcome>? = null

    override fun observeJams(): Flow<JamsSnapshot> {
        subscriptions++
        return snapshots
    }

    override suspend fun refresh(): JamsRefreshOutcome {
        refreshCalls++
        inFlight?.let { return it.await() }
        fetches++
        val call = CompletableDeferred<JamsRefreshOutcome>()
        inFlight = call
        try {
            hold?.await()
            val outcome = if (fails) {
                JamsRefreshOutcome.Failed(DataFailure.Offline)
            } else {
                JamsRefreshOutcome.Updated(
                    jamCount = 0,
                    rejected = emptyList(),
                    issues = emptyList(),
                    heldBack = emptyList(),
                )
            }
            call.complete(outcome)
            return outcome
        } finally {
            inFlight = null
            call.cancel()
        }
    }
}
