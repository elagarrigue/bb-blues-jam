package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.PullRefreshUiModel
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The refreshes Próxima jam starts (`live-refresh-during-jam`): [onRetry] (`list-states`), the
 * [pullRefresh] model, and whether the snapshot must be shown [quiet], that is with
 * `isRefreshing = false`, because the only refresh running is a periodic one (Decision 4).
 */
internal class NextJamRefreshes(val quiet: Boolean, val pullRefresh: PullRefreshUiModel, val onRetry: () -> Unit) {
    /** [snapshot] as drawn: a periodic refresh never flashes "Actualizando…" nor turns an error into the skeleton. */
    fun shown(snapshot: JamsSnapshot): JamsSnapshot = if (quiet && snapshot.freshness.isRefreshing) {
        snapshot.copy(freshness = snapshot.freshness.copy(isRefreshing = false))
    } else {
        snapshot
    }
}

/**
 * Holds the refresh state of [NextJamPresenter], created once (no key), so an earlier model's
 * handlers still act on it.
 *
 * - The live loop ([runLiveRefresh]) runs only while [isResumed] and some jam of [snapshot] has a
 *   window; it is keyed by the jams' dates and start times, never by the snapshot, because every
 *   refresh emits a new snapshot and a restart would refresh again at once, forever.
 * - A pull calls [resubscribe] and one [JamsRepository.refresh]; a pull while one is running is
 *   ignored, so one pull is one call. A pull during a periodic call joins it (the repository is
 *   single-flight). Only a pull spins the indicator.
 * - Retry calls [resubscribe] and [JamsRepository.refresh], as before.
 *
 * A Retry or a pull marks a user refresh until its call returns; while one runs, the snapshot is
 * shown as it is, with "Actualizando…".
 */
@Composable
internal fun rememberNextJamRefreshes(
    jams: JamsRepository,
    calendar: JamCalendar,
    random: Random,
    isResumed: Boolean,
    snapshot: JamsSnapshot?,
    resubscribe: () -> Unit,
): NextJamRefreshes {
    val scope = rememberCoroutineScope()
    var periodicInFlight by remember { mutableStateOf(false) }
    var userRefreshes by remember { mutableIntStateOf(0) }
    var pulling by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(snapshot)
    val liveJams = snapshot?.liveJams().orEmpty()
    LaunchedEffect(isResumed, liveJams) {
        if (!isResumed || liveJams.isEmpty()) return@LaunchedEffect
        runLiveRefresh(
            windows = liveJams.map { liveWindow(it, calendar.zone) },
            now = calendar::now,
            lastFetchedAt = { latest?.freshness?.fetchedAt },
            jitter = { random.jitter() },
        ) {
            periodicInFlight = true
            try {
                val updated = jams.refresh() is JamsRefreshOutcome.Updated
                // The repository's "not refreshing" snapshot may arrive a moment after the call
                // returns; stay quiet until it does, so the notice never flashes for one frame.
                withTimeoutOrNull(LiveRefresh.INTERVAL.toMillis()) {
                    snapshotFlow { latest?.freshness?.isRefreshing != true }.first { it }
                }
                updated
            } finally {
                periodicInFlight = false
            }
        }
    }
    val userRefresh: (onDone: () -> Unit) -> Unit = { onDone ->
        userRefreshes++
        resubscribe()
        scope.launch {
            try {
                jams.refresh()
            } finally {
                userRefreshes--
                onDone()
            }
        }
    }
    val onPull: () -> Unit = {
        if (!pulling) {
            pulling = true
            userRefresh { pulling = false }
        }
    }
    return NextJamRefreshes(
        quiet = periodicInFlight && userRefreshes == 0,
        pullRefresh = PullRefreshUiModel(isRefreshing = pulling, events = EventHandler { onPull() }),
        onRetry = { userRefresh {} },
    )
}
