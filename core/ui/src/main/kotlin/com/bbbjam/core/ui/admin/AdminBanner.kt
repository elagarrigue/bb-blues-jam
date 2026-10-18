package com.bbbjam.core.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The fixed "MODO ADMIN" strip (`admin-mode-highlight`): drawn once at the `:app` composition root,
 * directly under the status bar and above the tab bar/every full-screen route's content, never
 * inside a scrolling container. Stateless on purpose: its only input is that it is being drawn at
 * all — the caller (the composition root) decides that from `AdminSession.observeIsAdmin()`, so
 * this component itself never branches on `isAdmin`. Its fill and content colour are
 * [AdminBannerDefaults.fill]/`.content`, read with no admin check inside the component: by the time
 * [AdminBanner] is composed, [BluesJamTheme.colors] already resolves to the admin ramp (the caller
 * only draws this banner while admin mode is on).
 *
 * The text is a single plain node (Scenario 4): no `liveRegion`, so TalkBack reads it once when it
 * first enters the tree (or when a screen first composes it), not on every recomposition the way a
 * polite live region (`StalenessNotice`) would.
 */
@Composable
fun AdminBanner(modifier: Modifier = Modifier) {
    val defaults = AdminBannerDefaults
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .background(defaults.fill()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.admin_banner_label),
            style = BluesJamTheme.typography.body,
            color = defaults.content(),
        )
    }
}

@Preview
@Composable
private fun AdminBannerPreview() {
    BluesJamTheme(admin = true) {
        AdminBanner()
    }
}
