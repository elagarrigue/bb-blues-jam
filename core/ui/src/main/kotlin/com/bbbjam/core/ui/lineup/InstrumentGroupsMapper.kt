package com.bbbjam.core.ui.lineup

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.ui.strip.InstrumentStripCopy

/**
 * The lineup of one song grouped by instrument (`song-detail-screen`, G1). Groups follow the order
 * in which each instrument first appears in [Lineup.slots] (the Sheet's column order); an
 * instrument with no slot has no group (D-18). Within a group, open slots come first, then filled
 * ones, each in lineup order (a stable partition). [extras] stay apart, in their `Otros` order,
 * even when the admin typed an instrument's name as free text: an extra is never a slot (D-18).
 *
 * Pure: presenters call it, so every feature that groups a lineup maps it identically.
 */
fun Lineup.toInstrumentGroups(extras: List<ExtraParticipant>): InstrumentGroupsUiModel {
    val hasOpenSlot = slots.any { it.isOpen }
    return InstrumentGroupsUiModel(
        groups = slots.groupBy { it.instrument }.map { (instrument, instrumentSlots) ->
            val (open, filled) = instrumentSlots.partition { it.isOpen }
            InstrumentGroupUiModel(
                heading = InstrumentStripCopy.name(instrument),
                lines = (open + filled).map { it.toLine() },
            )
        },
        extras = extras.map { it.toLine() },
        noOpenSlotsNote = noOpenSlotsNote(hasOpenSlot),
        hint = openSlotsHint(hasOpenSlot),
    )
}
