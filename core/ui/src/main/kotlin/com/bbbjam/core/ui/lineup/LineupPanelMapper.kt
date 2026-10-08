package com.bbbjam.core.ui.lineup

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentStripCopy

/**
 * The panel of one song: the open slots in lineup order, then the filled slots in lineup order (a
 * stable partition, so two guitars keep their column order within each section), then [extras] in
 * their `Otros` order. Guitars are not numbered. An extra is never a slot and never open (D-18).
 *
 * Pure: presenters call it, so every feature that draws an expanded lineup maps it identically.
 */
fun Lineup.toLineupPanel(
    extras: List<ExtraParticipant>,
    onAssign: ((Instrument, SlotPosition) -> Unit)? = null,
): LineupPanelUiModel {
    val indexed = slots.mapIndexed { index, slot -> index to slot }
    val (open, filled) = indexed.partition { (_, slot) -> slot.isOpen }
    return LineupPanelUiModel(
        openSlots = open.map { (index, slot) ->
            val position = positionOf(index)
            slot.toLine(
                onAssign?.takeIf { position != null }?.let { handler ->
                    { handler(slot.instrument, requireNotNull(position)) }
                },
                actionKey = position?.let { "${slot.instrument.name}:${it.value}" },
            )
        },
        filledSlots = filled.map { (_, slot) -> slot.toLine() },
        extras = extras.map { it.toLine() },
        noOpenSlotsNote = noOpenSlotsNote(hasOpenSlot = open.isNotEmpty()),
        hint = openSlotsHint(hasOpenSlot = open.isNotEmpty()),
    )
}

/** The note drawn when no slot is open, or null. Shared with the instrument groups. */
internal fun noOpenSlotsNote(hasOpenSlot: Boolean): String? = if (hasOpenSlot) null else LineupPanelCopy.NO_OPEN_SLOTS

/** The hint drawn when at least one slot is open, or null. Shared with the instrument groups. */
internal fun openSlotsHint(hasOpenSlot: Boolean): String? = if (hasOpenSlot) LineupPanelCopy.HINT else null

internal fun Slot.toLine(onAssign: (() -> Unit)? = null, actionKey: String? = null): LineupLineUiModel {
    val name = musicianName
    return if (name == null) {
        LineupLineUiModel(
            instrument = InstrumentStripCopy.name(instrument),
            detail = LineupPanelCopy.OPEN_DETAIL,
            contentDescription = InstrumentStripCopy.openDescription(instrument),
            kind = InstrumentChipKind.OPEN_SLOT,
            actionLabel = if (onAssign == null) null else LineupPanelCopy.ASSIGN,
            action = onAssign?.let { callback ->
                com.bbbjam.core.ui.presenter.EventHandler(key = "assign:${actionKey ?: instrument.name}") { event ->
                    when (event) {
                        LineupLineEvent.Activate -> callback()
                    }
                }
            },
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

internal fun ExtraParticipant.toLine() = LineupLineUiModel(
    instrument = LineupPanelCopy.extraInstrument(instrument),
    detail = name,
    contentDescription = InstrumentStripCopy.extraDescription(instrument, name),
    kind = InstrumentChipKind.EXTRA,
)
