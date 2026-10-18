package com.bbbjam.core.ui.lineup

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.text.UiText

/**
 * The lineup of one song, as the song detail shows it (DESIGN.md "Song detail"): open slots
 * first, then filled slots, then the extra participants, each list in its own section.
 * [noOpenSlotsNote] replaces the open section when no slot is open, and [hint] follows the open
 * lines only when at least one slot is open; each is null otherwise (the admin's panel has no
 * hint). Read-only for musicians: only the admin's lines carry an action.
 */
data class LineupPanelUiModel(
    val openSlots: List<LineupLineUiModel>,
    val filledSlots: List<LineupLineUiModel>,
    val extras: List<LineupLineUiModel>,
    val noOpenSlotsNote: UiText?,
    val hint: UiText?,
) : UiModel

/**
 * One line of the panel: [instrument] ("Guitarra", or "+ saxo" for an extra), [detail] ("LIBRE" or
 * the musician's name as in the Sheet) and what a screen reader says for the whole line
 * ([contentDescription], the strip's own descriptions). [kind] reuses the strip's three kinds.
 */
data class LineupLineUiModel(
    val instrument: UiText,
    val detail: UiText,
    val contentDescription: UiText,
    val kind: InstrumentChipKind,
    /**
     * Present only on the admin's lines of the song detail: "Anotar" on an open slot, "Liberar" on
     * a filled one, "Quitar" on an extra.
     */
    val actionLabel: UiText? = null,
    val action: EventHandler<LineupLineEvent>? = null,
    /** What a screen reader says for the action button when its label alone is ambiguous, or null. */
    val actionDescription: UiText? = null,
) : UiModel

sealed interface LineupLineEvent : UiEvent {
    data object Activate : LineupLineEvent
}
