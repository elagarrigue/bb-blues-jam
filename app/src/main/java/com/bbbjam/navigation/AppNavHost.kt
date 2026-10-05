package com.bbbjam.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.songdetail.SongDetailScreen

/**
 * The app's navigation (`song-detail-screen`, N1; reshaped by `bottom-navigation`): Navigation
 * Compose, only in `:app`. The outer host has two destinations: the tabs shell ([TabsShell], with
 * its own inner host and the bottom bar) and the song detail, full screen over it with no bar. The
 * detail slides in from the end over the tabs, which stay drawn under it, and slides out to the end
 * on back (M1). System back pops the detail; on the tabs it is the inner host's (see [TabsShell]).
 */
@Composable
internal fun AppNavHost(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = AppRoutes.TABS,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(
            AppRoutes.TABS,
            // The shell stays drawn under the detail while it slides in and out.
            exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
            popEnterTransition = { EnterTransition.None },
        ) {
            TabsShell(
                onOpenSong = { date, position ->
                    nav.navigate(AppRoutes.songDetail(date, position)) { launchSingleTop = true }
                },
            )
        }
        composable(
            AppRoutes.SONG_DETAIL,
            arguments = listOf(
                navArgument(AppRoutes.JAM_DATE) { type = NavType.StringType },
                navArgument(AppRoutes.POSITION) { type = NavType.IntType },
            ),
            enterTransition = { slideIntoContainer(SlideDirection.Start, tween(AppMotion.DETAIL_SLIDE_MS)) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(AppMotion.DETAIL_SLIDE_MS)) },
        ) { entry ->
            val arguments = entry.arguments
            val args = AppRoutes.parseSongDetail(
                jamDate = arguments?.getString(AppRoutes.JAM_DATE),
                position = arguments?.takeIf { it.containsKey(AppRoutes.POSITION) }?.getInt(AppRoutes.POSITION),
            )
            if (args == null) {
                // Unreachable from the UI, which only builds routes through AppRoutes.songDetail.
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                // The status-bar inset sits outside the scroll, over a solid band; the bottom one inside it.
                Box(modifier = Modifier.fillMaxSize().background(BluesJamTheme.colors.background).statusBarsPadding()) {
                    SongDetailScreen(
                        jamDate = args.jamDate,
                        position = args.position,
                        onBack = {
                            // A second tap while the pop runs must never pop the tabs and leave a blank host.
                            if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                        },
                        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    )
                }
            }
        }
    }
}
