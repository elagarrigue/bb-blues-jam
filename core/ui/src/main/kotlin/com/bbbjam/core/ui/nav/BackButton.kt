package com.bbbjam.core.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The back arrow of a detail screen: an icon button in `text`, never amber, with a 48dp touch target
 * (Material's minimum interactive size, no literal) and the model's label as its description. It is
 * in `:core:ui` because features do not depend on the icons library. The arrow mirrors in RTL.
 */
@Composable
fun BackButton(model: BackUiModel, modifier: Modifier = Modifier) {
    val target = LocalMinimumInteractiveComponentSize.current
    Box(
        modifier = modifier
            .sizeIn(minWidth = target, minHeight = target)
            .clip(CircleShape)
            .clickable(role = Role.Button) { model.events(BackUiModel.Event.Back) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = model.label,
            tint = BluesJamTheme.colors.text,
        )
    }
}

@Preview
@Composable
private fun BackButtonPreview() {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.background) {
            BackButton(BackUiModel(NavCopy.BACK, EventHandler {}))
        }
    }
}
