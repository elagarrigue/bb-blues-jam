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
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam as DomainJam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.filter.InstrumentFilterChange
import com.bbbjam.core.ui.filter.instrumentFilterBar
import com.bbbjam.core.ui.filter.matchesInstrumentFilter
import com.bbbjam.core.ui.filter.updatedBy
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.PullRefreshUiModel
import com.bbbjam.core.ui.state.listError
import com.bbbjam.core.ui.state.stalenessNotice
import com.bbbjam.core.ui.strip.toInstrumentChips
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.admin.AdminState
import com.bbbjam.feature.nextjam.admin.NextJamSetlistWrites
import com.bbbjam.feature.nextjam.admin.RowAdmin
import com.bbbjam.feature.nextjam.admin.decorate
import com.bbbjam.feature.nextjam.admin.rememberMoveControls
import com.bbbjam.feature.nextjam.admin.rememberNextJamAdminState
import com.bbbjam.feature.nextjam.admin.toUiModel
import com.bbbjam.feature.nextjam.overlay.DisplayedJamSong
import com.bbbjam.feature.nextjam.overlay.SongOverlaySources
import com.bbbjam.feature.nextjam.overlay.SongOverlays
import com.bbbjam.feature.nextjam.overlay.displayEntries
import com.bbbjam.feature.nextjam.overlay.rememberSongOverlays
import com.bbbjam.feature.nextjam.refresh.rememberNextJamRefreshes
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
 * The instrument filter (`instrument-filter-chips`) is a set of instruments held the same way: created
 * once, never recreated, and not tied to the jam date, because it describes the musician, not the
 * jam. Handlers send an [InstrumentFilterChange] that is applied to the current set, never a set
 * computed from an earlier model.
 *
 * Opening a song's detail (`song-detail-screen`) is [Params.onOpenSong], a plain callback that
 * `:app` binds to navigation; this module never sees the navigation library (D-03). It is read
 * through `rememberUpdatedState`, so an earlier model's row handler calls the current callback.
 * Since `song-detail-replaces-row-expansion` rows never expand: a row tap opens the detail, which
 * holds the lineup panel and the admin's song-level controls; the list keeps the strip.
 *
 * Admin (`admin-add-song-to-setlist`, D-15): the flag comes from [adminSession]; it only decides
 * what is drawn, never what is allowed (Apps Script authorizes every write). For the admin the
 * list maps `jam.setlist`, draft songs included, and the model gains [NextJamAdminUiModel]: the
 * draft badge, the pending and failed adds of [writes] and "Agregar tema", which calls
 * [Params.onAddSong]. Musicians keep `setlistForMusicians()` and a model with no admin part. When
 * the flag turns on, the presenter asks for one refresh, which is the admin read (draft songs);
 * whether it already did is saveable, so returning to the tab does not repeat it.
 *
 * Removing a song (`admin-remove-song-from-setlist`, U1): which row is confirming is one saveable
 * key ("date|songId"), so the confirmation survives rotation and only one row confirms at a time.
 * Confirm acts only while that key is still the row's, clears it and launches
 * `removeSong` undispatched, so the `Sending` entry exists before the next frame
 * and a double tap removes once. The row shows "Quitando…" while a `Sending` removal exists for its
 * (date, songId); a failed one is a card, and the row is drawn as before.
 *
 * Song-level writes (`admin-set-key`, `admin-adjust-lineup`, `admin-assign-musician`,
 * `admin-clear-slot`, `admin-edit-extra-participants`) start in the song detail; the list draws
 * their [SongOverlays] (the same rule as the detail): while one is sending the row shows the pending
 * key and strip with "Guardando…"; a failed one never overlays, so the row reverts and a card says
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
internal class NextJamPresenter(
    private val jams: JamsRepository,
    private val calendar: JamCalendar,
    private val adminSession: AdminSession,
    private val writes: NextJamSetlistWrites,
    private val overlaySources: SongOverlaySources,
    private val random: Random = Random.Default,
) : Presenter<NextJamUiModel, NextJamPresenter.Params> {

    /**
     * [onOpenSong] receives the jam's date, the song id and the song's cached (pre-overlay) position
     * when a row is tapped; [onAddSong] the jam's date when the admin taps "Agregar tema".
     * [onOpenSong] stays last, so `Params { date, songId, position -> … }` still names it.
     * [isResumed] is true while the screen is visible (its lifecycle is at least `RESUMED`); only
     * then does the live window refresh run. False by default, so a presenter nobody marks visible
     * never polls.
     */
    data class Params(
        val isResumed: Boolean = false,
        val onAddSong: (jamDate: LocalDate) -> Unit = {},
        val onOpenSong: (jamDate: LocalDate, songId: SongId, position: Int) -> Unit = { _, _, _ -> },
    )

    @Composable
    override fun present(params: Params): NextJamUiModel {
        val currentOnOpenSong by rememberUpdatedState(params.onOpenSong)
        val onOpenSong: (LocalDate, SongId, Int) -> Unit = { date, songId, position ->
            currentOnOpenSong(date, songId, position)
        }
        val currentOnAddSong by rememberUpdatedState(params.onAddSong)
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)
        val adds by remember { writes.adds.observeAdds() }.collectAsState(initial = emptyList())
        val removes by remember { writes.removals.observeRemoves() }.collectAsState(initial = emptyList())
        val overlays = rememberSongOverlays(overlaySources)
        var subscription by remember { mutableIntStateOf(0) }
        val snapshot by remember(subscription) { jams.observeJams() }.collectAsState(initial = null)
        val moveControls = rememberMoveControls(writes.moves, snapshot)
        val admin = rememberNextJamAdminState(
            adminSession = adminSession,
            jams = jams,
            writes = writes,
            isAdmin = isAdmin,
            adds = adds,
            removes = removes,
            overlays = overlays,
            moves = moveControls.moves,
            onMove = moveControls.onMove,
            onAddSong = { date -> currentOnAddSong(date) },
        )
        val refreshes =
            rememberNextJamRefreshes(jams, calendar, random, params.isResumed, isAdmin, snapshot) { subscription++ }
        val today = remember(snapshot) { calendar.today() }
        val now = remember(snapshot) { calendar.now() }
        var filter by rememberSaveable(stateSaver = InstrumentFilterSaver) { mutableStateOf(emptySet<Instrument>()) }
        val onFilterChange: (InstrumentFilterChange) -> Unit = { change -> filter = filter.updatedBy(change) }
        val model = snapshot?.let { refreshes.shown(it) }
            ?.toUiModel(today, filter, onFilterChange, now, refreshes.onRetry, onOpenSong, admin)
            ?: NextJamUiModel.Loading(UiText.res(R.string.next_jam_loading))
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
 * screen owns. [filter] and [onFilterChange] come from the presenter's state
 * (`instrument-filter-chips`); the defaults draw every row unfiltered. [now] ages the cached data
 * for the staleness notice (by default the fetch time, so a test that does not care sees "hace
 * menos de un minuto"); [onRetry] is what the error block's and the notice's retry call.
 * [onOpenSong] is what a row tap calls.
 *
 * The notice is drawn exactly when something is cached and the latest refresh failed. Age alone
 * draws none: collecting the flow already refreshes stale data, and a notice on every open would
 * be noise.
 */
internal fun JamsSnapshot.toUiModel(
    today: LocalDate,
    filter: Set<Instrument> = emptySet(),
    onFilterChange: (InstrumentFilterChange) -> Unit = {},
    now: Instant = freshness.fetchedAt ?: Instant.EPOCH,
    onRetry: () -> Unit = {},
    onOpenSong: (LocalDate, SongId, Int) -> Unit = { _, _, _ -> },
    admin: AdminState? = null,
): NextJamUiModel {
    // A draft does not exist for a musician (D-26): musicians read the projection, the admin the raw view.
    val jam = (if (admin == null) forMusicians() else this).upcoming
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
            empty = EmptyStateUiModel(
                UiText.res(R.string.next_jam_no_upcoming_title),
                UiText.res(R.string.next_jam_no_upcoming_message),
            ),
            staleness = staleness,
            adminHint = if (admin == null) null else UiText.res(R.string.next_jam_admin_no_upcoming),
        )

        // Nothing was ever read: never claim there is no jam. An error only once a read has failed
        // and no retry is running; while one runs, the skeleton again.
        failure != null && !freshness.isRefreshing ->
            NextJamUiModel.Failed(listError(UiText.res(R.string.next_jam_load_failed), isOffline, onRetry))

        else -> NextJamUiModel.Loading(UiText.res(R.string.next_jam_loading))
    }
}

