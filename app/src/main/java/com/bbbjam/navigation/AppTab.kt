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

    /**
     * Admin only (`assistant-admin-tab`); always last, so the musician tabs never move. Drawn only when the
     * gateway answered Available at startup; the tab checks again each time it is entered.
     */
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
 * Asistente also needs [assistantAvailable] true: the gateway's status read at startup (or at login) answered
 * Available; null while that read has not answered.
 */
internal fun visibleTabs(admin: Boolean?, assistantAvailable: Boolean?): List<AppTab> = AppTab.entries.filter {
    when {
        !it.adminOnly -> true
        admin != true -> false
        it == AppTab.ASSISTANT -> assistantAvailable == true
        else -> true
    }
}

/**
 * True when the shell must leave an admin-only tab (Temas, Asistente): it is selected and the flag was read
 * as false (the session ended), or Asistente is selected (a restore) and the startup status read answered
 * that the gateway is not available. A value not read yet (null) never redirects.
 */
internal fun shouldLeaveAdminTab(selected: AppTab?, admin: Boolean?, assistantAvailable: Boolean?): Boolean =
    (selected?.adminOnly == true && admin == false) ||
        (selected == AppTab.ASSISTANT && assistantAvailable == false)

/**
 * The bar for the current [selected] tab (none selected when null) over [visibleTabs] for [admin] and
 * [assistantAvailable]. Each tab's handler calls [onSelect] with that tab; whether selecting the current tab
 * does anything is the caller's.
 */
internal fun tabBarModel(
    selected: AppTab?,
    admin: Boolean?,
    assistantAvailable: Boolean?,
    onSelect: (AppTab) -> Unit,
): TabBarUiModel = TabBarUiModel(
    visibleTabs(admin, assistantAvailable).map { tab ->
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
