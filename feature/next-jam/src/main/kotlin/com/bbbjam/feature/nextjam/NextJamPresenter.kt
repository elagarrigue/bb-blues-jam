package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam as DomainJam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.filter.InstrumentFilterChange
import com.bbbjam.core.ui.filter.instrumentFilterBar
import com.bbbjam.core.ui.filter.matchesInstrumentFilter
import com.bbbjam.core.ui.filter.updatedBy
import com.bbbjam.core.ui.lineup.toLineupPanel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.listError
import com.bbbjam.core.ui.state.stalenessNotice
import com.bbbjam.core.ui.strip.toInstrumentChips
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Presents Próxima jam: the upcoming jam of [JamsRepository.observeJams], already cached, split in
 * Buenos Aires and title-resolved. Read-only; it never starts network work on its own (collecting
 * the flow may start the repository's own background refresh). The one exception is the musician's
 * Retry (`list-states`): it re-subscribes to the flow, which recovers from a failed local read, and
 * calls [JamsRepository.refresh], a read; the outcome comes back through the snapshot's freshness.
 * The subscription counter is created once, so an earlier model's Retry handler still works.
 *
 * "Today" and "now" come only from [calendar], read once per snapshot, so the header
 * agrees with the repository's upcoming/past split (P7), never from the device clock or UTC.
 *
 * Which rows are expanded is one [ExpandedRows] held here, keyed by jam date and position, not a
 * child presenter per row: a `remember` inside a presenter called in a loop is keyed by call order,
 * so a dropped row would hand its expansion to a neighbour. The state object is created once and
 * never recreated (no `remember(snapshot)` around it): row handlers have no key, so Compose may keep
 * an earlier model's handler, and that handler must still write through the same state.
 *
 * The instrument filter (`instrument-filter-chips`) is a set of instruments held the same way: created
 * once, never recreated, and not tied to the jam date, because it describes the musician, not the
 * jam. Handlers send an [InstrumentFilterChange] that is applied to the current set, never a set
 * computed from an earlier model.
 *
 * Opening a song's detail (`song-detail-screen`) is [Params.onOpenSong], a plain callback that
 * `:app` binds to navigation; this module never sees the navigation library (D-03). It is read
 * through `rememberUpdatedState`, so an earlier model's row handler calls the current callback.
 */
class NextJamPresenter(private val jams: JamsRepository, private val calendar: JamCalendar) :
    Presenter<NextJamUiModel, NextJamPresenter.Params> {

    /** [onOpenSong] receives the jam's date and the song's position when a row's detail is opened. */
    data class Params(val onOpenSong: (jamDate: LocalDate, position: Int) -> Unit = { _, _ -> })

    @Composable
    override fun present(params: Params): NextJamUiModel {
        val currentOnOpenSong by rememberUpdatedState(params.onOpenSong)
        val onOpenSong: (LocalDate, Int) -> Unit = { date, position -> currentOnOpenSong(date, position) }
        val scope = rememberCoroutineScope()
        var subscription by remember { mutableIntStateOf(0) }
        val snapshot by remember(subscription) { jams.observeJams() }.collectAsState(initial = null)
        val today = remember(snapshot) { calendar.today() }
        val now = remember(snapshot) { calendar.now() }
        var expanded by rememberSaveable(stateSaver = ExpandedRows.Saver) { mutableStateOf(ExpandedRows.NONE) }
        val onToggle: (LocalDate, Int) -> Unit = { date, position -> expanded = expanded.toggle(date, position) }
        var filter by rememberSaveable(stateSaver = InstrumentFilterSaver) { mutableStateOf(emptySet<Instrument>()) }
        val onFilterChange: (InstrumentFilterChange) -> Unit = { change -> filter = filter.updatedBy(change) }
        val onRetry: () -> Unit = {
            subscription++
            scope.launch { jams.refresh() }
        }
        return snapshot?.toUiModel(today, expanded, onToggle, filter, onFilterChange, now, onRetry, onOpenSong)
            ?: NextJamUiModel.Loading(NextJamCopy.LOADING)
    }
}

/** The selected instruments as their enum names: bundle-safe, so the filter survives rotation. */
internal val InstrumentFilterSaver: Saver<Set<Instrument>, Any> = listSaver(
    save = { selected -> selected.map { it.name } },
    restore = { names -> names.map { Instrument.valueOf(it) }.toSet() },
)

/**
 * Decision 3 of `next-jam-read-only-list` and Decision 1 of `list-states`: the only branches this
 * screen owns. [expanded] and [onToggle] come from the presenter's state (`song-row-expansion`),
 * [filter] and [onFilterChange] too (`instrument-filter-chips`); the defaults draw every row
 * collapsed and unfiltered. [now] ages the cached data for the staleness notice (by default the
 * fetch time, so a test that does not care sees "hace menos de un minuto"); [onRetry] is what the
 * error block's and the notice's retry call. [onOpenSong] is what a row's detail action calls.
 *
 * The notice is drawn exactly when something is cached and the latest refresh failed. Age alone
 * draws none: collecting the flow already refreshes stale data, and a notice on every open would
 * be noise.
 */
internal fun JamsSnapshot.toUiModel(
    today: LocalDate,
    expanded: ExpandedRows = ExpandedRows.NONE,
    onToggle: (LocalDate, Int) -> Unit = { _, _ -> },
    filter: Set<Instrument> = emptySet(),
    onFilterChange: (InstrumentFilterChange) -> Unit = {},
    now: Instant = freshness.fetchedAt ?: Instant.EPOCH,
    onRetry: () -> Unit = {},
    onOpenSong: (LocalDate, Int) -> Unit = { _, _ -> },
): NextJamUiModel {
    val jam = upcoming
    val fetchedAt = freshness.fetchedAt
    val failure = freshness.lastFailure
    // The kind of failure never reaches the musician: only offline or not (Decision 5).
    val isOffline = failure is DataFailure.Offline
    val staleness = if (fetchedAt != null && failure != null) {
        stalenessNotice(isOffline, Duration.between(fetchedAt, now), freshness.isRefreshing, onRetry)
    } else {
        null
    }
    return when {
        jam != null -> NextJamUiModel.Jam(
            header = jam.toHeader(today),
            // A draft is never shown to a musician, whatever the read returned (D-04, access model).
            setlist = jam.setlistForMusicians().toUiModel(
                jam.date,
                RowState(expanded, onToggle, filter, onFilterChange, onOpenSong),
            ),
            staleness = staleness,
        )

        fetchedAt != null -> NextJamUiModel.NoUpcomingJam(
            empty = EmptyStateUiModel(NextJamCopy.NO_UPCOMING_TITLE, NextJamCopy.NO_UPCOMING_JAM),
            staleness = staleness,
        )

        // Nothing was ever read: never claim there is no jam. An error only once a read has failed
        // and no retry is running; while one runs, the skeleton again.
        failure != null && !freshness.isRefreshing ->
            NextJamUiModel.Failed(listError(NextJamCopy.LOAD_FAILED, isOffline, onRetry))

        else -> NextJamUiModel.Loading(NextJamCopy.LOADING)
    }
}

private fun DomainJam.toHeader(today: LocalDate) = JamHeaderUiModel(
    date = jamDateLabel(date, startTime),
    venue = venue,
    timeRemaining = timeRemaining(today, date, startTime),
)

/** The presenter's list state, passed down as one value. */
private class RowState(
    val expanded: ExpandedRows,
    val onToggle: (LocalDate, Int) -> Unit,
    val filter: Set<Instrument>,
    val onFilterChange: (InstrumentFilterChange) -> Unit,
    val onOpenSong: (LocalDate, Int) -> Unit,
)

/**
 * Rows the filter hides keep their expansion (keyed by position) and come back as they were. The
 * bar counts every song; the rows are those matching the selection, so the count line's number is
 * the number of rows. A setlist with no song is the empty block, with no bar: that is the empty
 * case, not the filter's no-results case.
 */
private fun Setlist.toUiModel(date: LocalDate, state: RowState): SetlistUiModel = when (this) {
    is Setlist.Available -> if (songs.isEmpty()) {
        // No song means no dropped row either (domain invariant), so no note is lost.
        SetlistUiModel.Empty(EmptyStateUiModel(NextJamCopy.EMPTY_SETLIST_TITLE, NextJamCopy.EMPTY_SETLIST))
    } else {
        SetlistUiModel.Songs(
            rows = songs
                .filter { it.lineup.matchesInstrumentFilter(state.filter) }
                .map { song ->
                    song.toRow(
                        isExpanded = state.expanded.isExpanded(date, song.position),
                        toggle = { state.onToggle(date, song.position) },
                        openDetail = { state.onOpenSong(date, song.position) },
                    )
                },
            droppedRowsNote = if (droppedRows == 0) null else NextJamCopy.droppedRows(droppedRows),
            filterBar = instrumentFilterBar(songs.map { it.lineup }, state.filter, state.onFilterChange),
        )
    }

    Setlist.Withheld -> SetlistUiModel.Withheld(
        DraftSetlistUiModel(NextJamCopy.DRAFT_LABEL, NextJamCopy.DRAFT_TITLE, NextJamCopy.DRAFT_MESSAGE),
    )

    // The problem is for logs and the admin, not for musicians.
    is Setlist.Unavailable -> SetlistUiModel.Unavailable(NextJamCopy.SETLIST_UNAVAILABLE)
}

private const val POSITION_DIGITS = 2

private fun JamSong.toRow(isExpanded: Boolean, toggle: () -> Unit, openDetail: () -> Unit) = SongRowUiModel(
    position = position,
    positionLabel = position.toString().padStart(POSITION_DIGITS, '0'),
    title = title,
    key = key.value,
    keyDescription = NextJamCopy.keyDescription(key.value),
    instruments = lineup.toInstrumentChips(extraParticipants),
    artist = artist,
    isExpanded = isExpanded,
    stateDescription = if (isExpanded) NextJamCopy.ROW_EXPANDED else NextJamCopy.ROW_COLLAPSED,
    toggleLabel = if (isExpanded) NextJamCopy.HIDE_SLOTS else NextJamCopy.SHOW_SLOTS,
    lineup = lineup.toLineupPanel(extraParticipants),
    detailLabel = NextJamCopy.OPEN_DETAIL,
    events = EventHandler { event ->
        when (event) {
            SongRowUiModel.Event.ToggleExpanded -> toggle()
            SongRowUiModel.Event.OpenDetail -> openDetail()
        }
    },
)
