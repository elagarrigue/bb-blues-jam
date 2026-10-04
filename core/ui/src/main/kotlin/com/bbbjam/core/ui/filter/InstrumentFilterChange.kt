package com.bbbjam.core.ui.filter

import com.bbbjam.core.model.Instrument

/**
 * A change to the selected instruments, as the user asked for it. Handlers carry the intent, not
 * the resulting set: a chip that did not change compares equal to its previous model, so Compose
 * may keep an earlier model's handler, and a handler holding a stale set would undo another chip's
 * selection. The presenter applies the change to its current state with [updatedBy].
 */
sealed interface InstrumentFilterChange {
    data class Toggle(val instrument: Instrument) : InstrumentFilterChange

    data object Clear : InstrumentFilterChange
}

/** The selection after [change]: a toggle flips one instrument, a clear empties it. */
fun Set<Instrument>.updatedBy(change: InstrumentFilterChange): Set<Instrument> = when (change) {
    is InstrumentFilterChange.Toggle -> {
        val instrument = change.instrument
        if (instrument in this) this - instrument else this + instrument
    }

    InstrumentFilterChange.Clear -> emptySet()
}
