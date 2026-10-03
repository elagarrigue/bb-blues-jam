package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.strip.InstrumentChipUiModel

/**
 * Everything Próxima jam draws, as plain values. Rows expand in place (`song-row-expansion`);
 * loading, error and offline get their designs in `list-states`.
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
 * One song row. [position] is the Sheet's `posicion`, never renumbered; [positionLabel] is it
 * zero-padded ("01"). [keyDescription] is what a screen reader says for [key]. [instruments] is the
 * instrument strip (slots in Sheet column order, then the extra participants), drawn while
 * collapsed. When [isExpanded], the row shows [artist] and [lineup] (open slots first) instead.
 * [stateDescription] ("expandido"/"contraído") and [toggleLabel] ("ocultar los cupos"/"ver los
 * cupos") are the header's state and action for screen readers. [lineup] is built for every row,
 * so the whole setlist is data whether or not it is drawn.
 */
data class SongRowUiModel(
    val position: Int,
    val positionLabel: String,
    val title: String,
    val key: String,
    val keyDescription: String,
    val instruments: List<InstrumentChipUiModel>,
    val artist: String,
    val isExpanded: Boolean,
    val stateDescription: String,
    val toggleLabel: String,
    val lineup: LineupPanelUiModel,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Expand a collapsed row or collapse an expanded one; other rows keep their state. */
        data object ToggleExpanded : Event
    }
}
