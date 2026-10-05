package com.bbbjam.feature.pastjams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import java.time.LocalDate

/**
 * Presents one past jam, identified by its date (`past-jam-detail`): the header and the song list,
 * nothing about who played. It collects [JamsRepository.observeJams] (cache-first; collecting may
 * start the repository's own background refresh) and looks the jam up on every snapshot, so a
 * refresh that withholds or removes it changes the screen live. Read only; its only dependency is
 * the jams repository, so it cannot read the admin flag.
 *
 * [Params.onBack] is read through `rememberUpdatedState`, so an earlier model's back handler calls
 * the current callback.
 */
class PastJamDetailPresenter(private val jams: JamsRepository) :
    Presenter<PastJamDetailUiModel, PastJamDetailPresenter.Params> {

    /** [jamDate] names the past jam; [onBack] leaves the screen (bound by `:app`). */
    data class Params(val jamDate: LocalDate, val onBack: () -> Unit)

    @Composable
    override fun present(params: Params): PastJamDetailUiModel {
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val currentOnBack by rememberUpdatedState(params.onBack)
        val onBack: () -> Unit = { currentOnBack() }
        return snapshot?.toPastJamDetail(params.jamDate, onBack)
            ?: PastJamDetailUiModel.Loading(PastJamsCopy.DETAIL_LOADING, backUiModel(onBack))
    }
}

/**
 * The state table of `docs/specs/past-jam-detail.md` (Technical Approach 2). The jam is looked up
 * by date among the past jams only, never the upcoming one, and its songs are read through
 * `setlistForMusicians()`, so a draft is never shown. `lineup` and `extraParticipants` are never read.
 */
internal fun JamsSnapshot.toPastJamDetail(jamDate: LocalDate, onBack: () -> Unit = {}): PastJamDetailUiModel {
    val back = backUiModel(onBack)
    val jam = past.firstOrNull { it.date == jamDate }
        ?: return PastJamDetailUiModel.NotFound(
            EmptyStateUiModel(PastJamsCopy.NOT_FOUND_TITLE, PastJamsCopy.NOT_FOUND_MESSAGE),
            back,
        )
    val setlist = jam.setlistForMusicians().toPastSetlist()
    val countLabel = (setlist as? PastSetlistUiModel.Songs)?.let { PastJamsCopy.songCount(it.rows.size) }
    return PastJamDetailUiModel.Jam(
        header = PastJamHeaderUiModel(pastJamDateLabel(jam.date), jam.venue, countLabel),
        setlist = setlist,
        back = back,
    )
}

private fun Setlist.toPastSetlist(): PastSetlistUiModel = when (this) {
    is Setlist.Available -> if (songs.isEmpty()) {
        PastSetlistUiModel.NotShown(PastJamsCopy.EMPTY_SETLIST)
    } else {
        PastSetlistUiModel.Songs(
            // Songs are already in position order (a Setlist invariant).
            rows = songs.map { it.toPastRow() },
            droppedRowsNote = droppedRows.takeIf { it > 0 }?.let(PastJamsCopy::droppedRows),
        )
    }

    Setlist.Withheld -> PastSetlistUiModel.NotShown(PastJamsCopy.SETLIST_NOT_PUBLISHED)

    is Setlist.Unavailable -> PastSetlistUiModel.NotShown(PastJamsCopy.SETLIST_UNAVAILABLE)
}

private fun JamSong.toPastRow() = PastSongRowUiModel(
    position = position,
    positionLabel = position.toString().padStart(POSITION_DIGITS, '0'),
    title = title,
    artist = artist.takeIf { it.isNotBlank() },
    key = key.value,
    keyDescription = PastJamsCopy.keyDescription(key.value),
)

/** "01" … "13": positions are padded with `padStart`, never `String.format` (locale-sensitive). */
private const val POSITION_DIGITS = 2
