package com.bbbjam.feature.songdetail

import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Emits whatever the test puts in [snapshots] (a new subscriber gets the latest one again). Its own
 * copy, not next-jam's: features share no code, test code included (D-03). The detail never
 * refreshes on its own, so [refreshCalls] must stay 0.
 */
class FakeJamsRepository : JamsRepository {
    val snapshots = MutableSharedFlow<JamsSnapshot>(replay = 1)
    var refreshCalls = 0
        private set

    override fun observeJams(): Flow<JamsSnapshot> = snapshots

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
