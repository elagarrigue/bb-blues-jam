package com.bbbjam.core.data

import java.time.Duration
import java.time.Instant

/**
 * How current cached data is, for a staleness indicator. `now` always comes from the caller, which
 * owns the clock.
 *
 * @property fetchedAt when the last successful read was stored; null when nothing was ever fetched.
 * @property lastFailure why the latest attempt failed, or null when it succeeded (or none was made).
 * @property isRefreshing true while a read is in flight.
 */
data class Freshness(val fetchedAt: Instant?, val lastFailure: DataFailure?, val isRefreshing: Boolean) {
    /** Time since [fetchedAt], or null when nothing was ever fetched. */
    fun age(now: Instant): Duration? = fetchedAt?.let { Duration.between(it, now) }

    /** True when nothing was fetched, the latest attempt failed, or the data is older than [STALE_AFTER]. */
    fun isStale(now: Instant): Boolean {
        val age = age(now) ?: return true
        return lastFailure != null || age > STALE_AFTER
    }

    companion object {
        /** Data older than this is stale (user approval Q3, 1 October 2026). */
        val STALE_AFTER: Duration = Duration.ofMinutes(30)
    }
}
