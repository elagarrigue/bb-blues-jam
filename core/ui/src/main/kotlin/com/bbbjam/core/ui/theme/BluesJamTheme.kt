package com.bbbjam.core.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The app's only theme: dark, no light variant, no dynamic color (D-17). Material 3 sits underneath
 * with every role mapped from a token, so Material components pick up the palette and fonts.
 *
 * Screens read [BluesJamTheme.colors] and the other token objects, never `MaterialTheme.colorScheme`:
 * Material defaults are not design decisions, and each component slice sets its colors from
 * [BluesJamColors].
 *
 * [admin] writes [AdminModeState] for the whole app (`admin-mode-highlight`): while true,
 * [BluesJamColors.background]/`.surface`/`.surfaceRaised` and the Material colour scheme's matching
 * roles switch to the admin ramp. Defaults to `false` so every existing call site (previews, tests)
 * stays on the musician palette with no change. The write happens directly in the composable body,
 * before [BluesJamMaterial.colorScheme] is read below, so the very first frame already reflects it.
 */
@Composable
fun BluesJamTheme(admin: Boolean = false, content: @Composable () -> Unit) {
    AdminModeState.isAdmin = admin
    MaterialTheme(
        colorScheme = BluesJamMaterial.colorScheme,
        typography = BluesJamMaterial.typography,
        shapes = BluesJamMaterial.shapes,
    ) {
        // MaterialTheme does not set LocalContentColor; without this, Text outside a Surface is
        // black. It does set the text selection colours, from the amber primary: replaced
        // app-wide by non-amber ones (`admin-add-song-to-setlist`, V1), so no text field
        // selects in amber.
        CompositionLocalProvider(
            LocalContentColor provides BluesJamColors.text,
            LocalTextSelectionColors provides BluesJamMaterial.textSelectionColors,
            content = content,
        )
    }
}

/** Entry point to the tokens, mirroring `MaterialTheme.colorScheme`. */
object BluesJamTheme {
    val colors: BluesJamColors get() = BluesJamColors
    val typography: BluesJamTypography get() = BluesJamTypography
    val shapes: BluesJamShapes get() = BluesJamShapes
    val spacing: BluesJamSpacing get() = BluesJamSpacing
}

/**
 * Material 3 mapped from the tokens. Every parameter is set, so no baseline purple leaks into a
 * Material component. `surfaceTint` is the background: elevation comes from explicit surface
 * tokens, not a tonal tint.
 *
 * [colorScheme] reads [AdminModeState] (plain, non-`@Composable`, same reasoning as
 * [BluesJamColors]) because every role built from [BluesJamPalette.Background], `.Surface` or
 * `.SurfaceRaised` must switch to [BluesJamAdminPalette] in admin mode (`admin-mode-highlight`),
 * otherwise an un-styled Material component (e.g. a bare `Surface`) would leak the musician colour.
 * [BluesJamPalette.SlotFilled] (`surfaceVariant`/`surfaceBright`/`surfaceContainerHighest`) is not
 * swapped: only background/surface/surfaceRaised have an admin variant (see non-goals).
 */
internal object BluesJamMaterial {
    val colorScheme: ColorScheme
        get() {
            val admin = AdminModeState.isAdmin
            val background = if (admin) BluesJamAdminPalette.Background else BluesJamPalette.Background
            val surface = if (admin) BluesJamAdminPalette.Surface else BluesJamPalette.Surface
            val surfaceRaised = if (admin) BluesJamAdminPalette.SurfaceRaised else BluesJamPalette.SurfaceRaised
            return darkColorScheme(
                primary = BluesJamPalette.Amber,
                onPrimary = BluesJamPalette.OnAmber,
                primaryContainer = BluesJamPalette.Amber,
                onPrimaryContainer = BluesJamPalette.OnAmber,
                inversePrimary = BluesJamPalette.Amber,
                secondary = BluesJamPalette.TextMuted,
                onSecondary = background,
                secondaryContainer = surfaceRaised,
                onSecondaryContainer = BluesJamPalette.Text,
                tertiary = BluesJamPalette.TextMuted,
                onTertiary = background,
                tertiaryContainer = surfaceRaised,
                onTertiaryContainer = BluesJamPalette.Text,
                background = background,
                onBackground = BluesJamPalette.Text,
                surface = background,
                onSurface = BluesJamPalette.Text,
                surfaceVariant = BluesJamPalette.SlotFilled,
                onSurfaceVariant = BluesJamPalette.TextMuted,
                surfaceTint = background,
                inverseSurface = BluesJamPalette.Text,
                inverseOnSurface = background,
                error = BluesJamPalette.Error,
                onError = background,
                errorContainer = surfaceRaised,
                onErrorContainer = BluesJamPalette.Error,
                outline = BluesJamPalette.Archive,
                outlineVariant = BluesJamPalette.Border,
                scrim = background,
                surfaceBright = BluesJamPalette.SlotFilled,
                surfaceContainer = surfaceRaised,
                surfaceContainerHigh = surfaceRaised,
                surfaceContainerHighest = BluesJamPalette.SlotFilled,
                surfaceContainerLow = surface,
                surfaceContainerLowest = background,
                surfaceDim = background,
            )
        }

    val typography: Typography = Typography(
        displayLarge = BluesJamTypography.key,
        displayMedium = BluesJamTypography.key,
        displaySmall = BluesJamTypography.key,
        headlineLarge = BluesJamTypography.h1,
        headlineMedium = BluesJamTypography.h1,
        headlineSmall = BluesJamTypography.h1,
        titleLarge = BluesJamTypography.songTitle,
        titleMedium = BluesJamTypography.songTitle,
        titleSmall = BluesJamTypography.songTitle,
        bodyLarge = BluesJamTypography.body,
        bodyMedium = BluesJamTypography.body,
        bodySmall = BluesJamTypography.caption,
        labelLarge = BluesJamTypography.body,
        labelMedium = BluesJamTypography.caption,
        labelSmall = BluesJamTypography.caption,
    )

    /** Selection handle in `text`, selected background `text` at 40%: never amber. */
    val textSelectionColors: TextSelectionColors = TextSelectionColors(
        handleColor = BluesJamPalette.Text,
        backgroundColor = BluesJamPalette.Text.copy(alpha = SELECTION_ALPHA),
    )

    const val SELECTION_ALPHA = 0.4f

    val shapes: Shapes = Shapes(
        extraSmall = BluesJamShapes.sm,
        small = BluesJamShapes.sm,
        medium = BluesJamShapes.md,
        large = BluesJamShapes.lg,
        extraLarge = BluesJamShapes.lg,
    )
}
