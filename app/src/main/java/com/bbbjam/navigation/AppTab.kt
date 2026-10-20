package com.bbbjam.navigation

import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.nav.TabBarUiModel
import com.bbbjam.core.ui.nav.TabIcon
import com.bbbjam.core.ui.nav.TabUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText

/**
 * The bottom bar's tabs in bar order, each with its inner route, its approved label (Rioplatense
 * Spanish, D-12) and its icon (I1).
 */
internal enum class AppTab(val route: String, val label: Int, val icon: TabIcon, val adminOnly: Boolean = false) {
    NEXT_JAM(AppRoutes.NEXT_JAM, R.string.tab_next_jam, TabIcon.SETLIST),
    PAST_JAMS(AppRoutes.PAST_JAMS, R.string.tab_jams, TabIcon.ARCHIVE),

    /** Admin only (`admin-catalog-tab`): the catalog, between Jams and Info. */
    CATALOG(AppRoutes.CATALOG, R.string.tab_catalog, TabIcon.CATALOG, adminOnly = true),
    INFO(AppRoutes.INFO, R.string.tab_info, TabIcon.INFO),

    /** Admin only (`assistant-admin-tab`); always last, so the musician tabs never move. */
    ASSISTANT(AppRoutes.ASSISTANT, R.string.tab_assistant, TabIcon.ASSISTANT, adminOnly = true),
    ;

    companion object {
        /** The tab whose route is [route], or null for any other route (or none). */
        fun fromRoute(route: String?): AppTab? = entries.firstOrNull { it.route == route }
    }
}

/**
 * The tabs the bar draws: the musician tabs always, the [AppTab.adminOnly] ones (Temas, Asistente) only when
 * [admin] is true. [admin] is null while the flag has not been read yet (process start, restore): no admin tab then.
 */
internal fun visibleTabs(admin: Boolean?): List<AppTab> = AppTab.entries.filter { !it.adminOnly || admin == true }

/**
 * True when the shell must leave an admin-only tab (Temas, Asistente): it is selected and the flag was read
 * as false (the session ended). A flag not read yet (null) never redirects.
 */
internal fun shouldLeaveAdminTab(selected: AppTab?, admin: Boolean?): Boolean =
    selected?.adminOnly == true && admin == false

/**
 * The bar for the current [selected] tab (none selected when null) over [visibleTabs] for [admin].
 * Each tab's handler calls [onSelect] with that tab; whether selecting the current tab does anything
 * is the caller's.
 */
internal fun tabBarModel(selected: AppTab?, admin: Boolean?, onSelect: (AppTab) -> Unit): TabBarUiModel = TabBarUiModel(
    visibleTabs(admin).map { tab ->
        TabUiModel(
            label = UiText.res(tab.label),
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
