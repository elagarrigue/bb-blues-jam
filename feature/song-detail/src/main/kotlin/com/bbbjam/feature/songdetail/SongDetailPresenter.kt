package com.bbbjam.feature.songdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.lineup.toInstrumentGroups
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import java.time.LocalDate

/**
 * Presents one jam song, identified by its jam's date and its `posicion` (the Sheet identity; the
 * key and the lineup belong to the jam song, not the catalog song, D-08). It collects
 * [JamsRepository.observeJams] (cache-first; collecting may start the repository's own background
 * refresh) and looks the song up on every snapshot, so a refresh that drops the row turns the
 * detail into the not-found block live. Read only; it never reads the catalog.
 *
 * [Params.onBack] is read through `rememberUpdatedState`, so an earlier model's back handler calls
 * the current callback.
 */
class SongDetailPresenter(private val jams: JamsRepository) :
    Presenter<SongDetailUiModel, SongDetailPresenter.Params> {

    /** [jamDate] and [position] name the song; [onBack] leaves the screen (bound by `:app`). */
    data class Params(val jamDate: LocalDate, val position: Int, val onBack: () -> Unit)

    @Composable
    override fun present(params: Params): SongDetailUiModel {
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val currentOnBack by rememberUpdatedState(params.onBack)
        val onBack: () -> Unit = { currentOnBack() }
        return snapshot?.toSongDetail(params.jamDate, params.position, onBack)
            ?: SongDetailUiModel.Loading(SongDetailCopy.LOADING, backUiModel(onBack))
    }
}

/**
 * Decision 2 of `song-detail-screen`: the jam is looked up by date among the upcoming and the past
 * jams, then the song by position in an available setlist. Anything else (no such jam or position,
 * a draft jam or a withheld or unavailable setlist, an empty snapshot after a failed cache read) is
 * not found.
 */
internal fun JamsSnapshot.toSongDetail(jamDate: LocalDate, position: Int, onBack: () -> Unit): SongDetailUiModel {
    val back = backUiModel(onBack)
    val jam = (listOfNotNull(upcoming) + past).firstOrNull { it.date == jamDate }
    // A draft's songs are never shown to a musician, even when they reached the app.
    val song = (jam?.setlistForMusicians() as? Setlist.Available)?.songs?.firstOrNull { it.position == position }
        ?: return SongDetailUiModel.NotFound(
            EmptyStateUiModel(SongDetailCopy.NOT_FOUND_TITLE, SongDetailCopy.NOT_FOUND_MESSAGE),
            back,
        )
    return SongDetailUiModel.Song(
        title = song.title,
        artist = song.artist.takeIf { it.isNotBlank() },
        keyLabel = SongDetailCopy.KEY_LABEL,
        key = song.key.value,
        keyDescription = SongDetailCopy.keyDescription(song.key.value),
        lineup = song.lineup.toInstrumentGroups(song.extraParticipants),
        back = back,
    )
}
