package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.ExtraParticipantChange
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SlotClear
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam as DomainJam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.filter.InstrumentFilterChange
import com.bbbjam.core.ui.filter.instrumentFilterBar
import com.bbbjam.core.ui.filter.matchesInstrumentFilter
import com.bbbjam.core.ui.filter.updatedBy
import com.bbbjam.core.ui.lineup.toLineupPanel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.PullRefreshUiModel
import com.bbbjam.core.ui.state.listError
import com.bbbjam.core.ui.state.stalenessNotice
import com.bbbjam.core.ui.strip.toInstrumentChips
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * Presents Próxima jam: the upcoming jam of [JamsRepository.observeJams], already cached, split in
 * Buenos Aires and title-resolved. Read-only; it starts network work only through the refreshes
 * below (collecting the flow may also start the repository's own background refresh). The first is
 * the musician's Retry (`list-states`): it re-subscribes to the flow, which recovers from a failed local read, and
 * calls [JamsRepository.refresh], a read; the outcome comes back through the snapshot's freshness.
 * The subscription counter is created once, so an earlier model's Retry handler still works.
 *
 * "Today" and "now" come only from [calendar], read once per snapshot, so the header
 * agrees with the repository's upcoming/past split (P7), never from the device clock or UTC.
 *
 * Which rows are expanded is one [ExpandedRows] held here, keyed by jam date and song id, not a
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
 *
 * Removing a song (`admin-remove-song-from-setlist`, U1): which row is confirming is one saveable
 * key ("date|songId"), so the confirmation survives rotation and only one row confirms at a time.
 * Confirm acts only while that key is still the row's, clears it and launches
 * [SetlistRepository.removeSong] undispatched, so the `Sending` entry exists before the next frame
 * and a double tap removes once. The row shows "Quitando…" while a `Sending` removal exists for its
 * (date, songId); a failed one is a card, and the row is drawn as before.
 *
 * Setting a key (`admin-set-key`, O1): "Cambiar tonalidad" calls [Params.onSetKey]; the picker calls
 * the repository. While a key change of a row is sending, the row draws the latest pending key with
 * "Guardando…"; a failed change never overlays, so the row reverts to the cached key and a card says
 * why. Musicians never get an overlay.
 *
 * Live refresh (`live-refresh-during-jam`): besides Retry, the presenter refreshes on a pull (one
 * read per pull, the indicator spinning until it returns) and, while [Params.isResumed] and "now" is
 * in a jam's live window (30 min before its start to 4 h after, Buenos Aires), every 30 s (60 s after
 * a failure). Periodic refreshes are quiet: they never show "Actualizando…" nor the skeleton. See
 * [rememberNextJamRefreshes]. Every one is the read [JamsRepository.refresh]; a refresh landing while
 * an admin write is sending leaves the write's overlay alone (it comes from the setlist entries, not
 * from the snapshot), and the next refresh brings the written row.
 */
