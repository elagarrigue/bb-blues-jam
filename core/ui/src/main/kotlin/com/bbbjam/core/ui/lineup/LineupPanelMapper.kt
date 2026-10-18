package com.bbbjam.core.ui.lineup

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.fullName

/**
 * The panel of one song: the open slots in lineup order, then the filled slots in lineup order (a
 * stable partition, so two guitars keep their column order within each section), then [extras] in
 * their `Otros` order. Guitars are not numbered. An extra is never a slot and never open (D-18).
 * With [onRemoveExtra] (the admin) each extra line gets "Quitar", called with its 1-based ordinal in
 * [extras] and the participant.
 *
 * Pure: presenters call it, so every screen that draws a lineup panel maps it identically.
 */
fun Lineup.toLineupPanel(
    extras: List<ExtraParticipant>,
    canAssign: (Instrument, SlotPosition) -> Boolean = { _, _ -> true },
    canClear: (Instrument, SlotPosition) -> Boolean = { _, _ -> true },
    isClearing: (Instrument, SlotPosition) -> Boolean = { _, _ -> false },
    clearingLabel: UiText = UiText.res(R.string.lineup_clearing),
    onRemoveExtra: ((ordinal: Int, ExtraParticipant) -> Unit)? = null,
    onClear: ((Instrument, SlotPosition, String) -> Unit)? = null,
    // Last, so a trailing lambda stays the assign handler.
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
        extras = extras.mapIndexed { index, extra ->
            val ordinal = index + 1
            extra.toLine(onRemoveExtra?.let { handler -> { handler(ordinal, extra) } }, ordinal)
        },
        noOpenSlotsNote = noOpenSlotsNote(hasOpenSlot = open.isNotEmpty()),
        hint = openSlotsHint(hasOpenSlot = open.isNotEmpty()),
    )
}

/** The note drawn when no slot is open, or null. */
internal fun noOpenSlotsNote(hasOpenSlot: Boolean): UiText? =
    if (hasOpenSlot) null else UiText.res(R.string.lineup_no_open_slots)

/** The hint drawn when at least one slot is open, or null. */
internal fun openSlotsHint(hasOpenSlot: Boolean): UiText? = if (hasOpenSlot) UiText.res(R.string.lineup_hint) else null

internal fun Slot.toLine(
    onAssign: (() -> Unit)? = null,
    actionKey: String? = null,
    onClear: (() -> Unit)? = null,
    pendingStatus: UiText? = null,
): LineupLineUiModel {
    val name = musicianName
    return if (name == null) {
        LineupLineUiModel(
            instrument = instrument.fullName(),
            detail = pendingStatus ?: UiText.res(R.string.lineup_open_detail),
            contentDescription = if (pendingStatus == null) {
                UiText.res(R.string.strip_open_description, instrument.fullName())
            } else {
                UiText.res(R.string.strip_filled_description, instrument.fullName(), pendingStatus)
            },
            kind = InstrumentChipKind.OPEN_SLOT,
            actionLabel = if (onAssign == null) null else UiText.res(R.string.lineup_assign),
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
            instrument = instrument.fullName(),
            detail = UiText.raw(name),
            contentDescription = if (onClear == null) {
                UiText.res(R.string.strip_filled_description, instrument.fullName(), name)
            } else {
                UiText.res(R.string.lineup_filled_clearable_description, instrument.fullName(), name)
            },
            kind = InstrumentChipKind.FILLED_SLOT,
            actionLabel = if (onClear == null) null else UiText.res(R.string.lineup_clear_slot),
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

internal fun ExtraParticipant.toLine(onRemove: (() -> Unit)? = null, ordinal: Int = 0) = LineupLineUiModel(
    instrument = UiText.res(R.string.lineup_extra_instrument, instrument),
    detail = UiText.raw(name),
    contentDescription = UiText.res(R.string.strip_extra_description, instrument, name),
    kind = InstrumentChipKind.EXTRA,
    actionLabel = onRemove?.let { UiText.res(R.string.lineup_remove_extra) },
    action = onRemove?.let { callback ->
        com.bbbjam.core.ui.presenter.EventHandler(key = "remove-extra:$ordinal:$name:$instrument") { event ->
            when (event) {
                LineupLineEvent.Activate -> callback()
            }
        }
    },
    actionDescription = onRemove?.let { UiText.res(R.string.lineup_remove_extra_description, name, instrument) },
)
