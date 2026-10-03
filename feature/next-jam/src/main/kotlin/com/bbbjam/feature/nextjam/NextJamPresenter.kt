package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam as DomainJam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Setlist
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
 */
class NextJamPresenter(private val jams: JamsRepository, private val calendar: JamCalendar) :
    Presenter<NextJamUiModel, Unit> {
    @Composable
    override fun present(params: Unit): NextJamUiModel {
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val today = remember(snapshot) { calendar.today() }
        var expanded by rememberSaveable(stateSaver = ExpandedRows.Saver) { mutableStateOf(ExpandedRows.NONE) }
        val onToggle: (LocalDate, Int) -> Unit = { date, position -> expanded = expanded.toggle(date, position) }
        return snapshot?.toUiModel(today, expanded, onToggle) ?: NextJamUiModel.Loading
    }
}

/**
 * Decision 3 of `next-jam-read-only-list`: the only branches this screen owns. [expanded] and
 * [onToggle] come from the presenter's state (`song-row-expansion`); the defaults draw every row
 * collapsed.
 */
internal fun JamsSnapshot.toUiModel(
    today: LocalDate,
    expanded: ExpandedRows = ExpandedRows.NONE,
    onToggle: (LocalDate, Int) -> Unit = { _, _ -> },
): NextJamUiModel {
    val jam = upcoming
    return when {
        jam != null -> NextJamUiModel.Jam(
            header = jam.toHeader(today),
            setlist = jam.setlist.toUiModel(jam.date, expanded, onToggle),
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

private fun Setlist.toUiModel(
    date: LocalDate,
    expanded: ExpandedRows,
    onToggle: (LocalDate, Int) -> Unit,
): SetlistUiModel = when (this) {
    is Setlist.Available -> SetlistUiModel.Songs(
        rows = songs.map { song ->
            song.toRow(isExpanded = expanded.isExpanded(date, song.position)) { onToggle(date, song.position) }
        },
        droppedRowsNote = if (droppedRows == 0) null else NextJamCopy.droppedRows(droppedRows),
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
