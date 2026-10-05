package com.bbbjam.debug

import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Debug-only decorator (debug-demo-upcoming-jam): when a [real] snapshot has no upcoming jam, it
 * fills it with [DemoUpcomingJam] dated from [calendar]'s today in Buenos Aires. A real upcoming jam
 * always wins and passes through unchanged; `past` and `freshness` are never touched; [refresh]
 * delegates. Nothing is written to the cache or sent anywhere. With [draft] the demo jam is a
 * `DRAFT` with the same songs (`unpublished-setlist-state`).
 */
internal class DemoUpcomingJamRepository(
    private val real: JamsRepository,
    private val calendar: JamCalendar,
    private val draft: Boolean = false,
) : JamsRepository {
    override fun observeJams(): Flow<JamsSnapshot> = real.observeJams().map { snapshot ->
        if (snapshot.upcoming ==
            null
        ) {
            snapshot.copy(upcoming = DemoUpcomingJam.on(calendar.today(), draft))
        } else {
            snapshot
        }
    }

    override suspend fun refresh(): JamsRefreshOutcome = real.refresh()
}
