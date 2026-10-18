package com.bbbjam.feature.nextjam

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.status.jamStatusBadge
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.theme.BluesJamTheme

private fun previewConfirming() = RemovalUiModel.Confirming(
    prompt = UiText.res(R.string.next_jam_remove_prompt, "Hoochie Coochie Man"),
    details = listOf(
        UiText.plural(R.plurals.next_jam_remove_assigned, 2),
        UiText.res(R.string.next_jam_remove_published_note),
    ),
    confirmLabel = UiText.res(R.string.next_jam_remove_ok),
    cancelLabel = UiText.res(R.string.next_jam_cancel),
    events = EventHandler {},
)

private fun previewMove(up: Boolean, down: Boolean) = MoveUiModel(
    up = MoveActionUiModel(
        UiText.res(R.string.next_jam_move_up),
        UiText.res(R.string.next_jam_move_up_description),
        up,
        EventHandler {},
    ),
    down = MoveActionUiModel(
        UiText.res(R.string.next_jam_move_down),
        UiText.res(R.string.next_jam_move_down_description),
        down,
        EventHandler {},
    ),
)

/**
 * The admin's compact bar (`song-detail-replaces-row-expansion`, P3) and the removal steps
 * (`admin-remove-song-from-setlist`): the bar, the confirmation replacing it, and "Quitando…".
 */
@Preview
@Composable
private fun NextJamRemovalPreview() {
    val filled =
        Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.BASS, "Nico"), Slot(Instrument.DRUMS)))
    val confirming = previewConfirming()
    fun move(up: Boolean, down: Boolean) = previewMove(up, down)
    val rows = listOf(
        previewRow(1, "Crossroads", "A", Lineup.default()).copy(
            admin = SongRowAdminUiModel(
                RemovalUiModel.Idle(
                    UiText.res(R.string.next_jam_remove),
                    EventHandler {
                    },
                ),
                move(false, true),
            ),
        ),
        previewRow(2, "Hoochie Coochie Man", "A", filled)
            .copy(
                admin = SongRowAdminUiModel(
                    confirming,
                    move(true, true),
                    saveStatus = UiText.res(R.string.next_jam_saving),
                ),
            ),
        previewRow(3, "The Thrill Is Gone", "Bm", Lineup.default())
            .copy(admin = SongRowAdminUiModel(RemovalUiModel.Removing(UiText.res(R.string.next_jam_removing)))),
    )
    val model = NextJamUiModel.Jam(
        header = JamHeaderUiModel(
            date = jamDateLabel(PREVIEW_DATE, PREVIEW_START),
            venue = "La Macanuda",
            timeRemaining = timeRemaining(PREVIEW_TODAY, PREVIEW_DATE, PREVIEW_START),
        ),
        setlist = SetlistUiModel.Songs(rows = rows, droppedRowsNote = null, filterBar = null),
        staleness = null,
        admin = NextJamAdminUiModel(
            status = AdminStatusUiModel(
                badge = jamStatusBadge(JamStatus.DRAFT),
                note = UiText.res(R.string.next_jam_note_draft),
                hint = UiText.res(R.string.next_jam_status_hint),
            ),
            pending = emptyList(),
            failures = listOf(
                AddFailureUiModel(
                    id = 4,
                    title = UiText.res(R.string.next_jam_remove_failed, "Pride and Joy"),
                    message = UiText.res(CoreR.string.write_failure_access_refused),
                    dismissLabel = UiText.res(R.string.next_jam_close),
                    events = EventHandler {},
                ),
            ),
            addSong = AddSongActionUiModel(UiText.res(R.string.next_jam_add_song), EventHandler {}),
        ),
    )
    BluesJamTheme { NextJamContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}
