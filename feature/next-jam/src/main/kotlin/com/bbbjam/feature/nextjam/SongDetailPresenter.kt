package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.core.ui.lineup.toLineupPanel
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.SongListText
import java.time.LocalDate

/**
 * Presents one jam song, identified by its jam's date and its song id (P2 of
 * `song-detail-replaces-row-expansion`; before, by `posicion`). The key and the lineup belong to
 * the jam song, not the catalog song (D-08). It collects [JamsRepository.observeJams]
 * (cache-first; collecting may start the repository's own background refresh) and looks the song
 * up on every snapshot, so a refresh that drops the row turns the detail into the not-found block
 * live. It never reads the catalog and never refreshes on its own (the list owns the per-login
 * admin refresh).
 *
 * Admin (`song-detail-replaces-row-expansion`, D-15): the flag comes from [adminSession] and only
 * decides what is drawn; Apps Script authorizes every write. The flag is read with no initial value,
 * so the detail stays [SongDetailUiModel.Loading] until both the flag and the jams are read: an
 * admin opening a draft's song never sees a not-found frame. For the admin the song is looked up in
 * the upcoming jam's `jam.setlist` (drafts included) and gains [SongDetailAdminUiModel]: the
 * [SongOverlays] of [setlist] (the same rule the list applies), the inline lineup editor, the
 * "Otros" form, "Anotar"/"Liberar"/"Quitar" on the panel lines and the song's failure cards.
 * Tapping the key calls [Params.onSetKey] and "Anotar" calls [Params.onAssignSlot] (navigation, bound
 * by `:app`); every write is an existing [SetlistRepository] function (D-13).
 *
 * Every callback is read through `rememberUpdatedState`, so an earlier model's handler calls the
 * current one.
 */
class SongDetailPresenter(
    private val jams: JamsRepository,
    private val adminSession: AdminSession,
    private val setlist: SetlistRepository,
) : Presenter<SongDetailUiModel, SongDetailPresenter.Params> {

    /**
     * [jamDate] and [songId] name the song ([position] only picks between rows a hand-edited tab
     * gives the same id, see [findSong]); [onBack] leaves the screen. [onSetKey] opens the key
     * picker from the admin's key block, and [onAssignSlot] opens the assign screen for an open slot.
     * Both are bound by `:app`.
     */
    data class Params(
        val jamDate: LocalDate,
        val songId: SongId,
        val position: Int,
        val onBack: () -> Unit,
        val onSetKey: (jamDate: LocalDate, songId: SongId) -> Unit = { _, _ -> },
        val onAssignSlot: (
            jamDate: LocalDate,
            songId: SongId,
            instrument: Instrument,
            position: SlotPosition,
        ) -> Unit = { _, _, _, _ -> },
    )

    @Composable
    override fun present(params: Params): SongDetailUiModel {
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = null)
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val currentOnBack by rememberUpdatedState(params.onBack)
        val currentOnSetKey by rememberUpdatedState(params.onSetKey)
        val currentOnAssignSlot by rememberUpdatedState(params.onAssignSlot)
        val onBack: () -> Unit = { currentOnBack() }
        val overlays = rememberSongOverlays(setlist)
        val admin = rememberSongAdminState(
            setlist = setlist,
            snapshot = snapshot,
            isAdmin = isAdmin == true,
            overlays = overlays,
            onSetKey = { date, songId -> currentOnSetKey(date, songId) },
            onAssignSlot = { date, songId, instrument, position ->
                currentOnAssignSlot(date, songId, instrument, position)
            },
        )
        val shown = snapshot
        return if (shown == null || isAdmin == null) {
            SongDetailUiModel.Loading(SongDetailCopy.LOADING, backUiModel(onBack))
        } else {
            shown.toSongDetail(params.jamDate, params.songId, params.position, admin, onBack)
        }
    }
}

