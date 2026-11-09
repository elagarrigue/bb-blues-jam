package com.bbbjam.core.ui.reorder

/** One laid-out list item: its [key] and its vertical extent in the viewport. */
internal data class ItemBounds(val key: Any, val top: Float, val bottom: Float) {
    val center: Float get() = (top + bottom) / 2f
}

/** Pure geometry of the long-press drag, JVM-tested. Knows nothing of lists, jams or repositories. */
internal object ReorderMath {
    private const val EDGE_ZONE_FRACTION = 0.12f

    /**
     * Where the lifted row would land for a finger at [pointerY]: before the first of [others] (the laid-out
     * target rows without the lifted one) whose centre is below the finger, else after the last. Counting
     * centres above the finger, not the nearest centre, keeps the slot stable when a neighbour slides into
     * the gap. Null with no [others].
     */
    fun dropSlot(pointerY: Float, others: List<ItemBounds>): DropSlot? {
        val below = others.firstOrNull { it.center > pointerY }
        return when {
            below != null -> DropSlot.Before(below.key)
            others.isNotEmpty() -> DropSlot.After(others.last().key)
            else -> null
        }
    }

    /** Vertical translation that puts the lifted row's centre under the finger, given where it was placed. */
    fun liftTranslation(pointerY: Float, itemSize: Float, placedTop: Float): Float =
        pointerY - itemSize / 2f - placedTop

    /** The finger's vertical position, given the lifted row's centre [pointerY] and where it was grabbed. */
    fun fingerY(pointerY: Float, itemSize: Float, grabY: Float): Float = pointerY - itemSize / 2f + grabY

    /**
     * Width of each auto-scroll edge zone: at most one row, but never so tall that a tall row's zones
     * swallow the list; at least [minZone] and 12% of the viewport otherwise.
     */
    fun autoScrollThreshold(itemSize: Float, viewportHeight: Float, minZone: Float): Float =
        minOf(itemSize, maxOf(minZone, viewportHeight * EDGE_ZONE_FRACTION))

    /**
     * Pixels to scroll this frame: negative near the viewport's top, positive near its bottom, zero
     * between. It grows linearly from zero at [threshold] from the edge to [maxStep] at the edge or beyond.
     */
    fun edgeScrollDelta(
        pointerY: Float,
        viewportTop: Float,
        viewportBottom: Float,
        threshold: Float,
        maxStep: Float,
    ): Float {
        val fromTop = pointerY - viewportTop
        val fromBottom = viewportBottom - pointerY
        return when {
            threshold <= 0f -> 0f
            fromTop < threshold -> -maxStep * ((threshold - fromTop) / threshold).coerceIn(0f, 1f)
            fromBottom < threshold -> maxStep * ((threshold - fromBottom) / threshold).coerceIn(0f, 1f)
            else -> 0f
        }
    }
}
