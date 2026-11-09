package com.bbbjam.core.ui.reorder

/** Where a lifted row would be inserted, relative to another row's [key]. */
sealed interface DropSlot {
    val key: Any

    data class Before(override val key: Any) : DropSlot

    data class After(override val key: Any) : DropSlot
}
