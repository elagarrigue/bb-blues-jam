package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam as DomainJam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
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
 *
 * Admin (`admin-add-song-to-setlist`, D-15): the flag comes from [adminSession]; it only decides
 * what is drawn, never what is allowed (Apps Script authorizes every write). For the admin the
 * list maps `jam.setlist`, draft songs included, and the model gains [NextJamAdminUiModel]: the
 * draft badge, the pending and failed adds of [setlist] and "Agregar tema", which calls
 * [Params.onAddSong]. Musicians keep `setlistForMusicians()` and a model with no admin part. When
 * the flag turns on, the presenter asks for one refresh, which is the admin read (draft songs);
 * whether it already did is saveable, so returning to the tab does not repeat it.
 */
class NextJamPresenter(
    private val jams: JamsRepository,
    private val calendar: JamCalendar,
    private val adminSession: AdminSession,
    private val setlist: SetlistRepository,
) : Presenter<NextJamUiModel, NextJamPresenter.Params> {

    /**
     * [onOpenSong] receives the jam's date and the song's position when a row's detail is opened;
     * [onAddSong] the jam's date when the admin taps "Agregar tema". [onOpenSong] stays last, so
     * `Params { date, position -> … }` still names it.
     */
    data class Params(
        val onAddSong: (jamDate: LocalDate) -> Unit = {},
        val onOpenSong: (jamDate: LocalDate, position: Int) -> Unit = { _, _ -> },
    )

    @Composable
    override fun present(params: Params): NextJamUiModel {
        val currentOnOpenSong by rememberUpdatedState(params.onOpenSong)
        val onOpenSong: (LocalDate, Int) -> Unit = { date, position -> currentOnOpenSong(date, position) }
        val currentOnAddSong by rememberUpdatedState(params.onAddSong)
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)
        val adds by remember { setlist.observeAdds() }.collectAsState(initial = emptyList())
        // Read only by the effect, never by the composition, so writing it recomposes nothing.
        var adminRefreshed by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            // The flag's real values only (not the composition's initial false), so coming back to
            // the tab with admin on does not count as a new login.
            adminSession.observeIsAdmin().collect { flag ->
                if (!flag) {
                    adminRefreshed = false
                } else if (!adminRefreshed) {
                    adminRefreshed = true
                    jams.refresh()
                }
            }
        }
        val admin = if (isAdmin) {
            AdminState(adds, onAddSong = { date -> currentOnAddSong(date) }, onDismiss = { id -> setlist.dismiss(id) })
        } else {
            null
        }
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
        return snapshot?.toUiModel(today, expanded, onToggle, filter, onFilterChange, now, onRetry, onOpenSong, admin)
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
    admin: AdminState? = null,
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
            // A draft is never shown to a musician, whatever the read returned (D-04, access model);
            // the admin sees the songs the admin read brought.
            setlist = (if (admin == null) jam.setlistForMusicians() else jam.setlist).toUiModel(
                jam.date,
                RowState(expanded, onToggle, filter, onFilterChange, onOpenSong, isAdmin = admin != null),
            ),
            staleness = staleness,
            admin = admin?.toUiModel(jam),
        )

        fetchedAt != null -> NextJamUiModel.NoUpcomingJam(
            empty = EmptyStateUiModel(NextJamCopy.NO_UPCOMING_TITLE, NextJamCopy.NO_UPCOMING_JAM),
            staleness = staleness,
            adminHint = if (admin == null) null else NextJamCopy.ADMIN_NO_UPCOMING,
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
    val isAdmin: Boolean,
)

/**
 * What the presenter knows only for an admin: the adds of `SetlistRepository.observeAdds()` and
 * the two admin callbacks. Null for musicians.
 */
internal class AdminState(
    val adds: List<SetlistAdd>,
    val onAddSong: (LocalDate) -> Unit,
    val onDismiss: (Long) -> Unit,
)

/**
 * The admin layer for [jam]: the adds for its date only, sending ones as pending rows and failed
 * ones as cards, and "Agregar tema" only while the setlist is readable. Handlers are keyed by what
 * they act on, so a model for another jam or entry never keeps an earlier handler.
 */
private fun AdminState.toUiModel(jam: DomainJam): NextJamAdminUiModel {
    val mine = adds.filter { it.jamDate == jam.date }
    val isDraft = jam.status == JamStatus.DRAFT
    return NextJamAdminUiModel(
        draftBadge = if (isDraft) NextJamCopy.DRAFT_BADGE else null,
        draftNote = if (isDraft) NextJamCopy.DRAFT_NOTE else null,
        pending = mine.filter { it.state == SetlistAdd.State.Sending }
            .map { PendingRowUiModel(it.id, it.title, NextJamCopy.ADDING) },
        failures = mine.mapNotNull { add ->
            (add.state as? SetlistAdd.State.Failed)?.let { failed ->
                AddFailureUiModel(
                    id = add.id,
                    title = NextJamCopy.addFailed(add.title),
                    message = failureMessage(failed.reason),
                    dismissLabel = NextJamCopy.CLOSE,
                    events = EventHandler(key = add.id) { onDismiss(add.id) },
                )
            }
        },
        addSong = if (jam.setlist is Setlist.Available) {
            AddSongActionUiModel(NextJamCopy.ADD_SONG, EventHandler(key = jam.date) { onAddSong(jam.date) })
        } else {
            null
        },
    )
}

/** The failure card's message by outcome and code (C1). */
internal fun failureMessage(reason: WriteOutcome): String = when (reason) {
    WriteOutcome.AccessRefused -> NextJamCopy.ACCESS_REFUSED

    WriteOutcome.Offline -> NextJamCopy.OFFLINE

    // Done never reaches a failure; the generic server line is the safe fallback.
    WriteOutcome.Unavailable, WriteOutcome.Done -> NextJamCopy.UNAVAILABLE

    is WriteOutcome.Rejected -> when (reason.code) {
        "song_already_in_setlist" -> NextJamCopy.ALREADY_LISTED
        "unknown_song" -> NextJamCopy.NOT_IN_CATALOG
        "unknown_jam", "jam_not_editable", "duplicate_date" -> NextJamCopy.JAM_CHANGED
        else -> NextJamCopy.SHEET_REFUSED
    }
}

/**
 * Rows the filter hides keep their expansion (keyed by position) and come back as they were. The
 * bar counts every song; the rows are those matching the selection, so the count line's number is
 * the number of rows. A setlist with no song is the empty block, with no bar: that is the empty
 * case, not the filter's no-results case.
 */
private fun Setlist.toUiModel(date: LocalDate, state: RowState): SetlistUiModel = when (this) {
    is Setlist.Available -> if (songs.isEmpty()) {
        // No song means no dropped row either (domain invariant), so no note is lost. The admin's
        // empty list is a draft being built, not a published empty list.
        SetlistUiModel.Empty(
            if (state.isAdmin) {
                EmptyStateUiModel(NextJamCopy.ADMIN_EMPTY_TITLE, NextJamCopy.ADMIN_EMPTY)
            } else {
                EmptyStateUiModel(NextJamCopy.EMPTY_SETLIST_TITLE, NextJamCopy.EMPTY_SETLIST)
            },
        )
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
