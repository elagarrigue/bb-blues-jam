package com.bbbjam.core.data.jams

import kotlinx.coroutines.flow.Flow

/**
 * The jams and their setlists, read from the Sheet through Apps Script and cached on the device.
 * Read-only in this slice: the Sheet owns past jams (D-04), and writes to the upcoming jam come with
 * the admin slices. Cache-first: the last stored jams are always served, offline included, with
 * their [com.bbbjam.core.data.Freshness].
 */
interface JamsRepository {
    /**
     * The cached jams split into the upcoming jam and the past ones ([JamsSnapshot]), re-emitted
     * whenever the cache, the catalog it resolves titles from, or the freshness changes. Collecting
     * it starts one background refresh when the cache is stale and the last attempt is at least
     * [com.bbbjam.core.data.catalog.CatalogRepository.MIN_RETRY_INTERVAL] old.
     */
    fun observeJams(): Flow<JamsSnapshot>

    /**
     * Reads the jams now and replaces the cache on success. Concurrent calls share one request. A
     * failure, a storage one included, leaves the cached jams untouched and is returned, not thrown.
     */
    suspend fun refresh(): JamsRefreshOutcome
}
