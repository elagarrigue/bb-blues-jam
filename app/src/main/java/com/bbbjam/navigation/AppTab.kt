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

    /** Admin only (`assistant-admin-tab`); always last, so the musician tabs never move. */
    ASSISTANT(AppRoutes.ASSISTANT, "Asistente", TabIcon.ASSISTANT),
    ;

    companion object {
        /** The tab whose route is [route], or null for any other route (or none). */
        fun fromRoute(route: String?): AppTab? = entries.firstOrNull { it.route == route }
    }
}

/**
 * The tabs the bar draws: the musician tabs always, [AppTab.ASSISTANT] only when [admin] is true.
 * [admin] is null while the flag has not been read yet (process start, restore): no admin tab then.
 */
internal fun visibleTabs(admin: Boolean?): List<AppTab> =
    AppTab.entries.filter { it != AppTab.ASSISTANT || admin == true }

/**
 * True when the shell must leave the Asistente tab: it is selected and the flag was read as false
 * (the session ended). A flag not read yet (null) never redirects.
 */
internal fun shouldLeaveAssistant(selected: AppTab?, admin: Boolean?): Boolean =
    selected == AppTab.ASSISTANT && admin == false

/**
 * The bar for the current [selected] tab (none selected when null) over [visibleTabs] for [admin].
 * Each tab's handler calls [onSelect] with that tab; whether selecting the current tab does anything
 * is the caller's.
 */
internal fun tabBarModel(selected: AppTab?, admin: Boolean?, onSelect: (AppTab) -> Unit): TabBarUiModel = TabBarUiModel(
    visibleTabs(admin).map { tab ->
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
