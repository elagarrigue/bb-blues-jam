package com.bbbjam.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.TabBar
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.assistant.AssistantScreen
import com.bbbjam.feature.catalog.CatalogScreen
import com.bbbjam.feature.info.InfoScreen
import com.bbbjam.feature.nextjam.NextJamScreen
import com.bbbjam.feature.pastjams.PastJamsScreen
import java.time.LocalDate

/**
 * The tabs shell (`bottom-navigation`), the outer host's [AppRoutes.TABS] destination: an inner
 * host with one route per tab above the bottom bar. Its controller, with every tab's saved state
 * (scroll, filter), is saved inside the outer `tabs` entry, so it survives the song
 * detail, rotation and process death. Switching tabs saves the tab left and restores the one
 * entered (`saveState`/`restoreState`); back from Jams or Info returns to Próxima jam (the
 * inner host's back handler wins while its stack has more than one entry), and from Próxima jam
 * the activity finishes. The status-bar inset (and, in admin mode, the "MODO ADMIN" banner,
 * `admin-mode-highlight`) is applied once by [AppNavHost]'s shared wrapper outside this shell, so
 * content never scrolls under the clock or the banner. [onOpenSong] opens a song's detail and [onOpenPastJam] a
 * past jam's detail, [onOpenAdminLogin] the admin login and [onOpenAddSong] the admin's catalog
 * picker, all in the outer host. The admin's key picker and assign screen open from the song
 * detail (`song-detail-replaces-row-expansion`), not from the tabs.
 *
 * [admin] is the admin flag, null until it is read (`assistant-admin-tab`, `admin-catalog-tab`): the Temas and
 * Asistente tabs are in the bar and drawn only when it is true, and a real false while one is selected selects
 * Próxima jam.
 * The route is registered always (navigation graphs must be stable). While the keyboard is up the bar
 * is not drawn, so the composer sits right above the keyboard with no bar-high gap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabsShell(
    onOpenSong: (jamDate: LocalDate, songId: SongId, position: Int) -> Unit,
    onOpenPastJam: (jamDate: LocalDate) -> Unit,
    onOpenAdminLogin: () -> Unit,
    onOpenAddSong: (jamDate: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    admin: Boolean? = null,
) {
    val inner = rememberNavController()
    val entry by inner.currentBackStackEntryAsState()
    val selected = entry?.destination?.hierarchy?.firstNotNullOfOrNull { AppTab.fromRoute(it.route) }
    val currentSelected by rememberUpdatedState(selected)
    val onSelect by rememberUpdatedState<(AppTab) -> Unit> { tab ->
        // Reselecting the current tab does nothing: no new entry, no reset, no animation.
        if (tab != currentSelected) inner.select(tab)
    }
    LaunchedEffect(selected, admin) {
        if (shouldLeaveAdminTab(selected, admin)) inner.select(AppTab.NEXT_JAM)
    }
    Column(modifier = modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
        NavHost(
            navController = inner,
            startDestination = AppRoutes.NEXT_JAM,
            modifier = Modifier.weight(1f),
            enterTransition = { fadeIn(tween(AppMotion.TAB_FADE_MS)) },
            exitTransition = { fadeOut(tween(AppMotion.TAB_FADE_MS)) },
            popEnterTransition = { fadeIn(tween(AppMotion.TAB_FADE_MS)) },
            popExitTransition = { fadeOut(tween(AppMotion.TAB_FADE_MS)) },
        ) {
            composable(AppRoutes.NEXT_JAM) {
                NextJamScreen(onOpenSong = onOpenSong, onAddSong = onOpenAddSong)
            }
            composable(AppRoutes.PAST_JAMS) { PastJamsScreen(onOpenJam = onOpenPastJam) }
            composable(AppRoutes.CATALOG) { if (admin == true) CatalogScreen() }
            composable(AppRoutes.INFO) { InfoScreen(onOpenAdminLogin = onOpenAdminLogin) }
            composable(AppRoutes.ASSISTANT) { if (admin == true) AssistantScreen() }
        }
        if (!WindowInsets.isImeVisible) TabBar(tabBarModel(selected, admin) { tab -> onSelect(tab) })
    }
}

/** One entry per tab: the tab left is saved, the one entered restored, Próxima jam stays below. */
private fun NavHostController.select(tab: AppTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
