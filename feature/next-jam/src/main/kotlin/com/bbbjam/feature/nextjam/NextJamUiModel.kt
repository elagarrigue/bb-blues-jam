package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.strip.InstrumentChipUiModel

/**
 * Everything Próxima jam draws, as plain values. No events in this slice: rows do not expand yet
 * (`song-row-expansion`), and loading, error and offline get their designs in `list-states`.
 */
sealed interface NextJamUiModel : UiModel {
    /** Nothing to show yet: no emission, or nothing was ever fetched. The screen draws only the background. */
    data object Loading : NextJamUiModel

    /** The jams were read and none is upcoming. */
    data class NoUpcomingJam(val message: String) : NextJamUiModel

    data class Jam(val header: JamHeaderUiModel, val setlist: SetlistUiModel) : NextJamUiModel
}

/** [date] is "Sábado 31 de octubre · 21:00"; [venue] is the Sheet's `lugar` as written. */
data class JamHeaderUiModel(val date: String, val venue: String, val timeRemaining: String) : UiModel

sealed interface SetlistUiModel : UiModel {
    /** The rows in position order; [droppedRowsNote] says how many rows could not be read, or is null. */
    data class Songs(val rows: List<SongRowUiModel>, val droppedRowsNote: String?) : SetlistUiModel

    /** The setlist is withheld (a draft) or unavailable: one line instead of rows. */
    data class NotShown(val message: String) : SetlistUiModel
}

/**
 * One collapsed row. [position] is the Sheet's `posicion`, never renumbered; [positionLabel] is it
 * zero-padded ("01"). [keyDescription] is what a screen reader says for [key]. [instruments] is the
 * instrument strip: the lineup's slots in Sheet column order, then the extra participants.
 */
data class SongRowUiModel(
    val position: Int,
    val positionLabel: String,
    val title: String,
    val key: String,
    val keyDescription: String,
    val instruments: List<InstrumentChipUiModel>,
) : UiModel
