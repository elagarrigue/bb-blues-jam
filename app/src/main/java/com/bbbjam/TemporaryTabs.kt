package com.bbbjam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.info.InfoScreen
import com.bbbjam.feature.nextjam.NextJamScreen

/**
 * A plain two-tab switch, Próxima jam (first) and Info, until `bottom-navigation` replaces this file
 * with the real bar (three tabs with icons, the navigation library, per-tab state and the status-bar
 * scrim). Switching disposes the other screen, so its scroll position is lost; accepted until then.
 * No amber: the selected tab is `text`, the other `textMuted`.
 */
@Composable
internal fun TemporaryTabs(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(Tab.NEXT_JAM) }
    val screenPadding = WindowInsets.statusBars.asPaddingValues()
    Column(modifier = modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
        Box(modifier = Modifier.weight(1f)) {
            when (selected) {
                Tab.NEXT_JAM -> NextJamScreen(contentPadding = screenPadding)
                Tab.INFO -> InfoScreen(contentPadding = screenPadding)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BluesJamTheme.colors.surface)
                .navigationBarsPadding()
                .selectableGroup(),
        ) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { selected = tab }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label,
                        style = BluesJamTheme.typography.body,
                        color = if (isSelected) BluesJamTheme.colors.text else BluesJamTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

private const val NEXT_JAM_LABEL = "Próxima jam"
private const val INFO_LABEL = "Info"

private enum class Tab(val label: String) {
    NEXT_JAM(NEXT_JAM_LABEL),
    INFO(INFO_LABEL),
}