class NextJamPresenter(
    private val jams: JamsRepository,
    private val calendar: JamCalendar,
    private val adminSession: AdminSession,
    private val setlist: SetlistRepository,
    private val random: Random = Random.Default,
) : Presenter<NextJamUiModel, NextJamPresenter.Params> {

    /**
     * [onOpenSong] receives the jam's date and the song's position when a row's detail is opened;
     * [onAddSong] the jam's date when the admin taps "Agregar tema"; [onSetKey] the jam's date and the
     * song id when the admin taps "Cambiar tonalidad". [onOpenSong] stays last, so
     * `Params { date, position -> … }` still names it. [isResumed] is true while the screen is
     * visible (its lifecycle is at least `RESUMED`); only then does the live window refresh run.
     * False by default, so a presenter nobody marks visible never polls.
     */
    data class Params(
        val isResumed: Boolean = false,
        val onAddSong: (jamDate: LocalDate) -> Unit = {},
        val onSetKey: (jamDate: LocalDate, songId: SongId) -> Unit = { _, _ -> },
        val onAssignSlot: (
            jamDate: LocalDate,
            songId: SongId,
            instrument: Instrument,
            position: SlotPosition,
        ) -> Unit = { _, _, _, _ -> },
        val onOpenSong: (jamDate: LocalDate, position: Int) -> Unit = { _, _ -> },
    )

    @Composable
    override fun present(params: Params): NextJamUiModel {
        val currentOnOpenSong by rememberUpdatedState(params.onOpenSong)
        val onOpenSong: (LocalDate, Int) -> Unit = { date, position -> currentOnOpenSong(date, position) }
        val currentOnAddSong by rememberUpdatedState(params.onAddSong)
        val currentOnSetKey by rememberUpdatedState(params.onSetKey)
        val currentOnAssignSlot by rememberUpdatedState(params.onAssignSlot)
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)
        val adds by remember { setlist.observeAdds() }.collectAsState(initial = emptyList())
        val removes by remember { setlist.observeRemoves() }.collectAsState(initial = emptyList())
        val keyChanges by remember { setlist.observeKeyChanges() }.collectAsState(initial = emptyList())
        val assignments by remember { setlist.observeAssignments() }.collectAsState(initial = emptyList())
        val extraParticipantChanges by remember {
            setlist.observeExtraParticipantChanges()
        }.collectAsState(initial = emptyList())
        val slotClears by remember { setlist.observeSlotClears() }.collectAsState(initial = emptyList())
        val publishes by remember { setlist.observePublishes() }.collectAsState(initial = emptyList())
        var subscription by remember { mutableIntStateOf(0) }
        val snapshot by remember(subscription) { jams.observeJams() }.collectAsState(initial = null)
        val moveControls = rememberMoveControls(setlist, snapshot)
        val lineupControls = rememberLineupControls(setlist, snapshot, isAdmin)
        val admin = rememberNextJamAdminState(
            adminSession = adminSession,
            jams = jams,
            setlist = setlist,
            isAdmin = isAdmin,
            adds = adds,
            removes = removes,
            keyChanges = keyChanges,
            assignments = assignments,
            extraParticipantChanges = extraParticipantChanges,
            slotClears = slotClears,
            moves = moveControls.moves,
            publishes = publishes,
            lineup = lineupControls,
            onMove = moveControls.onMove,
            onAddSong = { date -> currentOnAddSong(date) },
            onSetKey = { date, songId -> currentOnSetKey(date, songId) },
            onAssignSlot = { date, songId, instrument, position ->
                currentOnAssignSlot(date, songId, instrument, position)
            },
        )
        val refreshes = rememberNextJamRefreshes(jams, calendar, random, params.isResumed, snapshot) { subscription++ }
        val today = remember(snapshot) { calendar.today() }
        val now = remember(snapshot) { calendar.now() }
        var expanded by rememberSaveable(stateSaver = ExpandedRows.Saver) { mutableStateOf(ExpandedRows.NONE) }
        val onToggle: (LocalDate, SongId) -> Unit = { date, songId -> expanded = expanded.toggle(date, songId) }
        var filter by rememberSaveable(stateSaver = InstrumentFilterSaver) { mutableStateOf(emptySet<Instrument>()) }
        val onFilterChange: (InstrumentFilterChange) -> Unit = { change -> filter = filter.updatedBy(change) }
        val model = snapshot?.let { refreshes.shown(it) }
            ?.toUiModel(today, expanded, onToggle, filter, onFilterChange, now, refreshes.onRetry, onOpenSong, admin)
            ?: NextJamUiModel.Loading(NextJamCopy.LOADING)
        return model.withPullRefresh(refreshes.pullRefresh)
    }
}

/** This model with [pullRefresh]; every state can be pulled. */
private fun NextJamUiModel.withPullRefresh(pullRefresh: PullRefreshUiModel): NextJamUiModel = when (this) {
    is NextJamUiModel.Loading -> copy(pullRefresh = pullRefresh)
    is NextJamUiModel.Failed -> copy(pullRefresh = pullRefresh)
    is NextJamUiModel.NoUpcomingJam -> copy(pullRefresh = pullRefresh)
    is NextJamUiModel.Jam -> copy(pullRefresh = pullRefresh)
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
    onToggle: (LocalDate, SongId) -> Unit = { _, _ -> },
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
                RowState(
                    expanded,
                    onToggle,
                    filter,
                    onFilterChange,
                    onOpenSong,
                    admin?.let {
                        RowAdmin(it, jam)
                    },
                ),
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
    val onToggle: (LocalDate, SongId) -> Unit,
    val filter: Set<Instrument>,
    val onFilterChange: (InstrumentFilterChange) -> Unit,
    val onOpenSong: (LocalDate, Int) -> Unit,
    val admin: RowAdmin?,
) {
    val isAdmin: Boolean
        get() = admin != null
}

/** The admin's row state for one jam: the admin state and the jam (its date and whether it is published). */
internal class RowAdmin(val state: AdminState, val jam: DomainJam) {
    val removal: RemovalState
        get() = state.removal
}

/**
 * What the presenter knows only for an admin: the adds of `SetlistRepository.observeAdds()`, the
 * admin callbacks, the [removal] state and the [keyChanges] of `observeKeyChanges()`. Null for
 * musicians.
 */
