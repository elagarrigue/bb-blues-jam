package com.bbbjam.feature.catalog

import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import java.time.LocalDate

/**
 * The dates of the future jams (the upcoming one and every held-back one) whose readable setlist has
 * [songId], oldest first, from the raw snapshot (drafts included). A future setlist that is not
 * `Available` cannot be checked here, so it is not counted: the server decides (C6).
 */
internal fun futureUses(songId: SongId, snapshot: JamsSnapshot): List<LocalDate> =
    (listOfNotNull(snapshot.upcoming) + snapshot.heldBack)
        .filter { jam -> (jam.setlist as? Setlist.Available)?.songs?.any { it.songId == songId } == true }
        .map { it.date }
        .sorted()

/**
 * The delete dialog for [song]: the refusal naming the jams when [uses] is not empty (nothing is sent), the
 * confirmation otherwise. [onConfirm] and [onClose] are the presenter's.
 */
internal fun deleteDialog(
    song: Song,
    uses: List<LocalDate>,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
): DeleteSongUiModel = if (uses.isEmpty()) {
    DeleteSongUiModel.Confirming(
        songId = song.id.value,
        title = UiText.res(R.string.catalog_delete_title, song.title),
        lines = listOf(
            UiText.raw(song.artist),
            UiText.res(R.string.catalog_delete_past_note),
            UiText.res(R.string.catalog_delete_undo),
        ),
        confirmLabel = UiText.res(R.string.catalog_delete_confirm),
        cancelLabel = UiText.res(CoreR.string.jam_form_cancel),
        events = EventHandler(key = song.id.value) { event ->
            when (event) {
                DeleteSongUiModel.Confirming.Event.Confirm -> onConfirm()
                DeleteSongUiModel.Confirming.Event.Cancel -> onClose()
            }
        },
    )
} else {
    DeleteSongUiModel.Refused(
        songId = song.id.value,
        title = UiText.res(R.string.catalog_refused_title, song.title),
        lines = listOf(usesLine(uses), UiText.res(R.string.catalog_refused_advice)),
        okLabel = UiText.res(R.string.catalog_refused_ok),
        events = EventHandler(key = song.id.value) { event ->
            when (event) {
                DeleteSongUiModel.Refused.Event.Dismiss -> onClose()
            }
        },
    )
}

/** "Está en la lista de la jam del sábado 25 de julio.", or the two dates, or the first and how many more. */
private fun usesLine(uses: List<LocalDate>): UiText = when (uses.size) {
    1 -> UiText.res(R.string.catalog_refused_one, SpanishDateNames.sentence(uses[0]))

    2 -> UiText.res(
        R.string.catalog_refused_two,
        SpanishDateNames.sentence(uses[0]),
        SpanishDateNames.sentence(uses[1]),
    )

    else -> UiText.res(R.string.catalog_refused_many, SpanishDateNames.sentence(uses[0]), uses.size - 1)
}
