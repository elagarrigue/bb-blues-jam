package com.bbbjam.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbbjam.core.ui.admin.AdminBanner
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.info.AdminLoginScreen
import com.bbbjam.feature.nextjam.addsong.AddSongScreen
import com.bbbjam.feature.nextjam.assign.AssignMusicianScreen
import com.bbbjam.feature.nextjam.setkey.SetKeyScreen
import com.bbbjam.feature.nextjam.songdetail.SongDetailScreen
import com.bbbjam.feature.pastjams.detail.PastJamDetailScreen

/**
 * The app's navigation (`song-detail-screen`, N1; reshaped by `bottom-navigation`): Navigation
 * Compose, only in `:app`. The outer host has six destinations: the tabs shell ([TabsShell], with
 * its own inner host and the bottom bar), the song detail, the past jam detail (`past-jam-detail`),
 * the admin login (`admin-passphrase-login`) and the admin's pickers (`admin-add-song-to-setlist`,
 * `admin-set-key`), each full screen over it with no bar. A detail
 * slides in from the end over the tabs, which stay drawn under it, and slides out to the end on
 * back (M1). System back pops the detail; on the tabs it is the inner host's (see [TabsShell]).
 *
 * The status-bar inset and, while [admin] is true, the "MODO ADMIN" banner (`admin-mode-highlight`)
 * are applied once here, in a [Column] wrapping the whole [NavHost], rather than per destination:
 * every destination (the tabs shell and every full-screen route) used to apply its own
 * `statusBarsPadding()` independently, which would double the inset if this wrapper's inset stayed
 * too. [TabsShell] no longer applies its own. [assistantAvailable] goes to [TabsShell], which draws Asistente only
 * when it is true.
 */
@Composable
internal fun AppNavHost(modifier: Modifier = Modifier, admin: Boolean? = false, assistantAvailable: Boolean? = null) {
    val nav = rememberNavController()
    Column(modifier = modifier.fillMaxSize().background(BluesJamTheme.colors.background).statusBarsPadding()) {
        if (admin == true) AdminBanner()
        NavHost(
            navController = nav,
            startDestination = AppRoutes.TABS,
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
                    onOpenSong = { date, songId, position ->
                        nav.navigate(AppRoutes.songDetail(date, songId, position)) { launchSingleTop = true }
                    },
                    onOpenPastJam = { date ->
                        nav.navigate(AppRoutes.pastJamDetail(date)) { launchSingleTop = true }
                    },
                    onOpenAdminLogin = { nav.navigate(AppRoutes.ADMIN_LOGIN) { launchSingleTop = true } },
                    onOpenAddSong = { date -> nav.navigate(AppRoutes.addSong(date)) { launchSingleTop = true } },
                    admin = admin,
                    assistantAvailable = assistantAvailable,
                )
            }
            songDetail(nav)
            pastJamDetail(nav)
            adminLogin(nav)
            addSong(nav)
            setKey(nav)
            assignSlot(nav)
        }
    }
}

/** The fixed-slot musician assignment flow, beside the existing full-screen admin pickers. */
private fun NavGraphBuilder.assignSlot(nav: NavHostController) {
    composable(
        AppRoutes.ASSIGN_SLOT,
        arguments = listOf(
            navArgument(AppRoutes.JAM_DATE) { type = NavType.StringType },
            navArgument(AppRoutes.SONG_ID) { type = NavType.StringType },
            navArgument(AppRoutes.INSTRUMENT) { type = NavType.StringType },
            navArgument(AppRoutes.ORDINAL) { type = NavType.IntType },
        ),
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(AppMotion.DETAIL_SLIDE_MS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(AppMotion.DETAIL_SLIDE_MS)) },
    ) { entry ->
        val args = AppRoutes.parseAssignSlot(
            entry.arguments?.getString(AppRoutes.JAM_DATE),
            entry.arguments?.getString(AppRoutes.SONG_ID),
            entry.arguments?.getString(AppRoutes.INSTRUMENT),
            entry.arguments?.getInt(AppRoutes.ORDINAL),
        )
        if (args == null) {
            LaunchedEffect(Unit) { nav.popBackStack() }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
                AssignMusicianScreen(
                    jamDate = args.jamDate,
                    songId = args.songId,
                    instrument = args.instrument,
                    ordinal = args.position,
                    onBack = {
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                    },
                    onDone = { nav.popBackStack(AppRoutes.ASSIGN_SLOT, inclusive = true) },
                    contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                )
            }
        }
    }
}

/**
 * The admin's key picker (`admin-set-key`), beside the catalog picker and drawn the same way: full
 * screen over the tabs, the same slide and insets, back only while resumed. A pick pops by route,
 * a no-op once the picker is gone, so a late or repeated call is harmless.
 */
private fun NavGraphBuilder.setKey(nav: NavHostController) {
    composable(
        AppRoutes.SET_KEY,
        arguments = listOf(
            navArgument(AppRoutes.JAM_DATE) { type = NavType.StringType },
            navArgument(AppRoutes.SONG_ID) { type = NavType.StringType },
        ),
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(AppMotion.DETAIL_SLIDE_MS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(AppMotion.DETAIL_SLIDE_MS)) },
    ) { entry ->
        val args = AppRoutes.parseSetKey(
            jamDate = entry.arguments?.getString(AppRoutes.JAM_DATE),
            songId = entry.arguments?.getString(AppRoutes.SONG_ID),
        )
        if (args == null) {
            // Unreachable from the UI, which only builds routes through AppRoutes.setKey.
            LaunchedEffect(Unit) { nav.popBackStack() }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
                SetKeyScreen(
                    jamDate = args.jamDate,
                    songId = args.songId,
                    onBack = {
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                    },
                    onDone = { nav.popBackStack(AppRoutes.SET_KEY, inclusive = true) },
                    contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                )
            }
        }
    }
}

