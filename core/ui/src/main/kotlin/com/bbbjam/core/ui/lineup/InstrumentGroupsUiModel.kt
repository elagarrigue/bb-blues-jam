package com.bbbjam.core.ui.lineup

import com.bbbjam.core.ui.presenter.UiModel

/**
 * The lineup of one song grouped by instrument, as the song detail shows it (`song-detail-screen`,
 * G1): [groups] in the order each instrument first appears in the lineup (the Sheet's column
 * order), then [extras] under `Otros`, never merged into a group (D-18). [noOpenSlotsNote] and
 * [hint] follow the lineup panel's rules: the note when no slot is open, the hint when at least one
 * is; each is null otherwise. Read-only: nothing here is a control.
 */
data class InstrumentGroupsUiModel(
    val groups: List<InstrumentGroupUiModel>,
    val extras: List<LineupLineUiModel>,
    val noOpenSlotsNote: String?,
    val hint: String?,
) : UiModel

/**
 * One instrument's slots: [heading] is the instrument's full name ("Guitarra", drawn in uppercase)
 * and [lines] are its open slots first, then its filled slots, each in lineup order. The lines are
 * the panel's own, with the same descriptions ("Guitarra: libre", "Guitarra: Tincho").
 */
data class InstrumentGroupUiModel(val heading: String, val lines: List<LineupLineUiModel>) : UiModel
