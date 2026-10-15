package com.bbbjam.navigation

import com.bbbjam.core.ui.nav.TabIcon
import com.bbbjam.core.ui.nav.TabUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom bar's tabs (`bottom-navigation`): order, approved labels, routes and the bar model. */
class AppTabTest {

    @Test
    fun `the tabs are in bar order with the approved labels, routes and icons, Asistente last`() {
        val all = listOf(AppTab.NEXT_JAM, AppTab.PAST_JAMS, AppTab.INFO, AppTab.ASSISTANT)
        assertEquals(all, AppTab.entries.toList())
        assertEquals(listOf("Próxima jam", "Jams", "Info", "Asistente"), AppTab.entries.map { it.label })
        assertEquals(
            listOf(AppRoutes.NEXT_JAM, AppRoutes.PAST_JAMS, AppRoutes.INFO, AppRoutes.ASSISTANT),
            AppTab.entries.map { it.route },
        )
        assertEquals(
            listOf(TabIcon.SETLIST, TabIcon.ARCHIVE, TabIcon.INFO, TabIcon.ASSISTANT),
            AppTab.entries.map { it.icon },
        )
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
    fun `the Asistente tab is visible only when the admin flag is true`() {
        val musician = listOf(AppTab.NEXT_JAM, AppTab.PAST_JAMS, AppTab.INFO)
        assertEquals(musician, visibleTabs(null))
        assertEquals(musician, visibleTabs(false))
        assertEquals(musician + AppTab.ASSISTANT, visibleTabs(true))
    }

    @Test
    fun `the shell leaves Asistente only on a real false`() {
        assertTrue(shouldLeaveAssistant(AppTab.ASSISTANT, false))
        assertFalse(shouldLeaveAssistant(AppTab.ASSISTANT, null))
        assertFalse(shouldLeaveAssistant(AppTab.ASSISTANT, true))
        listOf(null, false, true).forEach { admin ->
            listOf(null, AppTab.NEXT_JAM, AppTab.PAST_JAMS, AppTab.INFO).forEach { selected ->
                assertFalse("$selected/$admin", shouldLeaveAssistant(selected, admin))
            }
        }
    }

    @Test
    fun `the bar marks exactly the selected tab over the visible tabs`() {
        listOf(null, false, true).forEach { admin ->
            val visible = visibleTabs(admin)
            visible.forEach { selected ->
                val model = tabBarModel(selected, admin) {}
                assertEquals(visible.map { it == selected }, model.tabs.map { it.selected })
                assertEquals(visible.map { it.label }, model.tabs.map { it.label })
                assertEquals(visible.map { it.icon }, model.tabs.map { it.icon })
            }
        }
        assertEquals(listOf(false, false, false), tabBarModel(null, false) {}.tabs.map { it.selected })
        assertEquals(listOf(false, false, false, false), tabBarModel(null, true) {}.tabs.map { it.selected })
        assertEquals(3, tabBarModel(AppTab.NEXT_JAM, null) {}.tabs.size)
        assertEquals(4, tabBarModel(AppTab.ASSISTANT, true) {}.tabs.size)
    }

    @Test
    fun `each tab's handler selects that tab`() {
        val selected = mutableListOf<AppTab>()
        val model = tabBarModel(AppTab.NEXT_JAM, admin = true) { selected += it }
        model.tabs.forEach { it.events(TabUiModel.Event.Select) }
        assertEquals(AppTab.entries.toList(), selected)
    }
}
