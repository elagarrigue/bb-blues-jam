package com.bbbjam.feature.nextjam.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.bbbjam.core.data.setlist.played.PlayedChange
import com.bbbjam.core.data.setlist.played.SetlistPlayedRepository
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.action.IconRowActionIcon
import com.bbbjam.core.ui.action.IconRowToggle
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.feature.nextjam.PlayedRowDefaults
import com.bbbjam.feature.nextjam.PlayedToggleUiModel
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.overlay.SongOverlays
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The "ya tocado" handler of the list and the song detail (`song-played-state`, D-13): a call sends one
 * `setSongPlayed(date, songId, played)`, launched undispatched so the `Sending` entry exists before the next
 * frame, and does nothing while a change of that song is still sending (one write per tap pair). Without a
 * [repository] (a model built from lists only) it does nothing.
 */
@Composable
internal fun rememberPlayedToggle(
    repository: SetlistPlayedRepository?,
    changes: List<PlayedChange>,
): (LocalDate, SongId, Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(changes)
    return remember(repository, scope) {
        { date, songId, played ->
            val sending = latest.any {
                it.jamDate == date && it.songId == songId && it.state == PlayedChange.State.Sending
            }
            if (repository != null && !sending) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.setSongPlayed(date, songId, played) }
            }
        }
    }
}

/** The "ya tocado" switch (`song-played-state`): a check icon, `text` when on and `textMuted` when off, never amber. */
@Composable
internal fun PlayedToggle(model: PlayedToggleUiModel, modifier: Modifier = Modifier) {
    IconRowToggle(
        icon = IconRowActionIcon.Played,
        checked = model.checked,
        contentDescription = model.description.resolve(),
        stateDescription = model.stateDescription.resolve(),
        checkedTint = PlayedRowDefaults.toggle(checked = true),
        uncheckedTint = PlayedRowDefaults.toggle(checked = false),
        modifier = modifier,
        enabled = model.enabled,
    ) {
        model.events(PlayedToggleUiModel.Event.Toggle)
    }
}

/**
 * The switch of [songId] on [date]: [checked] is the effective value, so a tap sends its opposite. Handlers
 * are keyed by (date, songId, step), as every other admin handler.
 */
internal fun SongOverlays.playedToggle(
    date: LocalDate,
    songId: SongId,
    checked: Boolean,
    onToggle: (LocalDate, SongId, Boolean) -> Unit,
): PlayedToggleUiModel = PlayedToggleUiModel(
    checked = checked,
    enabled = !isSavingPlayed(date, songId),
    description = UiText.res(if (checked) R.string.next_jam_unmark_played else R.string.next_jam_mark_played),
    stateDescription = UiText.res(if (checked) R.string.next_jam_state_played else R.string.next_jam_state_unplayed),
    events = EventHandler(key = "${removalKey(date, songId)}|played|$checked") { event ->
        when (event) {
            PlayedToggleUiModel.Event.Toggle -> onToggle(date, songId, !checked)
        }
    },
)
