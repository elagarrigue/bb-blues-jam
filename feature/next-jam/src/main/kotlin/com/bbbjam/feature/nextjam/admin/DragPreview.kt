package com.bbbjam.feature.nextjam.admin

import com.bbbjam.core.ui.reorder.DropSlot
import com.bbbjam.core.ui.reorder.withDragged
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.feature.nextjam.SetlistUiModel

/** The full displayed order with [draggedKey] moved to [slot]; the one order preview, numbers and drop share. */
internal fun previewKeys(orderKeys: List<String>, draggedKey: String, slot: DropSlot?): List<String> =
    orderKeys.withDragged(draggedKey, slot)

/**
 * These songs laid out in [previewKeys] order, each row numbered by its place in that full order (hidden rows
 * count). Unchanged when [previewKeys] is null or is not a permutation of [SetlistUiModel.Songs.orderKeys].
 */
internal fun SetlistUiModel.Songs.previewed(previewKeys: List<String>?): SetlistUiModel.Songs {
    if (previewKeys == null || previewKeys.size != orderKeys.size || previewKeys.toSet() != orderKeys.toSet()) {
        return this
    }
    val indexOf = previewKeys.withIndex().associate { it.value to it.index }
    val reordered = rows.filter { it.rowKey in indexOf }
        .sortedBy { indexOf.getValue(it.rowKey) }
        .map { it.copy(positionLabel = SongListText.positionLabel(indexOf.getValue(it.rowKey) + 1)) }
    return copy(rows = reordered)
}
