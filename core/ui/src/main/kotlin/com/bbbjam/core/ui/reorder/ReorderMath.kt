package com.bbbjam.core.ui.reorder

/** One laid-out list item: its [key] and its vertical extent in the viewport. */
internal data class ItemBounds(val key: Any, val top: Float, val bottom: Float) {
    val center: Float get() = (top + bottom) / 2f
}

/** Pure geometry of the long-press drag, JVM-tested. Knows nothing of lists, jams or repositories. */
internal object ReorderMath {
    private const val EDGE_ZONE_FRACTION = 0.12f

    /**
     * Where the lifted row would land, given the drawn row's centre [pointerY]. Without [gapTop] it is the
     * centre rule: before the first of [others] whose centre is below the finger, else after the last.
     *
     * With [gapTop] (the lifted row's placed top, which is where its gap is) the slot follows the geometry:
     * the gap moves past a neighbour only once the drawn row is more than half that neighbour's step away
     * from the gap, plus [marginFraction] of the step as a dead band so the back-swap boundary differs from the
     * forward one and jitter cannot flap. A step is how far the gap moves; it is signed. One call may cross
     * several rows. [others] are the laid-out target rows without the lifted one, in list order, with the gap
     * already in the layout. Null with no [others].
     */
    fun dropSlot(
        pointerY: Float,
        others: List<ItemBounds>,
        liftedSize: Float = 0f,
        gapTop: Float? = null,
        marginFraction: Float = 0f,
    ): DropSlot? {
        if (others.isEmpty()) return null
        val index = if (gapTop == null) {
            others.indexOfFirst { it.center > pointerY }.let { if (it < 0) others.size else it }
        } else {
            gapIndex(pointerY - liftedSize / 2f, others, liftedSize, gapTop, marginFraction)
        }
        return if (index < others.size) DropSlot.Before(others[index].key) else DropSlot.After(others.last().key)
    }

    /** The index in [others] the gap sits before, after moving it from [gapTop] toward the drawn row's top. */
    private fun gapIndex(
        drawnTop: Float,
        others: List<ItemBounds>,
        liftedSize: Float,
        gapTop: Float,
        marginFraction: Float,
    ): Int {
        var index = others.count { it.center < gapTop + liftedSize / 2f }
        var gap = gapTop
        var moved = true
        while (moved && index < others.size) {
            val next = others[index].bottom - liftedSize
            val step = next - gap
            moved = drawnTop - gap > step / 2f + marginFraction * step
            if (moved) {
                gap = next
                index++
            }
        }
        if (gap == gapTop) {
            moved = true
            while (moved && index > 0) {
                val next = others[index - 1].top
                val step = next - gap
                moved = drawnTop - gap < step / 2f + marginFraction * step
                if (moved) {
                    gap = next
                    index--
                }
            }
        }
        return index
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
