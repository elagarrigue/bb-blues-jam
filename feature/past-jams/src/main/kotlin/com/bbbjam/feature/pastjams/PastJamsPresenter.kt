package com.bbbjam.feature.pastjams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.listError
import com.bbbjam.core.ui.state.stalenessNotice
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Presents Anteriores: the past jams of [JamsRepository.observeJams], newest first. Read-only (D-04,
 * D-13); it never starts network work on its own (collecting the flow may start the repository's
 * own background refresh). The one exception is the musician's Retry (`list-states`): it
 * re-subscribes to the flow, which recovers from a failed local read, and calls
 * [JamsRepository.refresh], a read. The subscription counter is created once, so an earlier model's
 * Retry handler still works. "Now" comes only from [calendar], read once per snapshot, to age the
 * cached data for the staleness notice. [Params.onOpenJam] is read through `rememberUpdatedState`,
 * so an earlier model's Open handler calls the current callback.
 */
class PastJamsPresenter(private val jams: JamsRepository, private val calendar: JamCalendar) :
    Presenter<PastJamsUiModel, PastJamsPresenter.Params> {

    /** [onOpenJam] opens a past jam's detail (bound by `:app`). */
    data class Params(val onOpenJam: (jamDate: LocalDate) -> Unit = {})

    @Composable
    override fun present(params: Params): PastJamsUiModel {
        val scope = rememberCoroutineScope()
        val currentOnOpenJam by rememberUpdatedState(params.onOpenJam)
        val onOpenJam: (LocalDate) -> Unit = { date -> currentOnOpenJam(date) }
        var subscription by remember { mutableIntStateOf(0) }
        val snapshot by remember(subscription) { jams.observeJams() }.collectAsState(initial = null)
        val now = remember(snapshot) { calendar.now() }
        val onRetry: () -> Unit = {
            subscription++
            scope.launch { jams.refresh() }
        }
        return snapshot?.toUiModel(now, onRetry, onOpenJam)
            ?: PastJamsUiModel.Loading(PastJamsCopy.TITLE, PastJamsCopy.LOADING)
    }
}

/**
 * The state table of `docs/specs/past-jams-list.md` (Technical Approach 3), checked in order: rows
 * when there is a past jam; the empty block once something was fetched; the error block once a read
 * failed with nothing fetched and no retry running; the skeleton otherwise. The upcoming jam is
 * never listed. The notice is drawn exactly when something is cached and the latest refresh failed,
 * never on age alone. Rows are sorted here, newest first, not trusted to the repository's order.
 * [now] ages the cached data (by default the fetch time); [onRetry] is what both retries call;
 * [onOpenJam] is what a row with songs calls on Open.
 */
internal fun JamsSnapshot.toUiModel(
    now: Instant = freshness.fetchedAt ?: Instant.EPOCH,
    onRetry: () -> Unit = {},
    onOpenJam: (LocalDate) -> Unit = {},
): PastJamsUiModel {
    val fetchedAt = freshness.fetchedAt
    val failure = freshness.lastFailure
    // The kind of failure never reaches the musician: only offline or not.
    val isOffline = failure is DataFailure.Offline
    val staleness = if (fetchedAt != null && failure != null) {
        stalenessNotice(isOffline, Duration.between(fetchedAt, now), freshness.isRefreshing, onRetry)
    } else {
        null
    }
    val title = PastJamsCopy.TITLE
    return when {
        past.isNotEmpty() -> PastJamsUiModel.Jams(
            title = title,
            rows = past.sortedByDescending { it.date }.map { it.toRow(onOpenJam) },
            staleness = staleness,
        )

        fetchedAt != null -> PastJamsUiModel.Empty(
            title = title,
            empty = EmptyStateUiModel(PastJamsCopy.EMPTY_TITLE, PastJamsCopy.EMPTY_MESSAGE),
            staleness = staleness,
        )

        // Nothing was ever read: never claim there is no history. An error only once a read has
        // failed and no retry is running; while one runs, the skeleton again.
        failure != null && !freshness.isRefreshing ->
            PastJamsUiModel.Failed(title, listError(PastJamsCopy.LOAD_FAILED, isOffline, onRetry))

        else -> PastJamsUiModel.Loading(title, PastJamsCopy.LOADING)
    }
}

/** How many titles the hook shows before "y n más" (C1). */
internal const val HOOK_TITLES = 3

/** A draft's songs are never shown to a musician: the summary reads `setlistForMusicians()`. */
private fun Jam.toRow(onOpenJam: (LocalDate) -> Unit): PastJamRowUiModel {
    val summary = setlistForMusicians().toSummary()
    return PastJamRowUiModel(
        date = date,
        dateLabel = pastJamDateLabel(date),
        venue = venue,
        summary = summary,
        // Only a row with songs opens: a NotShown row would open on the same line.
        openLabel = PastJamsCopy.OPEN_JAM.takeIf { summary is PastJamSummary.Songs },
        events = EventHandler { event ->
            when (event) {
                PastJamRowUiModel.Event.Open -> onOpenJam(date)
            }
        },
    )
}

/**
 * The count is the readable songs: dropped rows are not counted, matching the rows `past-jam-detail`
 * draws. A list with no song is a line, never "0 temas" (C1).
 */
private fun Setlist.toSummary(): PastJamSummary = when (this) {
    is Setlist.Available -> if (songs.isEmpty()) {
        PastJamSummary.NotShown(PastJamsCopy.EMPTY_SETLIST)
    } else {
        // Songs are already in position order (a Setlist invariant).
        val titles = songs.map { it.title }
        val shown = titles.take(HOOK_TITLES).joinToString(", ")
        val rest = titles.size - HOOK_TITLES
        PastJamSummary.Songs(
            countLabel = PastJamsCopy.songCount(titles.size),
            hook = if (rest > 0) "$shown ${PastJamsCopy.andMore(rest)}" else shown,
        )
    }

    // A past draft (P1): the jam happened, so it is listed with its line.
    Setlist.Withheld -> PastJamSummary.NotShown(PastJamsCopy.SETLIST_NOT_PUBLISHED)

    // The problem is for logs and the admin, not for musicians.
    is Setlist.Unavailable -> PastJamSummary.NotShown(PastJamsCopy.SETLIST_UNAVAILABLE)
}
