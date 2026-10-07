package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * The live window (`live-refresh-during-jam`, Decisions 2 and 3): while "now" is between
 * [OPENS_BEFORE] a jam's start and [CLOSES_AFTER] it, Buenos Aires time, Próxima jam refreshes every
 * [INTERVAL] while the screen is visible, and every [FAILURE_INTERVAL] after a failed refresh.
 */
internal object LiveRefresh {
    val OPENS_BEFORE: Duration = Duration.ofMinutes(30)
    val CLOSES_AFTER: Duration = Duration.ofHours(4)

    /** The period after a successful refresh (L1). The one lever on the load per jam night. */
    val INTERVAL: Duration = Duration.ofSeconds(30)

    /** The back-off after a failed refresh (offline included): the repositories' own retry interval. */
    val FAILURE_INTERVAL: Duration = CatalogRepository.MIN_RETRY_INTERVAL
}

/** The jam's start and time, the only parts of a jam the window depends on. */
internal data class LiveJam(val date: LocalDate, val startTime: LocalTime)

/**
 * The jams that may have an open window: the upcoming one and the latest past one, because a jam
 * turns historical at 00:00 but its window runs to start + 4 h (L4). Only date and start time, so a
 * new snapshot of the same jams gives an equal list.
 */
internal fun JamsSnapshot.liveJams(): List<LiveJam> =
    listOfNotNull(upcoming, past.firstOrNull()).map { LiveJam(it.date, it.startTime) }

/** `[start − 30 min, start + 4 h)` of [jam] in [zone]: start inclusive, end exclusive. */
internal fun liveWindow(jam: Jam, zone: ZoneId): OpenEndRange<Instant> =
    liveWindow(LiveJam(jam.date, jam.startTime), zone)

internal fun liveWindow(jam: LiveJam, zone: ZoneId): OpenEndRange<Instant> {
    val start = jam.date.atTime(jam.startTime).atZone(zone).toInstant()
    return start.minus(LiveRefresh.OPENS_BEFORE)..<start.plus(LiveRefresh.CLOSES_AFTER)
}

/** Uniform in `[0, INTERVAL)`, so phones open when a window opens do not all call in the same second. */
internal fun Random.jitter(): Duration = Duration.ofMillis(nextLong(LiveRefresh.INTERVAL.toMillis()))

/**
 * The periodic refresh. Outside every window it waits for the next one to open (plus [jitter]) or
 * returns when none will. On entering a window it refreshes at once, unless [lastFetchedAt] is
 * younger than [LiveRefresh.INTERVAL], in which case it waits the remainder; then it calls [refresh]
 * (true when the refresh updated the cache) and waits [LiveRefresh.INTERVAL] after a success or
 * [LiveRefresh.FAILURE_INTERVAL] after a failure, measured from when the refresh returned, so slow
 * answers never overlap. The window is checked again before every call. Cancel it to stop it.
 */
internal suspend fun runLiveRefresh(
    windows: List<OpenEndRange<Instant>>,
    now: () -> Instant,
    lastFetchedAt: () -> Instant?,
    jitter: () -> Duration,
    refresh: suspend () -> Boolean,
) {
    while (true) {
        val current = now()
        if (windows.any { current in it }) {
            waitForStaleData(current, lastFetchedAt())
            while (windows.any { now() in it }) {
                val period = if (refresh()) LiveRefresh.INTERVAL else LiveRefresh.FAILURE_INTERVAL
                delay(period.toMillis())
            }
        } else {
            val opensAt = windows.map { it.start }.filter { it > current }.minOrNull() ?: return
            delay(Duration.between(current, opensAt).plus(jitter()).toMillis())
        }
    }
}

/** Data fetched less than [LiveRefresh.INTERVAL] ago is fresh enough: wait until it is not. */
private suspend fun waitForStaleData(now: Instant, fetchedAt: Instant?) {
    val age = fetchedAt?.let { Duration.between(it, now) } ?: return
    if (!age.isNegative && age < LiveRefresh.INTERVAL) delay(LiveRefresh.INTERVAL.minus(age).toMillis())
}