/**
 * Decision 2 of `song-detail-screen`: the jam is looked up by date among the upcoming and the past
 * jams, then the song by id in an available setlist ([findSong]). A musician looks it up in the
 * [forMusicians] projection (D-26, S1), so the song of a PUBLISHED jam behind a BORRADOR upcoming
 * jam is found, as Próxima jam shows it, and a BORRADOR jam's song, past or future, is not. Anything
 * else (no such jam or song, a draft jam for a musician, a withheld or unavailable setlist, an empty
 * snapshot after a failed cache read) is not found. With [admin], a song of the upcoming jam is looked up in
 * `jam.setlist` (drafts included) and carries the admin's controls; any other song (a past jam's)
 * is the musician's read-only detail.
 */
internal fun JamsSnapshot.toSongDetail(
    jamDate: LocalDate,
    songId: SongId,
    position: Int,
    admin: SongAdminState? = null,
    onBack: () -> Unit,
): SongDetailUiModel {
    val back = backUiModel(onBack)
    val adminSong = admin?.let {
        val jam = upcoming?.takeIf { it.date == jamDate }
        (jam?.setlist as? Setlist.Available)?.songs?.findSong(songId, position)
    }
    val visible = forMusicians()
    val jam = (listOfNotNull(visible.upcoming) + visible.past).firstOrNull { it.date == jamDate }
    // A draft's songs are never shown to a musician, even when they reached the app.
    val song = (jam?.setlistForMusicians() as? Setlist.Available)?.songs?.findSong(songId, position)
    return when {
        admin != null && adminSong != null -> admin.songDetail(jamDate, adminSong, back)

        song != null -> song.toSongModel(song.key.value, song.lineup.toLineupPanel(song.extraParticipants), back)

        else -> SongDetailUiModel.NotFound(
            EmptyStateUiModel(SongDetailCopy.NOT_FOUND_TITLE, SongDetailCopy.NOT_FOUND_MESSAGE),
            back,
        )
    }
}

private fun JamSong.toSongModel(key: String, lineup: LineupPanelUiModel, back: BackUiModel) = SongDetailUiModel.Song(
    title = title,
    artist = artist.takeIf { it.isNotBlank() },
    keyLabel = SongDetailCopy.KEY_LABEL,
    key = key,
    keyDescription = SongListText.keyDescription(key),
    lineup = lineup,
    back = back,
)

/**
 * The admin's detail of [cached]: drawn as the list draws its row (the same [SongOverlays], plus the
 * detail's own `+`/`−` reservations), with the song-level controls. Handlers are keyed by (date,
 * songId, step), as the list's.
 */
private fun SongAdminState.songDetail(date: LocalDate, cached: JamSong, back: BackUiModel): SongDetailUiModel.Song {
    val id = cached.songId
    val song = overlays.overlaid(date, cached) { lineup -> reservations.overlay(date, id, lineup) }
    val key = overlays.pendingKey(date, id)?.value ?: cached.key.value
    val saving = overlays.isSaving(date, id) || reservations.has(date, id)
    return song.toSongModel(key, lineupPanel(date, song, song.extraParticipants), back).copy(
        admin = SongDetailAdminUiModel(
            saveStatus = if (saving) NextJamCopy.SAVING else null,
            setKey = SetKeyActionUiModel(
                NextJamCopy.SET_KEY,
                EventHandler(key = "${removalKey(date, id)}|setKey") { onSetKey(date, id) },
            ),
            lineup = lineupEditor(date, song),
            extraParticipants = extraEditor(date, song),
            failures = failureCards(overlays.failures(date, id), onDismiss),
        ),
    )
}

/**
 * P2 of `song-detail-replaces-row-expansion`, the same rule as the mutations' R1: the one song with
 * [songId], wherever it now is, so a move or a removal renumbering the list never switches the
 * detail to another song; when a hand-edited tab repeats the id, the one at [position]; none, null.
 */
internal fun List<JamSong>.findSong(songId: SongId, position: Int): JamSong? {
    val matches = filter { it.songId == songId }
    return matches.singleOrNull() ?: matches.firstOrNull { it.position == position }
}