internal class AdminState(
    val adds: List<SetlistAdd>,
    val onAddSong: (LocalDate) -> Unit,
    val onDismiss: (Long) -> Unit,
    val removal: RemovalState = RemovalState(),
    val keyChanges: List<KeyChange> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val extraParticipantChanges: List<ExtraParticipantChange> = emptyList(),
    val slotClears: List<SlotClear> = emptyList(),
    val moves: List<SetlistMove> = emptyList(),
    val onSetKey: (LocalDate, SongId) -> Unit = { _, _ -> },
    val extraFormKey: String? = null,
    val extraName: String = "",
    val extraInstrument: String = "",
    val onExtraEvent: (LocalDate, SongId, ExtraParticipantEditorUiModel.Event) -> Unit = { _, _, _ -> },
    val lineupChanges: List<LineupChange> = emptyList(),
    val lineupEditing: String? = null,
    val reservations: LineupReservations = LineupReservations(),
    val onLineupEditor: (LocalDate, SongId, LineupEditorUiModel.Event) -> Unit = { _, _, _ -> },
    val onLineupCount: (LocalDate, SongId, Instrument, LineupEditorLineUiModel.Event) -> Unit = { _, _, _, _ -> },
    val onAssignSlot: (LocalDate, SongId, Instrument, SlotPosition) -> Unit = { _, _, _, _ -> },
    val onClearSlot: (LocalDate, SongId, Instrument, SlotPosition, String) -> Unit = { _, _, _, _, _ -> },
    val onMove: (LocalDate, SongId, Int) -> Unit = { _, _, _ -> },
    val publishes: List<SetlistPublish> = emptyList(),
    val publishConfirming: String? = null,
    val onRequestPublish: (LocalDate) -> Unit = {},
    val onCancelPublish: (LocalDate) -> Unit = {},
    val onConfirmPublish: (LocalDate) -> Unit = {},
    val onRetryPublish: (LocalDate, Long) -> Unit = { _, _ -> },
)

/**
 * The removals of `SetlistRepository.observeRemoves()`, the row that is [confirming] (its
 * [removalKey], or null) and the one handler every row's removal events go to.
 */
internal class RemovalState(
    val removes: List<SetlistRemove> = emptyList(),
    val confirming: String? = null,
    val onEvent: (LocalDate, SongId, RemovalUiModel.Event) -> Unit = { _, _, _ -> },
)

/** The confirming row's saveable key: bundle-safe, unique per jam and song. */
internal fun removalKey(date: LocalDate, songId: SongId): String = "$date|${songId.value}"

/**
 * The admin layer for [jam]: the adds for its date only, sending ones as pending rows and failed
 * ones as cards, and "Agregar tema" only while the setlist is readable. Handlers are keyed by what
 * they act on, so a model for another jam or entry never keeps an earlier handler.
 */
private fun AdminState.toUiModel(jam: DomainJam): NextJamAdminUiModel {
    val mine = adds.filter { it.jamDate == jam.date }
    val isDraft = jam.status == JamStatus.DRAFT
    return NextJamAdminUiModel(
        status = AdminStatusUiModel(
            badge = if (isDraft) NextJamCopy.DRAFT_BADGE else NextJamCopy.PUBLISHED_BADGE,
            isPublished = !isDraft,
            note = if (isDraft) NextJamCopy.DRAFT_NOTE else NextJamCopy.PUBLISHED_NOTE,
            publish = if (isDraft) publishUiModel(jam) else null,
        ),
        pending = mine.filter { it.state == SetlistAdd.State.Sending }
            .map { PendingRowUiModel(it.id, it.title, NextJamCopy.ADDING) },
        failures = failures(jam.date),
        addSong = if (jam.setlist is Setlist.Available) {
            AddSongActionUiModel(NextJamCopy.ADD_SONG, EventHandler(key = jam.date) { onAddSong(jam.date) })
        } else {
            null
        },
    )
}

/**
 * The failed adds, removals and key changes of [date], as cards in id order (call order). A key
 * change has its own message mapping ([keyFailureMessage]).
 */
