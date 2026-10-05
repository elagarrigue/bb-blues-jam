package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel

/**
 * Everything a past jam's detail draws, as plain values (`past-jam-detail`). Every state carries
 * [back], so the back control works whatever the screen shows. Read only (D-04, D-13): the song
 * list and nothing about who played (user, 5 October 2026), no filter, no admin control, no row
 * event.
 */
sealed interface PastJamDetailUiModel : UiModel {
    val back: BackUiModel

    /** Nothing emitted yet (a local read). Only the back control is drawn; [description] is for screen readers. */
    data class Loading(val description: String, override val back: BackUiModel) : PastJamDetailUiModel

    /** No past jam has this date any more: the empty block. Never an error. */
    data class NotFound(val empty: EmptyStateUiModel, override val back: BackUiModel) : PastJamDetailUiModel

    /** The jam: its [header], then its songs or the line that replaces them. */
    data class Jam(val header: PastJamHeaderUiModel, val setlist: PastSetlistUiModel, override val back: BackUiModel) :
        PastJamDetailUiModel
}

/**
 * [dateLabel] is "Sábado 25 de julio de 2026"; [venue] is the Sheet's `lugar` as written;
 * [countLabel] is "13 temas", only when there are songs to show.
 */
data class PastJamHeaderUiModel(val dateLabel: String, val venue: String, val countLabel: String?) : UiModel

/** What sits under the header. */
sealed interface PastSetlistUiModel : UiModel {
    /** The songs in position order; [droppedRowsNote] says how many rows could not be read, or null. */
    data class Songs(val rows: List<PastSongRowUiModel>, val droppedRowsNote: String?) : PastSetlistUiModel

    /** The list was never published, cannot be read, or has no songs: one line (the Anteriores copy). */
    data class NotShown(val message: String) : PastSetlistUiModel
}

/**
 * One song as played that night. [positionLabel] is "01"; [artist] is null when blank; [key] is the
 * jam song's key as the admin set it (D-08), read as [keyDescription] ("Tonalidad B"). Deliberately
 * nothing else: no lineup, slot or musician (user, 5 October 2026), and no event.
 */
data class PastSongRowUiModel(
    val position: Int,
    val positionLabel: String,
    val title: String,
    val artist: String?,
    val key: String,
    val keyDescription: String,
) : UiModel
