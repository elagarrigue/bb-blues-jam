package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Presents the key picker (`admin-set-key`, V1, O1). It finds the song by id in the upcoming jam's
 * setlist (the admin's view, drafts included), only while the admin flag is on, and shows its
 * current key: the latest pending change when one is still saving, so the picker agrees with the
 * row, else the cached key. Picking a key calls [SetlistRepository.setKey] undispatched and then
 * [Params.onDone] at once: the write runs in the data layer, and Próxima jam shows the new key with
 * "Guardando…" until the server answers. Only the first pick counts, and the current key's cell
 * does nothing, so nothing is sent for the same key.
 *
 * Reached only from the admin's "Cambiar tonalidad"; it authorizes nothing (Apps Script does).
 */
class SetKeyPresenter(
    private val jams: JamsRepository,
    private val adminSession: AdminSession,
    private val setlist: SetlistRepository,
) : Presenter<SetKeyUiModel, SetKeyPresenter.Params> {

    /** [jamDate] and [songId] name the song; [onBack] leaves without a change; [onDone] closes after a pick. */
    data class Params(val jamDate: LocalDate, val songId: SongId, val onBack: () -> Unit, val onDone: () -> Unit)

    @Composable
    override fun present(params: Params): SetKeyUiModel {
        val currentJamDate by rememberUpdatedState(params.jamDate)
        val currentSongId by rememberUpdatedState(params.songId)
        val currentOnBack by rememberUpdatedState(params.onBack)
        val currentOnDone by rememberUpdatedState(params.onDone)
        val scope = rememberCoroutineScope()
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = null)
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val keyChanges by remember { setlist.observeKeyChanges() }.collectAsState(initial = emptyList())
        var picked by remember { mutableStateOf(false) }
        val onPick: (Key) -> Unit = { key ->
            if (!picked) {
                picked = true
                // Undispatched: the change is handed to the data layer before this handler returns.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    setlist.setKey(currentJamDate, currentSongId, key)
                }
                currentOnDone()
            }
        }
        return setKeyModel(
            isAdmin = isAdmin,
            snapshot = snapshot,
            keyChanges = keyChanges,
            jamDate = params.jamDate,
            songId = params.songId,
            back = backUiModel { currentOnBack() },
            onPick = onPick,
        )
    }
}

/**
 * The picker for ([jamDate], [songId]): loading until the flag and the jams are read, gone when the
 * flag is off or the upcoming jam's readable setlist has no such song, else the content.
 */
internal fun setKeyModel(
    isAdmin: Boolean?,
    snapshot: JamsSnapshot?,
    keyChanges: List<KeyChange>,
    jamDate: LocalDate,
    songId: SongId,
    back: BackUiModel = backUiModel {},
    onPick: (Key) -> Unit = {},
): SetKeyUiModel {
    val jam = snapshot?.upcoming?.takeIf { isAdmin == true && it.date == jamDate }
    val song = (jam?.setlist as? Setlist.Available)?.songs?.firstOrNull { it.songId == songId }
    return when {
        isAdmin == null || snapshot == null -> SetKeyUiModel.Loading(back)
        song == null -> SetKeyUiModel.Gone(back, EmptyStateUiModel(SetKeyCopy.GONE_TITLE, SetKeyCopy.GONE_MESSAGE))
        else -> pickerContent(song, (keyChanges.pendingKey(jamDate, songId) ?: song.key).value, back, onPick)
    }
}

/** The picker's content for [song], whose current key is [current] (the pending one, if any). */
private fun pickerContent(song: JamSong, current: String, back: BackUiModel, onPick: (Key) -> Unit) =
    SetKeyUiModel.Content(
        back = back,
        title = SetKeyCopy.TITLE,
        songTitle = song.title,
        currentLabel = SetKeyCopy.CURRENT,
        currentKey = current,
        currentKeyDescription = NextJamCopy.keyDescription(current),
        sections = listOf(
            keySection(SetKeyCopy.MAJORS, SetKeyDefaults.MAJORS, current, onPick),
            keySection(SetKeyCopy.MINORS, SetKeyDefaults.MINORS, current, onPick),
        ),
    )

/** A section of [keys] as rows of [SetKeyDefaults.COLUMNS]; only the cell spelled exactly [current] is current. */
internal fun keySection(label: String, keys: List<Key>, current: String, onPick: (Key) -> Unit) = KeySectionUiModel(
    label = label,
    rows = keys.map { key ->
        val isCurrent = key.value == current
        KeyCellUiModel(
            key = key.value,
            description = NextJamCopy.keyDescription(key.value),
            isCurrent = isCurrent,
            currentLabel = if (isCurrent) SetKeyCopy.CURRENT_CELL else null,
            pickLabel = SetKeyCopy.PICK_LABEL,
            events = EventHandler(key = "${key.value}|$isCurrent") { event ->
                when (event) {
                    KeyCellUiModel.Event.Pick -> if (!isCurrent) onPick(key)
                }
            },
        )
    }.chunked(SetKeyDefaults.COLUMNS),
)
