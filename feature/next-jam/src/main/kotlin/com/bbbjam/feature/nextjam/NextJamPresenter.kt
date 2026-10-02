package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam as DomainJam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.presenter.Presenter
import java.time.LocalDate

/**
 * Presents Próxima jam: the upcoming jam of [JamsRepository.observeJams], already cached, split in
 * Buenos Aires and title-resolved. Read-only; it never starts network work itself (collecting the
 * flow may start the repository's own background refresh).
 *
 * "Today" for the time remaining comes only from [calendar], read once per snapshot, so the header
 * agrees with the repository's upcoming/past split (P7), never from the device clock or UTC.
 */
class NextJamPresenter(private val jams: JamsRepository, private val calendar: JamCalendar) :
    Presenter<NextJamUiModel, Unit> {
    @Composable
    override fun present(params: Unit): NextJamUiModel {
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val today = remember(snapshot) { calendar.today() }
        return snapshot?.toUiModel(today) ?: NextJamUiModel.Loading
    }
}

/** Decision 3 of the spec: the only branches this slice owns. */
internal fun JamsSnapshot.toUiModel(today: LocalDate): NextJamUiModel {
    val jam = upcoming
    return when {
        jam != null -> NextJamUiModel.Jam(header = jam.toHeader(today), setlist = jam.setlist.toUiModel())

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

private fun Setlist.toUiModel(): SetlistUiModel = when (this) {
    is Setlist.Available -> SetlistUiModel.Songs(
        rows = songs.map { it.toRow() },
        droppedRowsNote = if (droppedRows == 0) null else NextJamCopy.droppedRows(droppedRows),
    )

    Setlist.Withheld -> SetlistUiModel.NotShown(NextJamCopy.SETLIST_WITHHELD)

    // The problem is for logs and the admin, not for musicians.
    is Setlist.Unavailable -> SetlistUiModel.NotShown(NextJamCopy.SETLIST_UNAVAILABLE)
}

private const val POSITION_DIGITS = 2

private fun JamSong.toRow() = SongRowUiModel(
    position = position,
    positionLabel = position.toString().padStart(POSITION_DIGITS, '0'),
    title = title,
    key = key.value,
    keyDescription = NextJamCopy.keyDescription(key.value),
)
