package com.bbbjam.feature.nextjam

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.bbbjam.core.ui.reorder.ReorderState
import com.bbbjam.core.ui.reorder.rememberReorderState
import com.bbbjam.feature.nextjam.admin.previewKeys
import com.bbbjam.feature.nextjam.admin.previewed
import kotlinx.coroutines.delay

private const val HOLD_MS = 2000L

/** The previewed order [keys] kept after a drop, over the committed order [from] it was dropped on. */
private data class HeldOrder(val keys: List<String>, val from: List<String>)

/**
 * The drag state of the setlist and the setlist to draw: while a row is lifted, the preview order (the
 * gap and the would-be numbers); after a drop, that order is held until the model reflects the move
 * (or [HOLD_MS] passes), so the row never flashes back to its old slot. The drop fires one `Drop` with
 * the previewed 1-based position.
 */
@Composable
internal fun rememberDragPreview(
    setlist: SetlistUiModel,
    listState: LazyListState,
    rowKeys: List<String>,
): Pair<ReorderState, SetlistUiModel> {
    val songs = setlist as? SetlistUiModel.Songs
    val orderKeys = songs?.orderKeys.orEmpty()
    var held by remember { mutableStateOf<HeldOrder?>(null) }
    val currentOrderKeys by rememberUpdatedState(orderKeys)
    val currentRows by rememberUpdatedState(songs?.rows.orEmpty())
    val reorder = rememberReorderState(
        listState = listState,
        isTarget = { it in rowKeys },
        onDrop = { fromKey, slot ->
            val drop = currentRows.firstOrNull { it.rowKey == fromKey }?.admin?.move?.reorder
            val keys = currentOrderKeys
            if (fromKey is String && drop != null && fromKey in keys) {
                val preview = previewKeys(keys, fromKey, slot)
                val toPosition = preview.indexOf(fromKey) + 1
                if (toPosition != keys.indexOf(fromKey) + 1) {
                    held = HeldOrder(preview, keys)
                    drop.events(ReorderActionUiModel.Event.Drop(toPosition))
                }
            }
        },
    )
    val dragged = reorder.draggedKey as? String
    LaunchedEffect(dragged) { if (dragged != null) held = null }
    val heldOrder = held
    LaunchedEffect(orderKeys, heldOrder) {
        if (heldOrder != null) {
            if (orderKeys == heldOrder.from) delay(HOLD_MS)
            held = null
        }
    }
    val displayed = when {
        songs == null -> setlist
        dragged != null -> songs.previewed(previewKeys(orderKeys, dragged, reorder.dropSlot))
        heldOrder != null -> songs.previewed(heldOrder.keys)
        else -> songs
    }
    return reorder to displayed
}
