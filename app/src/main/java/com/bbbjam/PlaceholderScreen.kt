package com.bbbjam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview

/**
 * Temporary launch screen until the real screens exist. Colors come from `res/values/colors.xml`;
 * `design-tokens-theme` replaces them with Kotlin tokens in `:core:ui`.
 */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = stringResource(R.string.placeholder_title),
            style = TextStyle(color = colorResource(R.color.text)),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF111318)
@Composable
private fun PlaceholderScreenPreview() {
    PlaceholderScreen()
}
