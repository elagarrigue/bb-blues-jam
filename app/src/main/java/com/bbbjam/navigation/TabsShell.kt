package com.bbbjam.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
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
import com.bbbjam.core.ui.nav.TabBar
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.info.InfoScreen
import com.bbbjam.feature.nextjam.NextJamScreen
import com.bbbjam.feature.pastjams.PastJamsScreen
import java.time.LocalDate

/**
 * The tabs shell (`bottom-navigation`), the outer host's [AppRoutes.TABS] destination: an inner
 * host with one route per tab above the bottom bar. Its controller, with every tab's saved state
 * (scroll, expanded rows, filter), is saved inside the outer `tabs` entry, so it survives the song
 * detail, rotation and process death. Switching tabs saves the tab left and restores the one
 * entered (`saveState`/`restoreState`); back from Anteriores or Info returns to Próxima jam (the
 * inner host's back handler wins while its stack has more than one entry), and from Próxima jam
 * the activity finishes. The status-bar inset is applied outside the scroll, over `background`, so
 * content never scrolls under the clock. [onOpenSong] opens a song's detail and [onOpenPastJam] a
 * past jam's detail and [onOpenAdminLogin] the admin login, all in the outer host.
 */
@Composable
internal fun TabsShell(
    onOpenSong: (jamDate: LocalDate, position: Int) -> Unit,
    onOpenPastJam: (jamDate: LocalDate) -> Unit,
    onOpenAdminLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inner = rememberNavController()
    val entry by inner.currentBackStackEntryAsState()
    val selected = entry?.destination?.hierarchy?.firstNotNullOfOrNull { AppTab.fromRoute(it.route) }
    val currentSelected by rememberUpdatedState(selected)
    val onSelect by rememberUpdatedState<(AppTab) -> Unit> { tab ->
        // Reselecting the current tab does nothing: no new entry, no reset, no animation.
        if (tab != currentSelected) inner.select(tab)
    }
    Column(modifier = modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
        NavHost(
            navController = inner,
            startDestination = AppRoutes.NEXT_JAM,
            modifier = Modifier.weight(1f).statusBarsPadding(),
            enterTransition = { fadeIn(tween(AppMotion.TAB_FADE_MS)) },
            exitTransition = { fadeOut(tween(AppMotion.TAB_FADE_MS)) },
            popEnterTransition = { fadeIn(tween(AppMotion.TAB_FADE_MS)) },
            popExitTransition = { fadeOut(tween(AppMotion.TAB_FADE_MS)) },
        ) {
            composable(AppRoutes.NEXT_JAM) { NextJamScreen(onOpenSong = onOpenSong) }
            composable(AppRoutes.PAST_JAMS) { PastJamsScreen(onOpenJam = onOpenPastJam) }
            composable(AppRoutes.INFO) { InfoScreen(onOpenAdminLogin = onOpenAdminLogin) }
        }
        TabBar(tabBarModel(selected) { tab -> onSelect(tab) })
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
