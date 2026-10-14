package com.bbbjam.debug

import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Debug-only decorator (debug-demo-upcoming-jam): when a [real] snapshot has no upcoming jam, it
 * fills it with [DemoUpcomingJam] dated from [calendar]'s today in Buenos Aires. A real upcoming jam
 * always wins and passes through unchanged; `past` and `freshness` are never touched; [refresh]
 * delegates. Nothing is written to the cache or sent anywhere. With a `DRAFT` or `CREATED` [status]
 * the demo jam has that status and the same songs (`unpublished-setlist-state`, `jam-status-created`).
 *
 * With [live] (`live-refresh-during-jam`) the demo jam **replaces** the upcoming jam, whatever the
 * cache holds, and starts [DemoUpcomingJam.LIVE_START_AFTER] after the first emission (Buenos Aires,
 * truncated to the minute, computed once per instance, so once per process), so its live window is
 * open; every [refresh] is then logged with [log] as `demo jams refresh: <Updated|Failed> at <instant>`.
 */
internal class DemoUpcomingJamRepository(
    private val real: JamsRepository,
    private val calendar: JamCalendar,
    private val status: JamStatus = JamStatus.PUBLISHED,
    private val live: Boolean = false,
    private val log: (String) -> Unit = {},
) : JamsRepository {
    private val liveJam: Jam by lazy {
        val start = calendar.now().atZone(calendar.zone).truncatedTo(ChronoUnit.MINUTES)
            .plus(DemoUpcomingJam.LIVE_START_AFTER)
            .toLocalDateTime()
        DemoUpcomingJam.startingAt(start, status)
    }

    override fun observeJams(): Flow<JamsSnapshot> = real.observeJams().map { snapshot ->
        when {
            // The replaced real upcoming jam moves to the front of heldBack, so a musician's
            // fall-through past a demo BORRADOR jam is visible on a device (jam-status-created, S1).
            live -> snapshot.copy(
                upcoming = liveJam,
                heldBack = listOfNotNull(snapshot.upcoming?.takeIf { it.date != liveJam.date }) + snapshot.heldBack,
            )

            snapshot.upcoming == null -> snapshot.copy(upcoming = DemoUpcomingJam.on(calendar.today(), status))

            else -> snapshot
        }
    }

    override suspend fun refresh(): JamsRefreshOutcome {
        val outcome = real.refresh()
        if (live) {
            val name = if (outcome is JamsRefreshOutcome.Updated) "Updated" else "Failed"
            log("demo jams refresh: $name at ${calendar.now()}")
        }
        return outcome
    }
}
