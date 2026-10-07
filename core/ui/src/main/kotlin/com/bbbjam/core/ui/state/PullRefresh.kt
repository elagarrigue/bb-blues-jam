package com.bbbjam.core.ui.state

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * Pull to refresh on a tab list (`live-refresh-during-jam`, L2). [isRefreshing] spins the indicator
 * and is true only while a refresh the user pulled is running (a periodic refresh never spins it).
 * [events] receives [Event.Refresh], which the presenter turns into one read
 * (`JamsRepository.refresh()`), never a write.
 */
data class PullRefreshUiModel(val isRefreshing: Boolean, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        /** Read the list again. */
        data object Refresh : Event
    }

    companion object {
        /**
         * Not refreshing, with a keyless handler that does nothing. Keyless handlers compare equal, so
         * a model built with this default equals a presenter's idle model.
         */
        val IDLE = PullRefreshUiModel(isRefreshing = false, events = EventHandler {})
    }
}

/**
 * Wraps a list in Material 3's `PullToRefreshBox`. The indicator's colours come from
 * [PullRefreshDefaults] (no amber). A pull gesture is not reachable with a screen reader, so the box
 * also carries the custom action "Actualizar" (L3), which sends the same [PullRefreshUiModel.Event.Refresh].
 * The [content] must scroll vertically (a lazy list, or a `verticalScroll` column) for the gesture to
 * reach the box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(
    model: PullRefreshUiModel,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val colors = PullRefreshDefaults.indicatorColors(BluesJamTheme.colors)
    val onRefresh: () -> Unit = { model.events(PullRefreshUiModel.Event.Refresh) }
    PullToRefreshBox(
        isRefreshing = model.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(PullRefreshCopy.ACTION) {
                    onRefresh()
                    true
                },
            )
        },
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = model.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = colors.container,
                color = colors.content,
            )
        },
        content = content,
    )
}

/**
 * The pull indicator's visual rules, kept apart from the composable so a JVM test can check them:
 * a `surfaceRaised` disc with a `text` arc. Never amber: refreshing is not a primary action.
 */
internal object PullRefreshDefaults {
    data class IndicatorColors(val container: Color, val content: Color)

    fun indicatorColors(colors: BluesJamColors = BluesJamColors): IndicatorColors =
        IndicatorColors(container = colors.surfaceRaised, content = colors.text)
}

/** The pull to refresh copy (L3, approved on 6 October 2026). Rioplatense Spanish (D-12). */
internal object PullRefreshCopy {
    /** The screen reader's custom action that refreshes the list. */
    const val ACTION = "Actualizar"
}