private fun DomainJam.toHeader(today: LocalDate) = JamHeaderUiModel(
    date = jamDateLabel(date, startTime),
    venue = venue,
    timeRemaining = timeRemaining(today, date, startTime),
)

/** The presenter's list state, passed down as one value. */
private class RowState(
    val filter: Set<Instrument>,
    val onFilterChange: (InstrumentFilterChange) -> Unit,
    val onOpenSong: (LocalDate, SongId, Int) -> Unit,
    val admin: RowAdmin?,
) {
    val isAdmin: Boolean
        get() = admin != null
}

/**
 * The bar counts every song; the rows are those matching the selection, so the count line's number is
 * the number of rows. A setlist with no song is the empty block, with no bar: that is the empty
 * case, not the filter's no-results case.
 */
private fun Setlist.toUiModel(date: LocalDate, state: RowState): SetlistUiModel = when (this) {
    is Setlist.Available -> if (songs.isEmpty()) {
        // No song means no dropped row either (domain invariant), so no note is lost. The admin's
        // empty list is a draft being built, not a published empty list.
        SetlistUiModel.Empty(
            if (state.isAdmin) {
                EmptyStateUiModel(
                    UiText.res(R.string.next_jam_admin_empty_title),
                    UiText.res(R.string.next_jam_admin_empty_message),
                )
            } else {
                EmptyStateUiModel(
                    UiText.res(R.string.next_jam_empty_title),
                    UiText.res(R.string.next_jam_empty_message),
                )
            },
        )
    } else {
        val overlaidSongs = songs.map { song ->
            val admin = state.admin?.state
            if (admin == null) {
                song
            } else {
                admin.overlays.overlaid(date, song)
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
                        openDetail = { state.onOpenSong(date, song.songId, entry.cachedPosition) },
                    ).let { row -> state.admin?.decorate(row, song, shownEntries.size) ?: row }
                },
            droppedRowsNote = if (droppedRows == 0) null else SongListText.droppedRows(droppedRows),
            filterBar = instrumentFilterBar(shownEntries.map { it.song.lineup }, state.filter, state.onFilterChange),
        )
    }

    Setlist.Withheld -> SetlistUiModel.Withheld(
        DraftSetlistUiModel(
            UiText.res(R.string.next_jam_draft_label),
            UiText.res(R.string.next_jam_draft_title),
            UiText.res(R.string.next_jam_draft_message),
        ),
    )

    // The problem is for logs and the admin, not for musicians.
    is Setlist.Unavailable -> SetlistUiModel.Unavailable(UiText.res(R.string.next_jam_setlist_unavailable))
}

private fun JamSong.toRow(position: Int, rowKey: String, openDetail: () -> Unit) = SongRowUiModel(
    position = position,
    positionLabel = SongListText.positionLabel(position),
    rowKey = rowKey,
    title = title,
    key = key.value,
    keyDescription = SongListText.keyDescription(key.value),
    instruments = lineup.toInstrumentChips(extraParticipants),
    openDetailLabel = UiText.res(R.string.next_jam_open_detail),
    events = EventHandler { event ->
        when (event) {
            SongRowUiModel.Event.OpenDetail -> openDetail()
        }
    },
)
