package com.bbbjam.core.data.catalog

import kotlinx.coroutines.flow.Flow

/**
 * The song catalog, read from the Sheet through Apps Script and cached on the device (D-04: the
 * Sheet owns it; the app never writes it). Cache-first: the last stored catalog is always served,
 * offline included, with its [com.bbbjam.core.data.Freshness].
 */
interface CatalogRepository {
    /**
     * The cached catalog in Sheet order, re-emitted whenever the cache or its freshness changes.
     * Collecting it starts one background refresh when the cache is stale and the last attempt is
     * at least [MIN_RETRY_INTERVAL] old.
     */
    fun observeCatalog(): Flow<CatalogSnapshot>

    /**
     * Reads the catalog now and replaces the cache on success. Concurrent calls share one request.
     * A failure leaves the cached songs untouched.
     */
    suspend fun refresh(): RefreshOutcome

    companion object {
        /** No automatic retry happens sooner than this after the last attempt (user approval Q3). */
        val MIN_RETRY_INTERVAL: java.time.Duration = java.time.Duration.ofSeconds(60)
    }
}
