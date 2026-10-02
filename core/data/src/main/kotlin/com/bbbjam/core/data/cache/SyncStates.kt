package com.bbbjam.core.data.cache

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.decodeDataFailure
import com.bbbjam.core.data.encode
import java.time.Duration
import java.time.Instant

/** The [Freshness] this row describes; nothing fetched yet when there is no row. */
internal fun SyncStateEntity?.toFreshness(isRefreshing: Boolean): Freshness = Freshness(
    fetchedAt = this?.fetchedAt?.let(Instant::ofEpochMilli),
    lastFailure = this?.failure?.let(::decodeDataFailure),
    isRefreshing = isRefreshing,
)

/**
 * Stale ([Freshness.isStale]) and the last attempt, if any, at least [minRetryInterval] ago: when
 * collecting a resource starts a background refresh.
 */
internal fun SyncStateEntity?.isRefreshDue(
    now: Instant,
    minRetryInterval: Duration = CatalogRepository.MIN_RETRY_INTERVAL,
): Boolean {
    val stale = toFreshness(isRefreshing = false).isStale(now)
    val attemptedAt = this?.attemptedAt?.let(Instant::ofEpochMilli)
    val retryAllowed = attemptedAt == null || !now.isBefore(attemptedAt.plus(minRetryInterval))
    return stale && retryAllowed
}

/**
 * The cache refused a write. Room surfaces SQLite errors as `android.database.SQLException` (a full
 * disk is its subclass `SQLiteFullException`). Catch that type, never `IllegalStateException`:
 * coroutine cancellation is one.
 */
internal fun SQLException.toStorageFailure(): DataFailure = DataFailure.Storage(javaClass.simpleName)

/**
 * Records [failure] through [record] (a DAO's `recordFailure` for one resource) and returns it, or
 * returns [DataFailure.Storage] when the cache refuses even that write.
 */
internal suspend fun recordedFailure(failure: DataFailure, record: suspend (encoded: String) -> Unit): DataFailure =
    try {
        record(failure.encode())
        failure
    } catch (e: SQLException) {
        e.toStorageFailure()
    }

/**
 * A mapper threw on a domain constructor: a mapper bug, reported as an invalid response (first line
 * of the message, at most 200 characters) instead of crashing the refresh.
 */
internal fun mappingFailure(error: IllegalArgumentException): DataFailure =
    DataFailure.InvalidResponse("mapping: ${error.message.orEmpty().lineSequence().first()}".take(MAX_DETAIL))

private const val MAX_DETAIL = 200
