package com.bbbjam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.theme.BluesJamTheme

/** Temporary launch screen until the real screens exist. Drawn from the `:core:ui` tokens. */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.placeholder_title),
            style = BluesJamTheme.typography.h1,
        )
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    BluesJamTheme { PlaceholderScreen() }
}
