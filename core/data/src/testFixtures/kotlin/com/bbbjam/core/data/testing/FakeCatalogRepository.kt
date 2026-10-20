package com.bbbjam.core.data.testing

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.catalog.RefreshOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Emits whatever the test puts in [snapshots] (a new subscriber gets the latest again); counts subscriptions and
 * refreshes.
 */
class FakeCatalogRepository : CatalogRepository {
    val snapshots = MutableSharedFlow<CatalogSnapshot>(replay = 1)
    var subscriptions = 0
        private set
    var refreshCalls = 0
        private set

    override fun observeCatalog(): Flow<CatalogSnapshot> {
        subscriptions++
        return snapshots
    }

    override suspend fun refresh(): RefreshOutcome {
        refreshCalls++
        return RefreshOutcome.Failed(DataFailure.Offline)
    }
}
