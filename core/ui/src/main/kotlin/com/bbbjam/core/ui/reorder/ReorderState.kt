package com.bbbjam.core.ui.reorder

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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

/**
 * The ephemeral state of one long-press drag over a `LazyColumn`: which item is lifted and how far
 * the finger has moved it. It changes every frame, so it never enters a `UiModel` (D-02); the drop
 * is reported through the `onDrop` callback and the presenter decides what it means.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val isTarget: () -> (Any) -> Boolean,
    private val onDrop: () -> (Any, Any) -> Unit,
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
    private var listCoordinates: LayoutCoordinates? = null

    /**
     * Where the lifted row must be drawn relative to where the lazy list placed it. A pinned item that
     * is far from the visible window is placed adjacent to it, not at its true offset, so the offset is
     * derived from the finger and the row's actual placement, never accumulated from scrolling.
     */
    val liftTranslationY: Float
        get() = ReorderMath.liftTranslation(pointerY, itemSize, placedTop)

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

    internal fun start(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        itemSize = item.size.toFloat()
        pointerY = item.offset + itemSize / 2f
        placedTop = item.offset.toFloat()
        draggedKey = key
    }

    internal fun dragBy(deltaY: Float) {
        pointerY += deltaY
    }

    internal suspend fun autoScrollStep() {
        if (draggedKey == null) return
        val info = listState.layoutInfo
        val delta = ReorderMath.edgeScrollDelta(
            pointerY = pointerY,
            viewportTop = info.viewportStartOffset.toFloat(),
            viewportBottom = info.viewportEndOffset.toFloat(),
            threshold = itemSize,
            maxStep = itemSize / EDGE_STEP_DIVISOR,
        )
        if (delta != 0f) listState.scrollBy(delta)
    }

    internal fun finish(commit: Boolean) {
        val key = draggedKey
        if (key != null && commit) {
            val accepts = isTarget()
            val candidates = listState.layoutInfo.visibleItemsInfo.filter { accepts(it.key) }
                .map { ItemBounds(it.key, it.offset.toFloat(), (it.offset + it.size).toFloat()) }
            val target = ReorderMath.hoveredKey(pointerY, candidates)
            if (target != null && target != key) onDrop()(key, target)
        }
        draggedKey = null
        lastDragEndNanos = System.nanoTime()
    }

    private companion object {
        const val TAP_GUARD_NANOS = 300_000_000L
        const val EDGE_STEP_DIVISOR = 4f
    }
}

/** Reports the list's coordinates to [state]; apply it to the `LazyColumn` whose rows use [reorderable]. */
fun Modifier.reorderHost(state: ReorderState): Modifier = onPlaced { state.onListPlaced(it) }

/**
 * Remembers the [ReorderState] of [listState]. [isTarget] says which item keys are rows that may be
 * dropped over; [onDrop] gets the dragged key and the key of the row the drop landed on (never equal).
 */
@Composable
fun rememberReorderState(
    listState: LazyListState,
    isTarget: (Any) -> Boolean,
    onDrop: (fromKey: Any, toKey: Any) -> Unit,
): ReorderState {
    val currentIsTarget by rememberUpdatedState(isTarget)
    val currentOnDrop by rememberUpdatedState(onDrop)
    return remember(listState) { ReorderState(listState, { currentIsTarget }, { currentOnDrop }) }
}
