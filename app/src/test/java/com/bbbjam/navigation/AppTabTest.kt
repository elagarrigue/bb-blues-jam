package com.bbbjam.navigation

import com.bbbjam.core.ui.nav.TabIcon
import com.bbbjam.core.ui.nav.TabUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The bottom bar's tabs (`bottom-navigation`): order, approved labels, routes and the bar model. */
class AppTabTest {

    @Test
    fun `the tabs are in bar order with the approved labels, routes and icons`() {
        assertEquals(listOf(AppTab.NEXT_JAM, AppTab.PAST_JAMS, AppTab.INFO), AppTab.entries.toList())
        assertEquals(listOf("Próxima jam", "Anteriores", "Info"), AppTab.entries.map { it.label })
        assertEquals(listOf(AppRoutes.NEXT_JAM, AppRoutes.PAST_JAMS, AppRoutes.INFO), AppTab.entries.map { it.route })
        assertEquals(listOf(TabIcon.SETLIST, TabIcon.ARCHIVE, TabIcon.INFO), AppTab.entries.map { it.icon })
    }

    @Test
    fun `fromRoute finds each tab and nothing else`() {
        AppTab.entries.forEach { assertEquals(it, AppTab.fromRoute(it.route)) }
        assertNull(AppTab.fromRoute(null))
        assertNull(AppTab.fromRoute("unknown"))
        assertNull(AppTab.fromRoute(AppRoutes.TABS))
        assertNull(AppTab.fromRoute(AppRoutes.SONG_DETAIL))
    }

    @Test
    fun `the bar marks exactly the selected tab`() {
        AppTab.entries.forEach { selected ->
            val model = tabBarModel(selected) {}
            assertEquals(AppTab.entries.map { it == selected }, model.tabs.map { it.selected })
            assertEquals(AppTab.entries.map { it.label }, model.tabs.map { it.label })
            assertEquals(AppTab.entries.map { it.icon }, model.tabs.map { it.icon })
        }
        assertEquals(listOf(false, false, false), tabBarModel(null) {}.tabs.map { it.selected })
    }

    @Test
    fun `each tab's handler selects that tab`() {
        val selected = mutableListOf<AppTab>()
        val model = tabBarModel(AppTab.NEXT_JAM) { selected += it }
        model.tabs.forEach { it.events(TabUiModel.Event.Select) }
        assertEquals(AppTab.entries.toList(), selected)
    }
}
