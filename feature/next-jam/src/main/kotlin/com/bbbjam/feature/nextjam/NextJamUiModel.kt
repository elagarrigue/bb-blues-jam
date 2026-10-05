package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.filter.InstrumentFilterBarUiModel
import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import com.bbbjam.core.ui.strip.InstrumentChipUiModel

/**
 * Everything Próxima jam draws, as plain values. Rows expand in place (`song-row-expansion`) and
 * can be filtered by instrument (`instrument-filter-chips`). Every non-happy path is its own state
 * (`list-states`): skeleton rows while nothing was read, an error block when nothing is cached and
 * the read failed, an empty block when there is nothing to list, and a staleness notice above
 * cached data whose latest refresh failed.
 */
sealed interface NextJamUiModel : UiModel {
    /**
     * Nothing to show yet: no emission, or nothing was ever fetched and a read is running (or none
     * has failed yet). Drawn as skeleton rows; [description] is what a screen reader says for them.
     */
    data class Loading(val description: String) : NextJamUiModel

    /** Nothing was ever fetched and the latest read failed: the error block with a retry button. */
    data class Failed(val error: ListErrorUiModel) : NextJamUiModel

    /** The jams were read and none is upcoming. [staleness] is set when the latest refresh failed. */
    data class NoUpcomingJam(val empty: EmptyStateUiModel, val staleness: StalenessNoticeUiModel?) : NextJamUiModel

    /** [staleness] is set exactly when something is cached and the latest refresh failed. */
    data class Jam(val header: JamHeaderUiModel, val setlist: SetlistUiModel, val staleness: StalenessNoticeUiModel?) :
        NextJamUiModel
}

/** [date] is "Sábado 31 de octubre · 21:00"; [venue] is the Sheet's `lugar` as written. */
data class JamHeaderUiModel(val date: String, val venue: String, val timeRemaining: String) : UiModel

sealed interface SetlistUiModel : UiModel {
    /**
     * The rows in position order, only those the instrument filter shows; [droppedRowsNote] says how
     * many rows could not be read, or is null. [filterBar] is the instrument filter
     * (`instrument-filter-chips`), null when the setlist has no song (the empty case is not a
     * filter's no-results case).
     */
    data class Songs(
        val rows: List<SongRowUiModel>,
        val droppedRowsNote: String?,
        val filterBar: InstrumentFilterBarUiModel?,
    ) : SetlistUiModel

    /** A published setlist with no song: the empty block, and no filter bar. */
    data class Empty(val empty: EmptyStateUiModel) : SetlistUiModel

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
 * so the whole setlist is data whether or not it is drawn. [detailLabel] ("Ver detalle del tema")
 * is the expanded row's action that opens the song detail (`song-detail-screen`).
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
    val detailLabel: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Expand a collapsed row or collapse an expanded one; other rows keep their state. */
        data object ToggleExpanded : Event

        /** Open this song's detail. Navigation only, never a write. */
        data object OpenDetail : Event
    }
}
