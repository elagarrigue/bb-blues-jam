package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import java.time.LocalDate

/**
 * Everything Anteriores draws, as plain values. Read-only: the Sheet owns past jams (D-04), so
 * there is no event but the states' Retry, which is a read, and a row's Open, which navigates.
 * [title] ("Jams anteriores") is screen
 * chrome, drawn in every state.
 */
sealed interface PastJamsUiModel : UiModel {
    val title: String

    /** Nothing to show yet. Drawn as skeleton rows; [description] is what a screen reader says. */
    data class Loading(override val title: String, val description: String) : PastJamsUiModel

    /** Nothing was ever fetched and the latest read failed: the error block with a retry button. */
    data class Failed(override val title: String, val error: ListErrorUiModel) : PastJamsUiModel

    /** The jams were read and none is past. [staleness] is set when the latest refresh failed. */
    data class Empty(
        override val title: String,
        val empty: EmptyStateUiModel,
        val staleness: StalenessNoticeUiModel?,
    ) : PastJamsUiModel

    /** The past jams, newest first. [staleness] is set exactly when the latest refresh failed. */
    data class Jams(
        override val title: String,
        val rows: List<PastJamRowUiModel>,
        val staleness: StalenessNoticeUiModel?,
    ) : PastJamsUiModel
}

/**
 * One past jam. [date] is its identity (the list key); [dateLabel] is "Sábado 25 de julio de 2026";
 * [venue] is the Sheet's `lugar` as written. A row with songs opens its jam (`past-jam-detail`):
 * [openLabel] is the click label ("ver la lista de temas"), null for a [PastJamSummary.NotShown]
 * row, which is not tappable; [events] receives [Event.Open].
 */
data class PastJamRowUiModel(
    val date: LocalDate,
    val dateLabel: String,
    val venue: String,
    val summary: PastJamSummary,
    val openLabel: String?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Open this jam's song list. Navigation only, never a write. */
        data object Open : Event
    }
}

/** What a past jam row says under its date and venue. */
sealed interface PastJamSummary : UiModel {
    /** [countLabel] is "13 temas"; [hook] is the first titles in position order, then "y n más". */
    data class Songs(val countLabel: String, val hook: String) : PastJamSummary

    /** The setlist was never published, cannot be read, or has no songs: one line instead. */
    data class NotShown(val message: String) : PastJamSummary
}