private fun AdminState.failures(date: LocalDate): List<AddFailureUiModel> {
    val failedAdds = adds.filter { it.jamDate == date }.mapNotNull { add ->
        (add.state as? SetlistAdd.State.Failed)?.let {
            Triple(add.id, NextJamCopy.addFailed(add.title), failureMessage(it.reason))
        }
    }
    val failedRemoves = removal.removes.filter { it.jamDate == date }.mapNotNull { remove ->
        (remove.state as? SetlistRemove.State.Failed)?.let {
            Triple(remove.id, NextJamCopy.removeFailed(remove.title), failureMessage(it.reason))
        }
    }
    val failedKeys = keyChanges.filter { it.jamDate == date }.mapNotNull { change ->
        (change.state as? KeyChange.State.Failed)?.let {
            Triple(change.id, NextJamCopy.keyFailed(change.title), keyFailureMessage(it.reason))
        }
    }
    val failedLineups = lineupChanges.filter { it.jamDate == date }.mapNotNull { change ->
        (change.state as? LineupChange.State.Failed)?.let {
            Triple(change.id, NextJamCopy.lineupFailed(change.title), lineupFailureMessage(it.reason))
        }
    }
    val failedAssignments = assignments.filter { it.jamDate == date }.mapNotNull { assignment ->
        (assignment.state as? Assignment.State.Failed)?.let {
            Triple(
                assignment.id,
                NextJamCopy.assignmentFailed(assignment.musicianName.value, assignment.title),
                assignmentFailureMessage(it.reason),
            )
        }
    }
    val failedSlotClears = slotClears.filter { it.jamDate == date }.mapNotNull { clear ->
        (clear.state as? SlotClear.State.Failed)?.let {
            Triple(clear.id, NextJamCopy.CLEAR_FAILED, slotClearFailureMessage(it.reason))
        }
    }
    val failedMoves = moves.filter { it.jamDate == date }.mapNotNull { move ->
        (move.state as? SetlistMove.State.Failed)?.let {
            Triple(move.id, MoveCopy.failed(move.title), moveFailureMessage(it.reason))
        }
    }
    val failedExtras = extraParticipantChanges.filter { it.jamDate == date }.mapNotNull { change ->
        (change.state as? ExtraParticipantChange.State.Failed)?.let {
            Triple(change.id, NextJamCopy.EXTRA_FAILED, failureMessage(it.reason))
        }
    }
    val allFailures =
        failedAdds + failedRemoves + failedKeys + failedLineups + failedAssignments + failedSlotClears + failedMoves +
            failedExtras
    return allFailures.sortedBy { it.first }.map { (id, title, message) ->
        AddFailureUiModel(
            id = id,
            title = title,
            message = message,
            dismissLabel = NextJamCopy.CLOSE,
            events = EventHandler(key = id) { onDismiss(id) },
        )
    }
}

/**
 * Rows the filter hides keep their expansion (keyed by song id) and come back as they were. The
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
        val overlaidSongs = songs.map { song ->
            val admin = state.admin?.state
            if (admin == null) {
                song
            } else {
                song.copy(
                    lineup = admin.reservations.overlay(
                        date,
                        song.songId,
                        admin.lineupChanges.pendingLineup(date, song.songId, song.lineup),
                    ).overlayAssignments(date, song.songId, admin.assignments)
                        .overlaySlotClears(date, song.songId, admin.slotClears),
                )
            }
        }
        val shownEntries = state.admin?.state?.moves?.displayEntries(date, overlaidSongs)
            ?: overlaidSongs.map { DisplayedJamSong(it, it.position) }
        val idCounts = shownEntries.groupingBy { it.song.songId }.eachCount()
        SetlistUiModel.Songs(
            rows = shownEntries
                .filter { it.song.lineup.matchesInstrumentFilter(state.filter) }
                .map { entry ->
                    val song = entry.song
                    val displayedPosition = song.position
                    val rowKey = if (idCounts[song.songId] == 1) song.songId.value else "p$displayedPosition"
                    song.toRow(
                        position = displayedPosition,
                        rowKey = rowKey,
                        isExpanded = state.expanded.isExpanded(date, song.songId),
                        toggle = { state.onToggle(date, song.songId) },
                        openDetail = { state.onOpenSong(date, entry.cachedPosition) },
                    ).let { row -> state.admin?.decorate(row, song, shownEntries.size) ?: row }
                },
            droppedRowsNote = if (droppedRows == 0) null else NextJamCopy.droppedRows(droppedRows),
            filterBar = instrumentFilterBar(shownEntries.map { it.song.lineup }, state.filter, state.onFilterChange),
        )
    }

    Setlist.Withheld -> SetlistUiModel.Withheld(
        DraftSetlistUiModel(NextJamCopy.DRAFT_LABEL, NextJamCopy.DRAFT_TITLE, NextJamCopy.DRAFT_MESSAGE),
    )

    // The problem is for logs and the admin, not for musicians.
    is Setlist.Unavailable -> SetlistUiModel.Unavailable(NextJamCopy.SETLIST_UNAVAILABLE)
}

private const val POSITION_DIGITS = 2

/**
 * The admin's [row] of [song]: the latest pending key change drawn in place of the cached key, with
 * "Guardando…" (O1; a failed change never overlays, which is the revert), and the admin's part.
 * "Cambiar tonalidad" is keyed by (date, songId), as the removal's handlers.
 */