/**
 * The admin's catalog picker (`admin-add-song-to-setlist`), beside the admin login and drawn the
 * same way: full screen over the tabs, the same slide and insets, back only while resumed. A pick
 * pops by route, a no-op once the picker is gone, so a late or repeated call is harmless.
 */
private fun NavGraphBuilder.addSong(nav: NavHostController) {
    composable(
        AppRoutes.ADD_SONG,
        arguments = listOf(navArgument(AppRoutes.JAM_DATE) { type = NavType.StringType }),
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(AppMotion.DETAIL_SLIDE_MS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(AppMotion.DETAIL_SLIDE_MS)) },
    ) { entry ->
        val jamDate = AppRoutes.parseAddSong(entry.arguments?.getString(AppRoutes.JAM_DATE))
        if (jamDate == null) {
            // Unreachable from the UI, which only builds routes through AppRoutes.addSong.
            LaunchedEffect(Unit) { nav.popBackStack() }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
                AddSongScreen(
                    jamDate = jamDate,
                    onBack = {
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                    },
                    onAdded = { nav.popBackStack(AppRoutes.ADD_SONG, inclusive = true) },
                    contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                )
            }
        }
    }
}

/**
 * The song detail (`song-detail-screen`): full screen over the tabs, with the M1 slide. Since
 * `song-detail-replaces-row-expansion` it is named by song id and opens the admin's key picker and
 * assign screen above itself; both pop by route, back onto the detail.
 */
private fun NavGraphBuilder.songDetail(nav: NavHostController) {
    composable(
        AppRoutes.SONG_DETAIL,
        arguments = listOf(
            navArgument(AppRoutes.JAM_DATE) { type = NavType.StringType },
            navArgument(AppRoutes.SONG_ID) { type = NavType.StringType },
            navArgument(AppRoutes.POSITION) { type = NavType.IntType },
        ),
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(AppMotion.DETAIL_SLIDE_MS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(AppMotion.DETAIL_SLIDE_MS)) },
    ) { entry ->
        val arguments = entry.arguments
        val args = AppRoutes.parseSongDetail(
            jamDate = arguments?.getString(AppRoutes.JAM_DATE),
            songId = arguments?.getString(AppRoutes.SONG_ID),
            position = arguments?.takeIf { it.containsKey(AppRoutes.POSITION) }?.getInt(AppRoutes.POSITION),
        )
        if (args == null) {
            // Unreachable from the UI, which only builds routes through AppRoutes.songDetail.
            LaunchedEffect(Unit) { nav.popBackStack() }
        } else {
            // The status-bar inset (and the admin banner) are applied once by AppNavHost's shared
            // Column, outside the scroll, over a solid band; the bottom inset is inside it.
            Box(modifier = Modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
                SongDetailScreen(
                    jamDate = args.jamDate,
                    songId = args.songId,
                    position = args.position,
                    onBack = {
                        // A second tap while the pop runs must never pop the tabs and leave a blank host.
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                    },
                    contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    // The admin's pickers open above the detail and pop back to it by route.
                    onSetKey = { date, songId ->
                        nav.navigate(AppRoutes.setKey(date, songId)) { launchSingleTop = true }
                    },
                    onAssignSlot = { date, songId, instrument, position ->
                        nav.navigate(AppRoutes.assignSlot(date, songId, instrument, position)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

/**
 * The past jam detail (`past-jam-detail`), beside the song detail and drawn the same way: full
 * screen over the tabs, the same slide, insets and double-tap guard.
 */
private fun NavGraphBuilder.pastJamDetail(nav: NavHostController) {
    composable(
        AppRoutes.PAST_JAM_DETAIL,
        arguments = listOf(navArgument(AppRoutes.JAM_DATE) { type = NavType.StringType }),
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(AppMotion.DETAIL_SLIDE_MS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(AppMotion.DETAIL_SLIDE_MS)) },
    ) { entry ->
        val jamDate = AppRoutes.parsePastJamDetail(entry.arguments?.getString(AppRoutes.JAM_DATE))
        if (jamDate == null) {
            // Unreachable from the UI, which only builds routes through AppRoutes.pastJamDetail.
            LaunchedEffect(Unit) { nav.popBackStack() }
        } else {
            // As the song detail: the shared status-bar inset (and admin banner) outside the
            // scroll, the bottom inset inside it.
            Box(modifier = Modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
                PastJamDetailScreen(
                    jamDate = jamDate,
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

/**
 * The admin login (`admin-passphrase-login`, A7), beside the details and drawn the same way: full
 * screen over the tabs, the same slide and insets. Back pops only while the entry is resumed (a
 * second tap during the pop must never pop the tabs). A successful login pops by route instead:
 * it lands after a network round trip, possibly while the app is in the background, and popping
 * the route is a no-op once it is gone, so a late or repeated call is harmless.
 */
private fun NavGraphBuilder.adminLogin(nav: NavHostController) {
    composable(
        AppRoutes.ADMIN_LOGIN,
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(AppMotion.DETAIL_SLIDE_MS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(AppMotion.DETAIL_SLIDE_MS)) },
    ) { entry ->
        Box(modifier = Modifier.fillMaxSize().background(BluesJamTheme.colors.background)) {
            AdminLoginScreen(
                onBack = {
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                },
                onLoggedIn = { nav.popBackStack(AppRoutes.ADMIN_LOGIN, inclusive = true) },
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            )
        }
    }
}
