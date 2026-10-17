package com.bbbjam.feature.nextjam

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bbbjam.core.ui.brand.BandLogo
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.state.StalenessNotice
import com.bbbjam.core.ui.state.rememberRevealingLazyListState
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.nextjam.admin.AdminHint
import com.bbbjam.feature.nextjam.admin.AdminStatusBlock
import com.bbbjam.feature.nextjam.admin.adminItems

@Composable
internal fun NoUpcomingJamContent(model: NextJamUiModel.NoUpcomingJam, modifier: Modifier, padding: PaddingValues) {
    val listState = rememberRevealingLazyListState(model.staleness, topAnchorKey = BRAND_HEADER_KEY)
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.md),
    ) {
        item(key = BRAND_HEADER_KEY) { BandLogo() }
        model.staleness?.let { notice -> item(key = STALENESS_KEY) { StalenessNotice(notice) } }
        item(key = EMPTY_KEY) { EmptyStateBlock(model.empty) }
        model.adminHint?.let { hint -> item(key = ADMIN_HINT_KEY) { AdminHint(hint) } }
    }
}

@Composable
internal fun JamContent(model: NextJamUiModel.Jam, modifier: Modifier, padding: PaddingValues) {
    val listState = rememberRevealingLazyListState(model.staleness, topAnchorKey = BRAND_HEADER_KEY)
    val rowKeys = ((model.setlist as? SetlistUiModel.Songs)?.rows).orEmpty().map { it.rowKey }
    var moveAnchor by remember { mutableStateOf<MoveScrollAnchor?>(null) }
    LaunchedEffect(moveAnchor, rowKeys) {
        val anchor = moveAnchor
        if (anchor != null) {
            val newIndex = rowKeys.indexOf(anchor.key)
            if (newIndex >= 0 && newIndex != anchor.rowIndex) {
                val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == anchor.key }
                if (visible != null) {
                    listState.scrollBy((visible.offset - anchor.offset).toFloat())
                } else {
                    listState.scrollToItem((anchor.lazyIndex + newIndex - anchor.rowIndex).coerceAtLeast(0))
                }
                moveAnchor = null
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm),
    ) {
        item(key = BRAND_HEADER_KEY) { BandLogo() }
        model.staleness?.let { notice -> item(key = STALENESS_KEY) { StalenessNotice(notice) } }
        // The header and the admin status block end their group with an extra `sm`, so the groups
        // sit `md` apart while song rows stay `sm` apart (`ui-polish-pass`, U11).
        item(key = HEADER_KEY) { Box(Modifier.padding(bottom = BluesJamTheme.spacing.sm)) { Header(model.header) } }
        model.admin?.let { admin ->
            item(key = ADMIN_STATUS_KEY) {
                Box(Modifier.padding(bottom = BluesJamTheme.spacing.sm)) { AdminStatusBlock(admin.status) }
            }
        }
        setlistItems(model.setlist) { rowKey, action ->
            if (action.enabled) {
                val rowIndex = rowKeys.indexOf(rowKey)
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rowKey }
                if (rowIndex >= 0 && item != null) {
                    moveAnchor = MoveScrollAnchor(rowKey, rowIndex, item.index, item.offset)
                }
                action.events(MoveActionUiModel.Event.Move)
            }
        }
        model.admin?.let { admin -> adminItems(admin) }
    }
}

internal data class MoveScrollAnchor(val key: String, val rowIndex: Int, val lazyIndex: Int, val offset: Int)
