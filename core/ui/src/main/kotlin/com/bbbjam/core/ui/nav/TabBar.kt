package com.bbbjam.core.ui.nav

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The app's bottom bar (`bottom-navigation`): Material 3 `NavigationBar` with every color set from
 * [TabBarDefaults] (Material defaults are not design decisions). Each item is a screen-reader tab
 * with its selected state (`NavigationBarItem` gives `Role.Tab`), at least 48dp tall (the bar is
 * 80dp), and its label always shown; the icon has no description because the label names the item.
 * The bar applies the navigation-bar inset itself.
 */
@Composable
fun TabBar(model: TabBarUiModel, modifier: Modifier = Modifier) {
    val colors = TabBarDefaults.colors()
    NavigationBar(
        modifier = modifier,
        containerColor = colors.container,
        contentColor = colors.unselectedContent,
    ) {
        model.tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.selected,
                onClick = { tab.events(TabUiModel.Event.Select) },
                icon = { Icon(imageVector = TabBarDefaults.icon(tab.icon), contentDescription = null) },
                label = { Text(text = tab.label, style = BluesJamTheme.typography.caption) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.selectedContent,
                    selectedTextColor = colors.selectedContent,
                    indicatorColor = colors.indicator,
                    unselectedIconColor = colors.unselectedContent,
                    unselectedTextColor = colors.unselectedContent,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun TabBarPreview() {
    BluesJamTheme {
        TabBar(
            TabBarUiModel(
                listOf(
                    TabUiModel("Próxima jam", TabIcon.SETLIST, selected = true, events = EventHandler {}),
                    TabUiModel("Anteriores", TabIcon.ARCHIVE, selected = false, events = EventHandler {}),
                    TabUiModel("Info", TabIcon.INFO, selected = false, events = EventHandler {}),
                ),
            ),
        )
    }
}
