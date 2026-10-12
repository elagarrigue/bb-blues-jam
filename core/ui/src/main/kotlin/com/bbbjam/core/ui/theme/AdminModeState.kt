package com.bbbjam.core.ui.theme

import androidx.compose.runtime.mutableStateOf

/**
 * The current admin-mode flag, written once by [BluesJamTheme] from the `:app` composition root
 * and read by [BluesJamColors.background]/`.surface`/`.surfaceRaised` (`admin-mode-highlight`).
 *
 * Backed by a Compose `mutableStateOf` rather than a plain `var`: a composable that reads
 * [isAdmin] (directly, or through a `…Defaults` function) still participates in Compose's snapshot
 * system and recomposes when it changes, the same guarantee a `CompositionLocal` would give. A
 * plain JVM test, or a non-composable `…Defaults` function with no Compose runtime, can still read
 * [isAdmin] as an ordinary `Boolean` with no snapshot/composition required; it defaults to `false`
 * so every existing test keeps seeing the musician palette with no change.
 *
 * Internal: nothing outside the theme package writes this directly. A screen or presenter still
 * reads admin state only through `AdminSession.observeIsAdmin()` (D-15); this holder exists purely
 * to make that one flag reach [BluesJamColors] without threading it through ~20 `…Defaults`
 * function signatures or adding an `if (isAdmin)` branch to any of them.
 */
internal object AdminModeState {
    private val state = mutableStateOf(false)

    var isAdmin: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }
}
