package com.bbbjam.core.ui.lineup

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentStripCopy

/**
 * The panel of one song: the open slots in lineup order, then the filled slots in lineup order (a
 * stable partition, so two guitars keep their column order within each section), then [extras] in
 * their `Otros` order. Guitars are not numbered. An extra is never a slot and never open (D-18).
 *
 * Pure: presenters call it, so every feature that draws an expanded lineup maps it identically.
 */
fun Lineup.toLineupPanel(extras: List<ExtraParticipant>): LineupPanelUiModel {
    val (open, filled) = slots.partition { it.isOpen }
    return LineupPanelUiModel(
        openSlots = open.map { it.toLine() },
        filledSlots = filled.map { it.toLine() },
        extras = extras.map { it.toLine() },
        noOpenSlotsNote = if (open.isEmpty()) LineupPanelCopy.NO_OPEN_SLOTS else null,
        hint = if (open.isEmpty()) null else LineupPanelCopy.HINT,
    )
}

private fun Slot.toLine(): LineupLineUiModel {
    val name = musicianName
    return if (name == null) {
        LineupLineUiModel(
            instrument = InstrumentStripCopy.name(instrument),
            detail = LineupPanelCopy.OPEN_DETAIL,
            contentDescription = InstrumentStripCopy.openDescription(instrument),
            kind = InstrumentChipKind.OPEN_SLOT,
        )
    } else {
        LineupLineUiModel(
            instrument = InstrumentStripCopy.name(instrument),
            detail = name,
            contentDescription = InstrumentStripCopy.filledDescription(instrument, name),
            kind = InstrumentChipKind.FILLED_SLOT,
        )
    }
}

private fun ExtraParticipant.toLine() = LineupLineUiModel(
    instrument = LineupPanelCopy.extraInstrument(instrument),
    detail = name,
    contentDescription = InstrumentStripCopy.extraDescription(instrument, name),
    kind = InstrumentChipKind.EXTRA,
)
