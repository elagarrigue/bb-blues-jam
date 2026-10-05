package com.bbbjam.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbbjam.TemporaryTabs
import com.bbbjam.feature.songdetail.SongDetailScreen

/**
 * The app's navigation (`song-detail-screen`, N1): Navigation Compose, only in `:app`. Two
 * destinations: the temporary tabs and the song detail, full screen over them. No transition
 * (motion is `bottom-navigation`'s). System and predictive back are the host's own: they pop the
 * detail, and on the tabs the activity finishes. Leaving the tabs keeps their saveable state (tab,
 * expanded rows, filter, scroll) in their back stack entry, restored when the detail is popped.
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
        composable(AppRoutes.TABS) {
            TemporaryTabs(
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
                SongDetailScreen(
                    jamDate = args.jamDate,
                    position = args.position,
                    onBack = {
                        // A second tap while the pop runs must never pop the tabs and leave a blank host.
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                    },
                    contentPadding = WindowInsets.systemBars.asPaddingValues(),
                )
            }
        }
    }
}
