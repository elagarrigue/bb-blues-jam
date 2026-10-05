package com.bbbjam.navigation

/**
 * The navigation motion (`bottom-navigation`, user decision M1, 5 October 2026). No other motion.
 */
internal object AppMotion {
    /** Tab switch: a crossfade of the inner host. */
    const val TAB_FADE_MS = 150

    /** Song detail: slides in from the end over the tabs, and out to the end on back. */
    const val DETAIL_SLIDE_MS = 250
}
