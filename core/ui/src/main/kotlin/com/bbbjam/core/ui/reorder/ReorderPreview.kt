package com.bbbjam.core.ui.reorder

/**
 * This list with [dragged] removed and re-inserted at [slot]. Unchanged when the slot is null, its key is
 * absent, or it is the dragged item itself.
 */
fun <K> List<K>.withDragged(dragged: K, slot: DropSlot?): List<K> {
    val items: List<Any?> = this
    val valid = slot != null && slot.key != dragged && dragged in this && slot.key in items
    if (!valid || slot == null) return this
    val rest = filter { it != dragged }
    val at = (rest as List<Any?>).indexOf(slot.key) + if (slot is DropSlot.After) 1 else 0
    return rest.take(at) + dragged + rest.drop(at)
}
