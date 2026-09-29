package com.bbbjam.core.ui.theme

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
 */
@Composable
fun BluesJamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BluesJamMaterial.colorScheme,
        typography = BluesJamMaterial.typography,
        shapes = BluesJamMaterial.shapes,
    ) {
        // MaterialTheme does not set LocalContentColor; without this, Text outside a Surface is black.
        CompositionLocalProvider(LocalContentColor provides BluesJamColors.text, content = content)
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
 */
internal object BluesJamMaterial {
    val colorScheme: ColorScheme = darkColorScheme(
        primary = BluesJamPalette.Amber,
        onPrimary = BluesJamPalette.OnAmber,
        primaryContainer = BluesJamPalette.Amber,
        onPrimaryContainer = BluesJamPalette.OnAmber,
        inversePrimary = BluesJamPalette.Amber,
        secondary = BluesJamPalette.TextMuted,
        onSecondary = BluesJamPalette.Background,
        secondaryContainer = BluesJamPalette.SurfaceRaised,
        onSecondaryContainer = BluesJamPalette.Text,
        tertiary = BluesJamPalette.TextMuted,
        onTertiary = BluesJamPalette.Background,
        tertiaryContainer = BluesJamPalette.SurfaceRaised,
        onTertiaryContainer = BluesJamPalette.Text,
        background = BluesJamPalette.Background,
        onBackground = BluesJamPalette.Text,
        surface = BluesJamPalette.Background,
        onSurface = BluesJamPalette.Text,
        surfaceVariant = BluesJamPalette.SlotFilled,
        onSurfaceVariant = BluesJamPalette.TextMuted,
        surfaceTint = BluesJamPalette.Background,
        inverseSurface = BluesJamPalette.Text,
        inverseOnSurface = BluesJamPalette.Background,
        error = BluesJamPalette.Error,
        onError = BluesJamPalette.Background,
        errorContainer = BluesJamPalette.SurfaceRaised,
        onErrorContainer = BluesJamPalette.Error,
        outline = BluesJamPalette.Archive,
        outlineVariant = BluesJamPalette.Border,
        scrim = BluesJamPalette.Background,
        surfaceBright = BluesJamPalette.SlotFilled,
        surfaceContainer = BluesJamPalette.SurfaceRaised,
        surfaceContainerHigh = BluesJamPalette.SurfaceRaised,
        surfaceContainerHighest = BluesJamPalette.SlotFilled,
        surfaceContainerLow = BluesJamPalette.Surface,
        surfaceContainerLowest = BluesJamPalette.Background,
        surfaceDim = BluesJamPalette.Background,
    )

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

    val shapes: Shapes = Shapes(
        extraSmall = BluesJamShapes.sm,
        small = BluesJamShapes.sm,
        medium = BluesJamShapes.md,
        large = BluesJamShapes.lg,
        extraLarge = BluesJamShapes.lg,
    )
}
