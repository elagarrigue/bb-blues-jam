package com.bbbjam.core.ui.strip

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.abbreviation
import com.bbbjam.core.ui.text.abbreviationInCaps
import com.bbbjam.core.ui.text.fullName

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
                label = UiText.res(R.string.strip_open_label, slot.instrument.abbreviationInCaps()),
                contentDescription = UiText.res(R.string.strip_open_description, slot.instrument.fullName()),
                kind = InstrumentChipKind.OPEN_SLOT,
            )
        } else {
            InstrumentChipUiModel(
                label = UiText.res(R.string.strip_filled_label, slot.instrument.abbreviation(), name),
                contentDescription = UiText.res(R.string.strip_filled_description, slot.instrument.fullName(), name),
                kind = InstrumentChipKind.FILLED_SLOT,
            )
        }
    }
    val extraChips = extras.map { extra ->
        InstrumentChipUiModel(
            label = UiText.res(R.string.strip_extra_label, extra.instrument, extra.name),
            contentDescription = UiText.res(R.string.strip_extra_description, extra.instrument, extra.name),
            kind = InstrumentChipKind.EXTRA,
        )
    }
    return slotChips + extraChips
}
