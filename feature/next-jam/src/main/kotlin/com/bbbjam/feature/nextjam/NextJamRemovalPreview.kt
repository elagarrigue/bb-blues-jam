package com.bbbjam.feature.nextjam

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The admin's compact bar (`song-detail-replaces-row-expansion`, P3) and the removal steps
 * (`admin-remove-song-from-setlist`): the bar, the confirmation replacing it, and "Quitando…".
 */
@Preview
@Composable
private fun NextJamRemovalPreview() {
    val filled =
        Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.BASS, "Nico"), Slot(Instrument.DRUMS)))
    val confirming = RemovalUiModel.Confirming(
        prompt = NextJamCopy.removePrompt("Hoochie Coochie Man"),
        details = listOf(NextJamCopy.assignedMusicians(2), NextJamCopy.PUBLISHED_REMOVE_NOTE),
        confirmLabel = NextJamCopy.OK,
        cancelLabel = NextJamCopy.CANCEL,
        events = EventHandler {},
    )
    fun move(up: Boolean, down: Boolean) = MoveUiModel(
        up = MoveActionUiModel(NextJamCopy.MOVE_UP, "subir un lugar", up, EventHandler {}),
        down = MoveActionUiModel(NextJamCopy.MOVE_DOWN, "bajar un lugar", down, EventHandler {}),
    )
    val rows = listOf(
        previewRow(1, "Crossroads", "A", Lineup.default()).copy(
            admin = SongRowAdminUiModel(RemovalUiModel.Idle(NextJamCopy.REMOVE, EventHandler {}), move(false, true)),
        ),
        previewRow(2, "Hoochie Coochie Man", "A", filled)
            .copy(admin = SongRowAdminUiModel(confirming, move(true, true), saveStatus = NextJamCopy.SAVING)),
        previewRow(3, "The Thrill Is Gone", "Bm", Lineup.default())
            .copy(admin = SongRowAdminUiModel(RemovalUiModel.Removing(NextJamCopy.REMOVING))),
    )
    val model = NextJamUiModel.Jam(
        header = JamHeaderUiModel("Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días"),
        setlist = SetlistUiModel.Songs(rows = rows, droppedRowsNote = null, filterBar = null),
        staleness = null,
        admin = NextJamAdminUiModel(
            status = AdminStatusUiModel(
                badge = NextJamCopy.DRAFT_BADGE,
                isPublished = false,
                note = NextJamCopy.DRAFT_NOTE,
                publish = null,
            ),
            pending = emptyList(),
            failures = listOf(
                AddFailureUiModel(
                    id = 4,
                    title = NextJamCopy.removeFailed("Pride and Joy"),
                    message = NextJamCopy.ACCESS_REFUSED,
                    dismissLabel = NextJamCopy.CLOSE,
                    events = EventHandler {},
                ),
            ),
            addSong = AddSongActionUiModel(NextJamCopy.ADD_SONG, EventHandler {}),
        ),
    )
    BluesJamTheme { NextJamContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}
