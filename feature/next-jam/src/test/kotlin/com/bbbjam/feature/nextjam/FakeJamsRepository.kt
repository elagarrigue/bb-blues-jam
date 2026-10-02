package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Emits whatever the test puts in [snapshots]; counts refreshes, which the presenter must never start. */
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
