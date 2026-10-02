package com.bbbjam.core.ui.strip

import com.bbbjam.core.ui.presenter.UiModel

/**
 * One chip of the instrument strip, fully formed: [label] is what the chip shows ("GTR: LIBRE",
 * "Gtr: Tincho", "+ saxo: Juan") and [contentDescription] what a screen reader says for it
 * ("Guitarra: libre"). [kind] picks the chip's style; only [InstrumentChipKind.OPEN_SLOT] is a place
 * to play.
 */
data class InstrumentChipUiModel(val label: String, val contentDescription: String, val kind: InstrumentChipKind) :
    UiModel {
    /** True only for an open lineup slot. An extra participant is never open (D-18). */
    val isOpen: Boolean
        get() = kind == InstrumentChipKind.OPEN_SLOT
}

/** What a chip stands for. Each kind has its own style in [InstrumentStrip]. */
enum class InstrumentChipKind {
    /** A lineup slot nobody is assigned to: amber text on a 15% amber fill, a dot. */
    OPEN_SLOT,

    /** A lineup slot with a musician: muted text on `slotFilled`, a check. */
    FILLED_SLOT,

    /** Someone playing outside the lineup ("Otros"): muted text, no fill, a leading "+". Never a slot. */
    EXTRA,
}
