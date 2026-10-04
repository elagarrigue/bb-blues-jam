package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.bbbjam.core.ui.strip.toInstrumentChips
import java.time.LocalDate

/**
 * Presents Próxima jam: the upcoming jam of [JamsRepository.observeJams], already cached, split in
 * Buenos Aires and title-resolved. Read-only; it never starts network work itself (collecting the
 * flow may start the repository's own background refresh).
 *
 * "Today" for the time remaining comes only from [calendar], read once per snapshot, so the header
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
 */
class NextJamPresenter(private val jams: JamsRepository, private val calendar: JamCalendar) :
    Presenter<NextJamUiModel, Unit> {
    @Composable
    override fun present(params: Unit): NextJamUiModel {
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val today = remember(snapshot) { calendar.today() }
        var expanded by rememberSaveable(stateSaver = ExpandedRows.Saver) { mutableStateOf(ExpandedRows.NONE) }
        val onToggle: (LocalDate, Int) -> Unit = { date, position -> expanded = expanded.toggle(date, position) }
        var filter by rememberSaveable(stateSaver = InstrumentFilterSaver) { mutableStateOf(emptySet<Instrument>()) }
        val onFilterChange: (InstrumentFilterChange) -> Unit = { change -> filter = filter.updatedBy(change) }
        return snapshot?.toUiModel(today, expanded, onToggle, filter, onFilterChange) ?: NextJamUiModel.Loading
    }
}

/** The selected instruments as their enum names: bundle-safe, so the filter survives rotation. */
internal val InstrumentFilterSaver: Saver<Set<Instrument>, Any> = listSaver(
    save = { selected -> selected.map { it.name } },
    restore = { names -> names.map { Instrument.valueOf(it) }.toSet() },
)

/**
 * Decision 3 of `next-jam-read-only-list`: the only branches this screen owns. [expanded] and
 * [onToggle] come from the presenter's state (`song-row-expansion`), [filter] and [onFilterChange]
 * too (`instrument-filter-chips`); the defaults draw every row collapsed and unfiltered.
 */
internal fun JamsSnapshot.toUiModel(
    today: LocalDate,
    expanded: ExpandedRows = ExpandedRows.NONE,
    onToggle: (LocalDate, Int) -> Unit = { _, _ -> },
    filter: Set<Instrument> = emptySet(),
    onFilterChange: (InstrumentFilterChange) -> Unit = {},
): NextJamUiModel {
    val jam = upcoming
    return when {
        jam != null -> NextJamUiModel.Jam(
            header = jam.toHeader(today),
            setlist = jam.setlist.toUiModel(jam.date, RowState(expanded, onToggle, filter, onFilterChange)),
        )

        // Nothing was ever read: the app must not claim there is no jam (list-states owns this case).
        freshness.fetchedAt == null -> NextJamUiModel.Loading

        else -> NextJamUiModel.NoUpcomingJam(NextJamCopy.NO_UPCOMING_JAM)
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
)

/**
 * Rows the filter hides keep their expansion (keyed by position) and come back as they were. The
 * bar counts every song; the rows are those matching the selection, so the count line's number is
 * the number of rows. No bar for a setlist with no song: that is the empty case, not no-results.
 */
private fun Setlist.toUiModel(date: LocalDate, state: RowState): SetlistUiModel = when (this) {
    is Setlist.Available -> SetlistUiModel.Songs(
        rows = songs
            .filter { it.lineup.matchesInstrumentFilter(state.filter) }
            .map { song ->
                song.toRow(isExpanded = state.expanded.isExpanded(date, song.position)) {
                    state.onToggle(date, song.position)
                }
            },
        droppedRowsNote = if (droppedRows == 0) null else NextJamCopy.droppedRows(droppedRows),
        filterBar = if (songs.isEmpty()) {
            null
        } else {
            instrumentFilterBar(songs.map { it.lineup }, state.filter, state.onFilterChange)
        },
    )

    Setlist.Withheld -> SetlistUiModel.NotShown(NextJamCopy.SETLIST_WITHHELD)

    // The problem is for logs and the admin, not for musicians.
    is Setlist.Unavailable -> SetlistUiModel.NotShown(NextJamCopy.SETLIST_UNAVAILABLE)
}

private const val POSITION_DIGITS = 2

private fun JamSong.toRow(isExpanded: Boolean, toggle: () -> Unit) = SongRowUiModel(
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
    events = EventHandler { event ->
        when (event) {
            SongRowUiModel.Event.ToggleExpanded -> toggle()
        }
    },
)
