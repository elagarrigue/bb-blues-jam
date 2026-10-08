package com.bbbjam.core.model

/** The 1-based ordinal of an instrument's slot within one [Lineup]. */
@JvmInline
value class SlotPosition(val value: Int) {
    init {
        require(value >= 1) { "Slot position must be 1-based" }
    }
}
