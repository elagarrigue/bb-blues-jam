package com.bbbjam.navigation

import com.bbbjam.core.ui.nav.TabBarUiModel
import com.bbbjam.core.ui.nav.TabIcon
import com.bbbjam.core.ui.nav.TabUiModel
import com.bbbjam.core.ui.presenter.EventHandler

/**
 * The bottom bar's tabs in bar order, each with its inner route, its approved label (Rioplatense
 * Spanish, D-12) and its icon (I1).
 */
internal enum class AppTab(val route: String, val label: String, val icon: TabIcon) {
    NEXT_JAM(AppRoutes.NEXT_JAM, "Próxima jam", TabIcon.SETLIST),
    PAST_JAMS(AppRoutes.PAST_JAMS, "Anteriores", TabIcon.ARCHIVE),
    INFO(AppRoutes.INFO, "Info", TabIcon.INFO),
    ;

    companion object {
        /** The tab whose route is [route], or null for any other route (or none). */
        fun fromRoute(route: String?): AppTab? = entries.firstOrNull { it.route == route }
    }
}

/**
 * The bar for the current [selected] tab (none selected when null). Each tab's handler calls
 * [onSelect] with that tab; whether selecting the current tab does anything is the caller's.
 */
internal fun tabBarModel(selected: AppTab?, onSelect: (AppTab) -> Unit): TabBarUiModel = TabBarUiModel(
    AppTab.entries.map { tab ->
        TabUiModel(
            label = tab.label,
            icon = tab.icon,
            selected = tab == selected,
            events = EventHandler { event ->
                when (event) {
                    TabUiModel.Event.Select -> onSelect(tab)
                }
            },
        )
    },
)
