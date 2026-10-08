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
    canAssign: (Instrument, SlotPosition) -> Boolean = { _, _ -> true },
    canClear: (Instrument, SlotPosition) -> Boolean = { _, _ -> true },
    isClearing: (Instrument, SlotPosition) -> Boolean = { _, _ -> false },
    clearingLabel: String = "Quitando…",
    onClear: ((Instrument, SlotPosition, String) -> Unit)? = null,
    onAssign: ((Instrument, SlotPosition) -> Unit)? = null,
): LineupPanelUiModel {
    val indexed = slots.mapIndexed { index, slot -> index to slot }
    val (open, filled) = indexed.partition { (_, slot) -> slot.isOpen }
    return LineupPanelUiModel(
        openSlots = open.map { (index, slot) ->
            val position = positionOf(index)
            val clearing = position != null && isClearing(slot.instrument, position)
            slot.toLine(
                onAssign?.takeIf {
                    position != null && !clearing && canAssign(slot.instrument, requireNotNull(position))
                }?.let { handler ->
                    { handler(slot.instrument, requireNotNull(position)) }
                },
                actionKey = position?.let { "${slot.instrument.name}:${it.value}" },
                pendingStatus = if (clearing) clearingLabel else null,
            )
        },
        filledSlots = filled.map { (index, slot) ->
            val position = positionOf(index)
            slot.toLine(
                actionKey = position?.let { "${slot.instrument.name}:${it.value}" },
                onClear = onClear?.takeIf {
                    position != null && canClear(slot.instrument, requireNotNull(position))
                }?.let { handler ->
                    { handler(slot.instrument, requireNotNull(position), requireNotNull(slot.musicianName)) }
                },
            )
        },
        extras = extras.map { it.toLine() },
        noOpenSlotsNote = noOpenSlotsNote(hasOpenSlot = open.isNotEmpty()),
        hint = openSlotsHint(hasOpenSlot = open.isNotEmpty()),
    )
}

/** The note drawn when no slot is open, or null. Shared with the instrument groups. */
internal fun noOpenSlotsNote(hasOpenSlot: Boolean): String? = if (hasOpenSlot) null else LineupPanelCopy.NO_OPEN_SLOTS

/** The hint drawn when at least one slot is open, or null. Shared with the instrument groups. */
internal fun openSlotsHint(hasOpenSlot: Boolean): String? = if (hasOpenSlot) LineupPanelCopy.HINT else null

internal fun Slot.toLine(
    onAssign: (() -> Unit)? = null,
    actionKey: String? = null,
    onClear: (() -> Unit)? = null,
    pendingStatus: String? = null,
): LineupLineUiModel {
    val name = musicianName
    return if (name == null) {
        LineupLineUiModel(
            instrument = InstrumentStripCopy.name(instrument),
            detail = pendingStatus ?: LineupPanelCopy.OPEN_DETAIL,
            contentDescription = if (pendingStatus == null) {
                InstrumentStripCopy.openDescription(instrument)
            } else {
                "${InstrumentStripCopy.name(instrument)}: $pendingStatus"
            },
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
            contentDescription = InstrumentStripCopy.filledDescription(instrument, name) +
                if (onClear == null) "" else ". ${LineupPanelCopy.CLEAR_SLOT}",
            kind = InstrumentChipKind.FILLED_SLOT,
            actionLabel = if (onClear == null) null else LineupPanelCopy.CLEAR_SLOT,
            action = onClear?.let { callback ->
                com.bbbjam.core.ui.presenter.EventHandler(key = "clear:${actionKey ?: instrument.name}") { event ->
                    when (event) {
                        LineupLineEvent.Activate -> callback()
                    }
                }
            },
        )
    }
}

internal fun ExtraParticipant.toLine() = LineupLineUiModel(
    instrument = LineupPanelCopy.extraInstrument(instrument),
    detail = name,
    contentDescription = InstrumentStripCopy.extraDescription(instrument, name),
    kind = InstrumentChipKind.EXTRA,
)
