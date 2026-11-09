package com.bbbjam.core.ui.reorder

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LIFT_SCALE = 1.02f
private val LIFT_ELEVATION = 8.dp
private val EDGE_MIN_ZONE = 48.dp

/**
 * Makes the item with [key] draggable after a long press while [enabled]. The dragged item follows the
 * finger, is lifted (scale, shadow in [shape]) and drawn above its neighbours. It does not consume
 * taps, so the item's own click keeps working. A cancelled drag reports nothing.
 */
fun Modifier.reorderable(state: ReorderState, key: Any, shape: Shape, enabled: Boolean = true): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pinnable = LocalPinnableContainer.current
    val session = remember(state, key) { DragSession(state, key, scope) }
    session.haptics = haptics
    session.pinnable = pinnable
    session.minZonePx = with(LocalDensity.current) { EDGE_MIN_ZONE.toPx() }
    session.touchSlop = LocalViewConfiguration.current.touchSlop
    DisposableEffect(session) { onDispose { session.end(commit = false) } }
    val lifted = state.draggedKey == key
    val gesture = if (enabled) {
        Modifier.pointerInput(session) {
            detectDragGesturesAfterLongPress(
                onDragStart = { session.begin(it.y) },
                onDrag = { change, amount ->
                    if (session.active) {
                        change.consume()
                        state.dragBy(amount.y)
                    }
                },
                onDragEnd = { session.end(commit = true) },
                onDragCancel = { session.end(commit = false) },
            )
        }
    } else {
        Modifier
    }
    gesture
        .zIndex(if (lifted) 1f else 0f)
        // Before graphicsLayer, so the reported position excludes the lift's own translation.
        .onPlaced { state.onItemPlaced(key, it) }
        .graphicsLayer {
            if (lifted) {
                translationY = state.liftTranslationY
                scaleX = LIFT_SCALE
                scaleY = LIFT_SCALE
            }
        }
        .then(if (lifted) Modifier.shadow(LIFT_ELEVATION, shape) else Modifier)
}

/**
 * One item's drag from long press to release. A lazy item scrolled out of the viewport is disposed and
 * its gesture cancelled, so the lifted item is pinned for the whole drag and released at its end.
 */
private class DragSession(private val state: ReorderState, private val key: Any, private val scope: CoroutineScope) {
    var haptics: HapticFeedback? = null
    var pinnable: PinnableContainer? = null
    var minZonePx = 0f
    var touchSlop = 0f
    var active = false
        private set
    private var pin: PinnableContainer.PinnedHandle? = null
    private var scroller: Job? = null

    fun begin(grabY: Float) {
        haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        state.start(key, grabY)
        if (state.draggedKey != key) return
        active = true
        pin = pinnable?.pin()
        scroller = scope.launch {
            while (isActive) {
                withFrameNanos { }
                state.autoScrollStep(minZonePx, touchSlop)
            }
        }
    }

    fun end(commit: Boolean) {
        scroller?.cancel()
        scroller = null
        pin?.release()
        pin = null
        if (active) {
            active = false
            state.finish(commit)
        }
    }
}
