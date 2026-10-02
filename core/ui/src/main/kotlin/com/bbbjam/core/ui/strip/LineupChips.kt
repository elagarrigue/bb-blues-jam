package com.bbbjam.core.ui.strip

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Lineup

/**
 * The chips of one song: one per slot of this lineup **in list order**, which is the Sheet's column
 * order, so strips are comparable down the list; then one per [extras] entry, in its `Otros` order.
 * A removed slot is absent from the lineup and gets no chip. Guitars are not numbered. Extra
 * participants get [InstrumentChipKind.EXTRA] chips, never an open-looking one (D-18).
 *
 * Pure: presenters call it, so every feature that draws a lineup maps it identically.
 */
fun Lineup.toInstrumentChips(extras: List<ExtraParticipant>): List<InstrumentChipUiModel> {
    val slotChips = slots.map { slot ->
        val name = slot.musicianName
        if (name == null) {
            InstrumentChipUiModel(
                label = InstrumentStripCopy.openLabel(slot.instrument),
                contentDescription = InstrumentStripCopy.openDescription(slot.instrument),
                kind = InstrumentChipKind.OPEN_SLOT,
            )
        } else {
            InstrumentChipUiModel(
                label = InstrumentStripCopy.filledLabel(slot.instrument, name),
                contentDescription = InstrumentStripCopy.filledDescription(slot.instrument, name),
                kind = InstrumentChipKind.FILLED_SLOT,
            )
        }
    }
    val extraChips = extras.map { extra ->
        InstrumentChipUiModel(
            label = InstrumentStripCopy.extraLabel(extra.instrument, extra.name),
            contentDescription = InstrumentStripCopy.extraDescription(extra.instrument, extra.name),
            kind = InstrumentChipKind.EXTRA,
        )
    }
    return slotChips + extraChips
}
