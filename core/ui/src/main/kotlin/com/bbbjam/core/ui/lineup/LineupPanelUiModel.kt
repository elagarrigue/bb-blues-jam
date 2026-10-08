package com.bbbjam.core.ui.lineup

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.strip.InstrumentChipKind

/**
 * The lineup of one song, as the expanded row shows it (DESIGN.md "Song row, expanded"): open slots
 * first, then filled slots, then the extra participants, each list in its own section.
 * [noOpenSlotsNote] replaces the open section when no slot is open, and [hint] follows the open
 * lines only when at least one slot is open; each is null otherwise. Read-only: nothing here is a
 * control.
 */
data class LineupPanelUiModel(
    val openSlots: List<LineupLineUiModel>,
    val filledSlots: List<LineupLineUiModel>,
    val extras: List<LineupLineUiModel>,
    val noOpenSlotsNote: String?,
    val hint: String?,
) : UiModel

/**
 * One line of the panel: [instrument] ("Guitarra", or "+ saxo" for an extra), [detail] ("LIBRE" or
 * the musician's name as in the Sheet) and what a screen reader says for the whole line
 * ([contentDescription], the strip's own descriptions). [kind] reuses the strip's three kinds.
 */
data class LineupLineUiModel(
    val instrument: String,
    val detail: String,
    val contentDescription: String,
    val kind: InstrumentChipKind,
    /** Present only for an open admin slot in Próxima jam. */
    val actionLabel: String? = null,
    val action: EventHandler<LineupLineEvent>? = null,
) : UiModel

sealed interface LineupLineEvent : UiEvent {
    data object Activate : LineupLineEvent
}
