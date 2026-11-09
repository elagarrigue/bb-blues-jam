package com.bbbjam.core.ui.reorder

import kotlin.math.abs

/** One laid-out list item: its [key] and its vertical extent in the viewport. */
internal data class ItemBounds(val key: Any, val top: Float, val bottom: Float) {
    val center: Float get() = (top + bottom) / 2f
}

/** Pure geometry of the long-press drag, JVM-tested. Knows nothing of lists, jams or repositories. */
internal object ReorderMath {
    /** The key of the candidate whose center is nearest to [draggedCenter], or null with no candidates. */
    fun hoveredKey(draggedCenter: Float, candidates: List<ItemBounds>): Any? =
        candidates.minByOrNull { abs(it.center - draggedCenter) }?.key

    /** Vertical translation that puts the lifted row's centre under the finger, given where it was placed. */
    fun liftTranslation(pointerY: Float, itemSize: Float, placedTop: Float): Float =
        pointerY - itemSize / 2f - placedTop

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
