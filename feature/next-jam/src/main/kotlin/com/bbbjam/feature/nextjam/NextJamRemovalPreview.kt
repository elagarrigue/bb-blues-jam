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

/** The admin's removal steps (`admin-remove-song-from-setlist`): confirming, sending, and the action. */
@Preview
@Composable
private fun NextJamRemovalPreview() {
    val filled =
        Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.BASS, "Nico"), Slot(Instrument.DRUMS)))
    val confirming = RemovalUiModel.Confirming(
        prompt = NextJamCopy.removePrompt("Hoochie Coochie Man"),
        details = listOf(NextJamCopy.assignedMusicians(2), NextJamCopy.PUBLISHED_REMOVE_NOTE),
        confirmLabel = NextJamCopy.CONFIRM_REMOVE,
        cancelLabel = NextJamCopy.CANCEL,
        events = EventHandler {},
    )
    val rows = listOf(
        previewRow(1, "Crossroads", "Cream", "A", Lineup.default(), isExpanded = true)
            .copy(admin = SongRowAdminUiModel(RemovalUiModel.Idle(NextJamCopy.REMOVE, EventHandler {}))),
        previewRow(2, "Hoochie Coochie Man", "Muddy Waters", "A", filled, isExpanded = true)
            .copy(admin = SongRowAdminUiModel(confirming)),
        previewRow(3, "The Thrill Is Gone", "B.B. King", "Bm", Lineup.default(), isExpanded = true)
            .copy(admin = SongRowAdminUiModel(RemovalUiModel.Removing(NextJamCopy.REMOVING))),
    )
    val model = NextJamUiModel.Jam(
        header = JamHeaderUiModel("Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días"),
        setlist = SetlistUiModel.Songs(rows = rows, droppedRowsNote = null, filterBar = null),
        staleness = null,
        admin = NextJamAdminUiModel(
            draftBadge = null,
            draftNote = null,
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
