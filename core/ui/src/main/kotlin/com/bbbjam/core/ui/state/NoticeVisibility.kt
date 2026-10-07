package com.bbbjam.core.ui.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Whether a staleness notice that just started being drawn should pull the list back to the top,
 * so it is not left above the visible area (`offline-notice-visible`). True only when the notice
 * is new (was absent, now present) and the user was already at or near the top. Takes primitives,
 * not a `LazyListState`, so it is unit-testable with no Compose UI harness.
 *
 * Public, like the sibling mappers [listError] and [stalenessNotice] in this package: used by
 * [rememberRevealingLazyListState] below and asserted directly in `NoticeVisibilityTest`.
 */
fun shouldRevealNotice(
    staleness: StalenessNoticeUiModel?,
    previousStaleness: StalenessNoticeUiModel?,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemIsTopAnchor: Boolean = false,
): Boolean {
    val appeared = staleness != null && previousStaleness == null
    val atTop =
        (firstVisibleItemIndex == 0 || firstVisibleItemIsTopAnchor) &&
            firstVisibleItemScrollOffset <= REST_THRESHOLD_PX
    return appeared && atTop
}

private const val REST_THRESHOLD_PX = 0

/**
 * A [LazyListState] that scrolls back to the notice (index 0) when it newly appears while the list
 * is resting at the top, so it is not inserted above the visible area (`offline-notice-visible`).
 * Does not re-trigger on every recomposition while the notice is already showing, and never pulls a
 * user who has scrolled away back to the top. Shared by `NextJamScreen` and `PastJamsScreen`, the
 * two screens that draw [StalenessNotice] (`list-states`). NextJam passes its keyed header as
 * [topAnchorKey] because the notice is inserted before that visible item.
 */
@Composable
fun rememberRevealingLazyListState(staleness: StalenessNoticeUiModel?, topAnchorKey: String? = null): LazyListState {
    val listState = rememberLazyListState()
    var previousStaleness by remember { mutableStateOf<StalenessNoticeUiModel?>(null) }
    LaunchedEffect(staleness) {
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index == listState.firstVisibleItemIndex
        }
        if (
            shouldRevealNotice(
                staleness,
                previousStaleness,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                firstVisibleItemIsTopAnchor =
                    topAnchorKey != null && visibleItem?.key == topAnchorKey,
            )
        ) {
            listState.animateScrollToItem(0)
        }
        previousStaleness = staleness
    }
    return listState
}
