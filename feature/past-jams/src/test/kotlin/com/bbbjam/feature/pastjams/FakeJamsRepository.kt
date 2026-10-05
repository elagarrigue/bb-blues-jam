package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Emits whatever the test puts in [snapshots] (a new subscriber gets the latest one again). Counts
 * subscriptions and refreshes: the presenter starts a refresh only on a Retry event (`list-states`),
 * and a Retry also re-subscribes.
 */
class FakeJamsRepository : JamsRepository {
    val snapshots = MutableSharedFlow<JamsSnapshot>(replay = 1)
    var refreshCalls = 0
        private set
    var subscriptions = 0
        private set

    override fun observeJams(): Flow<JamsSnapshot> {
        subscriptions++
        return snapshots
    }

    override suspend fun refresh(): JamsRefreshOutcome {
        refreshCalls++
        return JamsRefreshOutcome.Updated(
            jamCount = 0,
            rejected = emptyList(),
            issues = emptyList(),
            heldBack = emptyList(),
        )
    }
}
