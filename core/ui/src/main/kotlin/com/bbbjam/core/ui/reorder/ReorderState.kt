package com.bbbjam.core.ui.reorder

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import kotlin.math.abs

/**
 * The ephemeral state of one long-press drag over a `LazyColumn`: which item is lifted and how far
 * the finger has moved it. It changes every frame, so it never enters a `UiModel` (D-02); the drop
 * is reported through the `onDrop` callback and the presenter decides what it means.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val isTarget: () -> (Any) -> Boolean,
    private val onDrop: () -> (Any, DropSlot?) -> Unit,
) {
    var draggedKey: Any? by mutableStateOf(null)
        private set
    private var lastDragEndNanos = Long.MIN_VALUE

    // All in the coordinates of `LazyListLayoutInfo.visibleItemsInfo` offsets. The finger does not move
    // when the list scrolls, so [pointerY] stays valid after the row's own slot has left the viewport.
    // Both are snapshot state, so the lift's translation is recomputed every frame.
    private var pointerY by mutableFloatStateOf(0f)
    private var placedTop by mutableFloatStateOf(0f)
    private var itemSize = 0f
    private var grabY = 0f
    private var startFingerY = 0f
    private var startNanos = 0L
    private var listCoordinates: LayoutCoordinates? = null

    /**
     * Where the lifted row must be drawn relative to where the lazy list placed it. A pinned item that
     * is far from the visible window is placed adjacent to it, not at its true offset, so the offset is
     * derived from the finger and the row's actual placement, never accumulated from scrolling.
     */
    val liftTranslationY: Float
        get() = ReorderMath.liftTranslation(pointerY, itemSize, placedTop)

    private val dropSlotState = derivedStateOf {
        val dragged = draggedKey
        if (dragged == null) {
            null
        } else {
            val accepts = isTarget()
            val visible = listState.layoutInfo.visibleItemsInfo
            val others = visible
                .filter { it.key != dragged && accepts(it.key) }
                .map { ItemBounds(it.key, it.offset.toFloat(), (it.offset + it.size).toFloat()) }
            // The lifted row's own placed top is where its gap is. Not visible (pinned far away): centre rule.
            val gapTop = visible.firstOrNull { it.key == dragged }?.offset?.toFloat()
            ReorderMath.dropSlot(pointerY, others, itemSize, gapTop, GAP_MARGIN_FRACTION)
        }
    }

    /** Where the lifted row would land right now (null when not dragging); updates with finger and scroll. */
    val dropSlot: DropSlot? get() = dropSlotState.value

    internal fun onListPlaced(coordinates: LayoutCoordinates) {
        listCoordinates = coordinates
    }

    /** Reports the placed (translation-free) position of the item with [key]; only the lifted one matters. */
    internal fun onItemPlaced(key: Any, coordinates: LayoutCoordinates) {
        val list = listCoordinates?.takeIf { it.isAttached && coordinates.isAttached }
        if (draggedKey == key && list != null) {
            placedTop = list.localPositionOf(coordinates, Offset.Zero).y + listState.layoutInfo.viewportStartOffset
        }
    }

    /** True during a drag and for a short moment after it, so the row's own tap does not fire on release. */
    fun recentlyDragged(nowNanos: Long = System.nanoTime()): Boolean =
        draggedKey != null || nowNanos - lastDragEndNanos < TAP_GUARD_NANOS

    internal fun start(key: Any, grabY: Float = 0f) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        itemSize = item.size.toFloat()
        this.grabY = grabY
        pointerY = item.offset + itemSize / 2f
        placedTop = item.offset.toFloat()
        startFingerY = ReorderMath.fingerY(pointerY, itemSize, grabY)
        startNanos = System.nanoTime()
        draggedKey = key
    }

    internal fun dragBy(deltaY: Float) {
        pointerY += deltaY
    }

    /**
     * One frame of edge auto-scroll, measured from the finger (not the row's centre) with zones that do
     * not depend on the row's height. Held off right after the lift until [AUTO_SCROLL_DELAY_NANOS] pass or
     * the finger has moved [touchSlop], so lifting a row near an edge does not run the list away.
     */
    internal suspend fun autoScrollStep(minZone: Float, touchSlop: Float, nowNanos: Long = System.nanoTime()) {
        if (draggedKey == null) return
        val finger = ReorderMath.fingerY(pointerY, itemSize, grabY)
        val settled = nowNanos - startNanos >= AUTO_SCROLL_DELAY_NANOS || abs(finger - startFingerY) >= touchSlop
        if (!settled) return
        val info = listState.layoutInfo
        val top = info.viewportStartOffset.toFloat()
        val bottom = info.viewportEndOffset.toFloat()
        val threshold = ReorderMath.autoScrollThreshold(itemSize, bottom - top, minZone)
        val delta = ReorderMath.edgeScrollDelta(finger, top, bottom, threshold, itemSize / EDGE_STEP_DIVISOR)
        if (delta != 0f) listState.scrollBy(delta)
    }

    internal fun finish(commit: Boolean) {
        val key = draggedKey
        // The slot the user last saw, never recomputed at release.
        if (key != null && commit) onDrop()(key, dropSlot)
        draggedKey = null
        lastDragEndNanos = System.nanoTime()
    }

    private companion object {
        // Dead band of the gap swap, as a fraction of the neighbour's step (about 5 dp on a 100 dp row).
        const val GAP_MARGIN_FRACTION = 0.05f
        const val TAP_GUARD_NANOS = 300_000_000L
        const val EDGE_STEP_DIVISOR = 4f
        const val AUTO_SCROLL_DELAY_NANOS = 300_000_000L
    }
}

/** Reports the list's coordinates to [state]; apply it to the `LazyColumn` whose rows use [reorderable]. */
fun Modifier.reorderHost(state: ReorderState): Modifier = onPlaced { state.onListPlaced(it) }

/**
 * Remembers the [ReorderState] of [listState]. [isTarget] says which item keys are rows that may be
 * dropped over; [onDrop] gets the dragged key and the slot the preview last showed.
 */
@Composable
fun rememberReorderState(
    listState: LazyListState,
    isTarget: (Any) -> Boolean,
    onDrop: (fromKey: Any, slot: DropSlot?) -> Unit,
): ReorderState {
    val currentIsTarget by rememberUpdatedState(isTarget)
    val currentOnDrop by rememberUpdatedState(onDrop)
    return remember(listState) { ReorderState(listState, { currentIsTarget }, { currentOnDrop }) }
}
