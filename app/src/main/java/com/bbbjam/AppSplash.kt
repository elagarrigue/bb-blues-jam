package com.bbbjam

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.bbbjam.core.ui.R as CoreUiR
import com.bbbjam.core.ui.brand.SplashDefaults
import com.bbbjam.core.ui.theme.BluesJamTheme

/** How long the splash stays up on a cold start before the tabs show. */
internal const val SPLASH_DURATION_MS = 3_000L

/**
 * The launch screen: the band logo, large, over the jam's name, on the window color. Drawn in
 * Compose because the system splash masks its icon to a small circle, which crops or shrinks the
 * wide logo; the system splash shows only the window color and hands over on the first frame.
 * Static on purpose: DESIGN.md (M1) allows no motion beyond the tab fade and the detail slide.
 */
@Composable
internal fun AppSplash(modifier: Modifier = Modifier) {
    val colors = BluesJamTheme.colors
    val typography = BluesJamTheme.typography
    val spacing = BluesJamTheme.spacing
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(CoreUiR.drawable.bbb_blues_band_logo),
                contentDescription = stringResource(CoreUiR.string.brand_logo_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(LOGO_WIDTH_FRACTION),
            )
            Spacer(Modifier.height(spacing.lg))
            Box(
                modifier = Modifier
                    .width(SplashDefaults.dividerWidth)
                    .height(SplashDefaults.dividerThickness)
                    .background(colors.border),
            )
            Spacer(Modifier.height(spacing.md))
            BasicText(
                text = stringResource(R.string.splash_title),
                style = typography.h1.copy(color = colors.text),
            )
        }
    }
}

private const val LOGO_WIDTH_FRACTION = 0.85f