private fun RowAdmin.decorate(row: SongRowUiModel, song: JamSong, total: Int): SongRowUiModel {
    val pending = state.keyChanges.pendingKey(jam.date, song.songId)
    val setKey = SetKeyActionUiModel(
        NextJamCopy.SET_KEY,
        EventHandler(key = "${removalKey(jam.date, song.songId)}|setKey") { state.onSetKey(jam.date, song.songId) },
    )
    val removal = removalModel(song)
    val sendingMove = isMoveSending(song)
    val move = moveModel(row, song, total, removal)
    val extraEditor = state.extraEditor(jam.date, song)
    val shownExtras = extraEditor.extras
    val savingExtras = state.extraParticipantChanges.any {
        it.jamDate == jam.date && it.songId == song.songId && it.state == ExtraParticipantChange.State.Sending
    }
    val admin = SongRowAdminUiModel(
        setKey = setKey,
        removal = removal,
        move = move,
        lineup = state.lineupEditor(jam.date, song),
        saveStatus = if (listOf(
                pending != null,
                sendingMove,
                state.isSavingLineup(jam.date, song.songId),
                state.isSavingAssignment(jam.date, song.songId),
                state.slotClears.any {
                    it.jamDate == jam.date && it.songId == song.songId && it.state == SlotClear.State.Sending
                },
                savingExtras,
            ).any { it }
        ) {
            NextJamCopy.SAVING
        } else {
            null
        },
    )
    val lineup = state.lineupPanel(jam.date, song, shownExtras)
    return if (pending == null) {
        row.copy(
            instruments = song.lineup.toInstrumentChips(shownExtras),
            lineup = lineup,
            admin = admin.copy(extraParticipants = extraEditor),
        )
    } else {
        row.copy(
            key = pending.value,
            keyDescription = NextJamCopy.keyDescription(pending.value),
            instruments = song.lineup.toInstrumentChips(shownExtras),
            lineup = lineup,
            admin = admin.copy(extraParticipants = extraEditor),
        )
    }
}

/**
 * The removal part of [song]'s row: "Quitando…" while a removal of it is sending, the confirmation
 * while it is the confirming row, else the action. Handlers are keyed by (date, songId, step), so a
 * model for another song never keeps an earlier handler.
 */
private fun RowAdmin.removalModel(song: JamSong): RemovalUiModel {
    val key = removalKey(jam.date, song.songId)
    val sending = removal.removes.any {
        it.jamDate == jam.date && it.songId == song.songId && it.state == SetlistRemove.State.Sending
    }
    val handler: (String) -> EventHandler<RemovalUiModel.Event> = { step ->
        EventHandler(key = "$key|$step") { event -> removal.onEvent(jam.date, song.songId, event) }
    }
    return when {
        sending -> RemovalUiModel.Removing(NextJamCopy.REMOVING)

        removal.confirming == key -> RemovalUiModel.Confirming(
            prompt = NextJamCopy.removePrompt(song.title),
            details = removalDetails(song),
            confirmLabel = NextJamCopy.CONFIRM_REMOVE,
            cancelLabel = NextJamCopy.CANCEL,
            events = handler("confirming"),
        )

        else -> RemovalUiModel.Idle(NextJamCopy.REMOVE, handler("idle"))
    }
}

/** The confirmation's lines: assigned musicians (filled slots plus extras), then the published note. */
private fun RowAdmin.removalDetails(song: JamSong): List<String> {
    val assigned = song.lineup.slots.count { it.isFilled } + song.extraParticipants.size
    return listOfNotNull(
        if (assigned > 0) NextJamCopy.assignedMusicians(assigned) else null,
        if (jam.status == JamStatus.PUBLISHED) NextJamCopy.PUBLISHED_REMOVE_NOTE else null,
    )
}

private fun JamSong.toRow(
    position: Int,
    rowKey: String,
    isExpanded: Boolean,
    toggle: () -> Unit,
    openDetail: () -> Unit,
) = SongRowUiModel(
    position = position,
    positionLabel = position.toString().padStart(POSITION_DIGITS, '0'),
    rowKey = rowKey,
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
